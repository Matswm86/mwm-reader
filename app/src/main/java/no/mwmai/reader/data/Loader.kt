package no.mwmai.reader.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.mwmai.reader.format.Csv
import no.mwmai.reader.format.Epub
import no.mwmai.reader.format.Highlighter
import no.mwmai.reader.format.Html
import no.mwmai.reader.format.Kinds
import no.mwmai.reader.format.Markdown
import no.mwmai.reader.format.Office
import no.mwmai.reader.format.Rtf
import no.mwmai.reader.format.Texts
import no.mwmai.reader.model.Block
import no.mwmai.reader.model.DocKind
import no.mwmai.reader.model.DocRef
import no.mwmai.reader.model.LoadedDoc
import no.mwmai.reader.model.Outline
import no.mwmai.reader.model.plain

/** Turns a [DocRef] into something the reader can draw. */
object Loader {

    private const val TAG = "MwmReader"

    /** Past this, a text file is cut off and the reader says so. */
    const val TEXT_LIMIT = 12 * 1024 * 1024

    suspend fun load(context: Context, ref: DocRef): LoadedDoc = withContext(Dispatchers.IO) {
        try {
            loadBlocking(context, ref)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "out of memory loading ${ref.name}", e)
            LoadedDoc.Failed("Too big to open", "${ref.name} does not fit in memory on this phone.")
        } catch (e: Exception) {
            Log.w(TAG, "failed to load ${ref.name}", e)
            LoadedDoc.Failed("Could not open this file", e.message.orEmpty())
        }
    }

    private fun loadBlocking(context: Context, ref: DocRef): LoadedDoc {
        val uri = Uri.parse(ref.uri)
        val ext = Kinds.extensionOf(ref.name)
        return when (Kinds.kindOf(ref.name, ref.mime)) {
            DocKind.PDF -> pdf(context, uri, ref)
            DocKind.IMAGE -> {
                val file = Files.cacheCopy(context, uri, ref.name)
                    ?: return unreadable(ref)
                LoadedDoc.Picture(file)
            }
            DocKind.EPUB -> {
                val stream = Files.open(context, uri) ?: return unreadable(ref)
                val book = stream.use { Epub.load(it) }
                val head = ArrayList<Block>()
                if (book.title.isNotBlank()) head.add(Block.Heading(1, plain(book.title)))
                if (book.author.isNotBlank()) head.add(Block.Para(plain(book.author)))
                val outline = book.outline.map { it.copy(blockIndex = it.blockIndex + head.size) }
                LoadedDoc.Rendered(head + book.blocks, outline)
            }
            DocKind.OFFICE -> {
                val stream = Files.open(context, uri) ?: return unreadable(ref)
                val result = stream.use { Office.load(ext, it) }
                val blocks = ArrayList(result.blocks)
                if (result.note.isNotBlank()) {
                    blocks.add(Block.Rule)
                    blocks.add(Block.Quote(plain(result.note)))
                }
                LoadedDoc.Rendered(blocks, result.outline)
            }
            DocKind.RTF -> {
                val (bytes, cut) = Files.readAll(context, uri, TEXT_LIMIT) ?: return unreadable(ref)
                val text = Rtf.toText(Texts.decode(bytes))
                val blocks = Texts.paragraphs(Texts.lines(text)).map { Block.Para(plain(it)) }
                LoadedDoc.Rendered(blocks.ifEmpty { listOf(Block.Para(plain("(no text in this file)"))) }, emptyList(), null)
                    .also { if (cut) Log.i(TAG, "rtf truncated") }
            }
            DocKind.TABLE -> {
                val (bytes, cut) = Files.readAll(context, uri, TEXT_LIMIT) ?: return unreadable(ref)
                val text = Texts.decode(bytes)
                val rows = Csv.parse(text, Csv.delimiterFor(ref.name, text.take(4096)))
                if (rows.isEmpty()) LoadedDoc.Table(emptyList(), emptyList(), cut)
                else LoadedDoc.Table(rows.first(), rows.drop(1), cut)
            }
            DocKind.MARKDOWN -> {
                val (bytes, cut) = Files.readAll(context, uri, TEXT_LIMIT) ?: return unreadable(ref)
                if (Kinds.looksBinary(bytes)) return binary(ref)
                val lines = Texts.lines(Texts.decode(bytes))
                val (blocks, outline) = Markdown.parse(lines)
                LoadedDoc.Rendered(blocks + truncationNote(cut), outline, lines, "markdown")
            }
            DocKind.HTML -> {
                val (bytes, cut) = Files.readAll(context, uri, TEXT_LIMIT) ?: return unreadable(ref)
                val text = Texts.decode(bytes)
                val (blocks, outline) = Html.parse(text)
                LoadedDoc.Rendered(blocks + truncationNote(cut), outline, Texts.lines(text), "xml")
            }
            DocKind.CODE, DocKind.TEXT -> {
                val (bytes, cut) = Files.readAll(context, uri, TEXT_LIMIT) ?: return unreadable(ref)
                if (Kinds.looksBinary(bytes)) return binary(ref)
                LoadedDoc.Lines(Texts.lines(Texts.decode(bytes)), Kinds.langOf(ref.name), cut)
            }
            DocKind.UNSUPPORTED -> {
                // No extension and no useful mime type: look at the bytes.
                val (bytes, cut) = Files.readAll(context, uri, TEXT_LIMIT) ?: return unreadable(ref)
                if (bytes.isEmpty()) return LoadedDoc.Failed("Empty file", "${ref.name} has no content.")
                if (Kinds.looksBinary(bytes)) return binary(ref)
                LoadedDoc.Lines(Texts.lines(Texts.decode(bytes)), Kinds.langOf(ref.name), cut)
            }
        }
    }

    private fun truncationNote(cut: Boolean): List<Block> =
        if (!cut) emptyList()
        else listOf(
            Block.Rule,
            Block.Quote(plain("This file is larger than ${TEXT_LIMIT / (1024 * 1024)} MB and was cut off here.")),
        )

    private fun pdf(context: Context, uri: Uri, ref: DocRef): LoadedDoc {
        val file = Files.cacheCopy(context, uri, ref.name) ?: return unreadable(ref)
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    LoadedDoc.Pdf(file, renderer.pageCount)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PdfRenderer refused ${ref.name}", e)
            LoadedDoc.Failed(
                "This PDF will not open",
                "Android's PDF engine rejected it. Encrypted and password-protected files are the usual reason.",
            )
        }
    }

    private fun unreadable(ref: DocRef) = LoadedDoc.Failed(
        "No longer readable",
        "${ref.name} could not be opened. It may have been moved, deleted, or the app's permission to read it expired.",
    )

    private fun binary(ref: DocRef) = LoadedDoc.Failed(
        "Not a text file",
        "${ref.name} looks like binary data rather than text, so there is nothing to read.",
    )

    /** Cheap enough to run on the main thread for the outline of a code file. */
    fun outlineFor(lines: List<String>, langId: String): List<Outline> {
        if (!Highlighter.isKnown(langId)) return emptyList()
        val out = ArrayList<Outline>()
        val re = when (langId) {
            "pine" -> Regex("^\\s*(?:export\\s+)?(?:method\\s+)?(?:type|enum)?\\s*(\\w+)\\s*\\(.*=>|^\\s*(indicator|strategy|library)\\s*\\(")
            "python" -> Regex("^\\s*(?:async\\s+)?(?:def|class)\\s+(\\w+)")
            "kotlin", "java", "csharp", "swift" -> Regex("^\\s*(?:[\\w@]+\\s+)*?(?:fun|class|interface|object|enum|struct|protocol)\\s+(\\w+)")
            "javascript" -> Regex("^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?(?:function|class|const|let)\\s+(\\w+)")
            "go" -> Regex("^\\s*func\\s+(?:\\([^)]*\\)\\s*)?(\\w+)")
            "rust" -> Regex("^\\s*(?:pub\\s+)?(?:async\\s+)?(?:fn|struct|enum|trait|impl|mod)\\s+(\\w+)")
            "shell" -> Regex("^\\s*(?:function\\s+)?(\\w+)\\s*\\(\\)\\s*\\{")
            "sql" -> Regex("(?i)^\\s*create\\s+(?:or\\s+replace\\s+)?(?:table|view|index|function)\\s+(\\S+)")
            else -> return emptyList()
        }
        lines.forEachIndexed { index, line ->
            if (line.length > 400) return@forEachIndexed
            val m = re.find(line) ?: return@forEachIndexed
            val name = m.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return@forEachIndexed
            out.add(Outline(name, 1, index))
        }
        return out.take(500)
    }
}
