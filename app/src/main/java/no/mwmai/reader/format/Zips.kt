package no.mwmai.reader.format

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * EPUB, docx, xlsx, pptx and the OpenDocument formats are all zip archives of
 * XML. One sequential pass reads the entries the caller cares about, so nothing
 * is copied to disk and a 200 MB archive of images cannot be pulled into RAM.
 */
object Zips {

    class Archive(val entries: LinkedHashMap<String, ByteArray>) {
        /** Case-insensitive, and tolerant of a leading `./` or `/`. */
        fun find(path: String): ByteArray? {
            entries[path]?.let { return it }
            val want = path.trimStart('/', '.').lowercase()
            return entries.entries.firstOrNull {
                it.key.trimStart('/', '.').lowercase() == want
            }?.value
        }

        fun names(): List<String> = entries.keys.toList()
    }

    fun read(
        input: InputStream,
        budgetBytes: Long = 48L * 1024 * 1024,
        accept: (String) -> Boolean,
    ): Archive {
        val out = LinkedHashMap<String, ByteArray>()
        var used = 0L
        // A file that is not really a zip (a legacy .doc, a truncated download)
        // throws somewhere in here. An empty archive is the honest answer; the
        // caller turns that into a message on screen.
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    val name = entry.name
                    if (!accept(name)) { zip.closeEntry(); continue }
                    val buf = ByteArrayOutputStream(maxOf(1024, entry.size.toInt().coerceAtLeast(0)))
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val read = zip.read(chunk)
                        if (read <= 0) break
                        used += read
                        if (used > budgetBytes) { zip.closeEntry(); return Archive(out) }
                        buf.write(chunk, 0, read)
                    }
                    out[name] = buf.toByteArray()
                    zip.closeEntry()
                }
            }
        } catch (_: Exception) {
            return Archive(out)
        } catch (_: OutOfMemoryError) {
            return Archive(out)
        }
        return Archive(out)
    }

    fun text(bytes: ByteArray?): String = bytes?.toString(Charsets.UTF_8).orEmpty()
}

/** Minimal XML walking: these files are machine-written, so tag scanning is enough. */
object Xml {

    /** Every `<tag ...>body</tag>` body, in document order. Self-closing tags give "". */
    fun bodies(src: String, tag: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val open = "<$tag"
        val close = "</$tag>"
        while (true) {
            val s = src.indexOf(open, i)
            if (s < 0) break
            val gt = src.indexOf('>', s)
            if (gt < 0) break
            if (!isTagBoundary(src, s + open.length)) { i = s + open.length; continue }
            if (src[gt - 1] == '/') { out.add(""); i = gt + 1; continue }
            val e = src.indexOf(close, gt)
            if (e < 0) break
            out.add(src.substring(gt + 1, e))
            i = e + close.length
        }
        return out
    }

    /** Concatenated text of every `<tag>` inside a fragment. */
    fun textOf(src: String, tag: String): String =
        bodies(src, tag).joinToString("") { Html.decode(it) }

    fun attr(src: String, tag: String, name: String): String {
        val s = src.indexOf("<$tag")
        if (s < 0) return ""
        val gt = src.indexOf('>', s)
        if (gt < 0) return ""
        return attrIn(src.substring(s, gt), name)
    }

    fun attrIn(tagText: String, name: String): String {
        val key = "$name=\""
        val at = tagText.indexOf(key)
        if (at < 0) return ""
        val end = tagText.indexOf('"', at + key.length)
        if (end < 0) return ""
        return Html.decode(tagText.substring(at + key.length, end))
    }

    /** True when the character after a tag name really ends the name. */
    private fun isTagBoundary(src: String, at: Int): Boolean {
        val c = src.getOrNull(at) ?: return false
        return c.isWhitespace() || c == '>' || c == '/'
    }

    /** The opening tag text (`<w:p w:rsid="..">`) at or after [from]. */
    fun openTagAt(src: String, from: Int): String {
        val gt = src.indexOf('>', from)
        return if (gt < 0) "" else src.substring(from, gt + 1)
    }
}
