package no.mwmai.reader.model

import kotlinx.serialization.Serializable
import java.io.File

/**
 * What a file turns into on screen. Detection is by extension first and mime
 * type second, because a file manager will happily call a `.pine` file
 * `application/octet-stream`.
 */
enum class DocKind {
    /** Prose. Rendered in the reading font, wrapped, no line numbers. */
    TEXT,

    /** Source code. Monospace, syntax coloured, line numbers available. */
    CODE,

    /** Markdown, rendered as headings/lists/tables with a raw-source toggle. */
    MARKDOWN,

    /** HTML or XHTML, rendered as text blocks. Nothing is fetched or executed. */
    HTML,

    /** PDF, drawn page by page with Android's own PdfRenderer. */
    PDF,

    /** EPUB, unzipped in memory, one entry per spine item. */
    EPUB,

    /** Delimited data, shown as a table. */
    TABLE,

    /** A picture. */
    IMAGE,

    /** docx / xlsx / pptx / odt / odp / ods: the text is extracted, not laid out. */
    OFFICE,

    /** RTF, control words stripped. */
    RTF,

    /** Nothing sensible to show. */
    UNSUPPORTED,
}

/** A file the app knows how to reach again: a content:// or file:// string. */
@Serializable
data class DocRef(
    val uri: String,
    val name: String,
    val mime: String = "",
    val size: Long = -1L,
)

/** A [DocRef] plus where the reader had got to in it. */
@Serializable
data class Recent(
    val doc: DocRef,
    val openedAt: Long = 0L,
    val lineIndex: Int = 0,
    val lineOffset: Int = 0,
    val page: Int = 0,
    val chapter: Int = 0,
    val persisted: Boolean = false,
)

/** A folder the user granted long-term read access to. */
@Serializable
data class Shelf(
    val treeUri: String,
    val name: String,
)

// ---------------------------------------------------------------- inline text

/** One run of inline text with its emphasis. Deliberately free of any Android
 *  or Compose type so the parsers can be unit-tested on the JVM. */
@Serializable
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

typealias Inline = List<Span>

fun plain(text: String): Inline = listOf(Span(text))

/** Flattened text of an inline run, used by search and by the tests. */
fun Inline.text(): String = joinToString("") { it.text }

// -------------------------------------------------------------------- blocks

/** A rendered-document building block. Markdown, HTML, EPUB and the office
 *  extractors all produce these, so one renderer draws all of them. */
sealed interface Block {
    data class Heading(val level: Int, val inline: Inline) : Block
    data class Para(val inline: Inline) : Block
    data class Quote(val inline: Inline, val depth: Int = 1) : Block
    data class Bullet(val inline: Inline, val marker: String, val indent: Int) : Block
    data class CodeBlock(val lines: List<String>, val langId: String) : Block
    data class TableBlock(val header: List<String>, val rows: List<List<String>>) : Block
    data class Picture(val alt: String) : Block
    data object Rule : Block
}

/** A jump target in the outline drawer: a markdown heading or an EPUB chapter. */
data class Outline(
    val title: String,
    val level: Int,
    val blockIndex: Int,
)

// ------------------------------------------------------------- loaded result

sealed interface LoadedDoc {
    /** Plain text or source. One entry per physical line. */
    data class Lines(
        val lines: List<String>,
        val langId: String,
        val truncated: Boolean = false,
    ) : LoadedDoc

    /** A rendered document. [raw] is the source behind it, when there is one. */
    data class Rendered(
        val blocks: List<Block>,
        val outline: List<Outline> = emptyList(),
        val raw: List<String>? = null,
        val rawLangId: String = "text",
    ) : LoadedDoc

    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val truncated: Boolean = false,
    ) : LoadedDoc

    data class Pdf(val file: File, val pageCount: Int) : LoadedDoc

    data class Picture(val file: File) : LoadedDoc

    data class Failed(val message: String, val detail: String = "") : LoadedDoc
}

// ------------------------------------------------------------------ settings

enum class ThemeChoice { PAPER, SEPIA, DUSK, BLACK, SYSTEM }

enum class ReadingFont { SANS, SERIF, MONO }

@Serializable
data class Settings(
    val theme: String = ThemeChoice.SYSTEM.name,
    val font: String = ReadingFont.SERIF.name,
    val fontSize: Int = 17,
    val lineHeight: Int = 150, // percent of font size
    val margin: Int = 16, // dp
    val wrapCode: Boolean = true,
    val lineNumbers: Boolean = true,
    val keepScreenOn: Boolean = false,
) {
    val themeChoice: ThemeChoice
        get() = runCatching { ThemeChoice.valueOf(theme) }.getOrDefault(ThemeChoice.SYSTEM)

    val readingFont: ReadingFont
        get() = runCatching { ReadingFont.valueOf(font) }.getOrDefault(ReadingFont.SERIF)
}

@Serializable
data class AppState(
    val settings: Settings = Settings(),
    val recents: List<Recent> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
)
