package no.mwmai.reader.format

import no.mwmai.reader.model.Block
import no.mwmai.reader.model.Outline
import no.mwmai.reader.model.Span

/**
 * HTML and XHTML turned into the same blocks markdown produces, so one renderer
 * draws both and an EPUB chapter looks like a markdown note.
 *
 * Nothing here executes or fetches anything: `<script>` and `<style>` contents
 * are dropped on the floor, and the app holds no internet permission anyway.
 */
object Html {

    private val skipTags = setOf("script", "style", "head", "svg", "noscript", "template")

    fun parse(src: String): Pair<List<Block>, List<Outline>> {
        val blocks = ArrayList<Block>()
        val outline = ArrayList<Outline>()

        var cur = ArrayList<Span>()
        var style = Span("")
        val styleStack = ArrayList<Span>()

        var heading = 0
        var quote = 0
        var skip = 0
        var inPre = false
        val preBuf = StringBuilder()
        var preLang = "text"

        val listStack = ArrayList<IntArray>() // [ordered 0/1, counter]

        var inTable = false
        var rows = ArrayList<List<String>>()
        var cells = ArrayList<String>()
        var cellBuf: StringBuilder? = null

        fun flushInline() {
            val text = cur.joinToString("") { it.text }
            if (text.isBlank()) { cur = ArrayList(); return }
            val spans = cur.filter { it.text.isNotEmpty() }
            when {
                heading > 0 -> {
                    outline.add(Outline(text.trim(), heading, blocks.size))
                    blocks.add(Block.Heading(heading, spans))
                }
                quote > 0 -> blocks.add(Block.Quote(spans, quote))
                listStack.isNotEmpty() -> {
                    val top = listStack.last()
                    val marker = if (top[0] == 1) "${top[1]}." else "•"
                    blocks.add(Block.Bullet(spans, marker, listStack.size - 1))
                }
                else -> blocks.add(Block.Para(spans))
            }
            cur = ArrayList()
        }

        fun append(text: String) {
            if (text.isEmpty()) return
            if (cellBuf != null) { cellBuf!!.append(text); return }
            cur.add(style.copy(text = text))
        }

        var i = 0
        val n = src.length
        while (i < n) {
            val lt = src.indexOf('<', i)
            if (lt < 0) {
                if (skip == 0) {
                    if (inPre) preBuf.append(decode(src.substring(i))) else append(squash(decode(src.substring(i))))
                }
                break
            }
            if (lt > i && skip == 0) {
                val raw = src.substring(i, lt)
                if (inPre) preBuf.append(decode(raw)) else append(squash(decode(raw)))
            }
            // Comments and doctype.
            if (src.startsWith("<!--", lt)) {
                val end = src.indexOf("-->", lt)
                i = if (end < 0) n else end + 3
                continue
            }
            if (src.startsWith("<!", lt) || src.startsWith("<?", lt)) {
                val end = src.indexOf('>', lt)
                i = if (end < 0) n else end + 1
                continue
            }
            val gt = src.indexOf('>', lt)
            if (gt < 0) break
            val rawTag = src.substring(lt + 1, gt).trim()
            i = gt + 1

            val closing = rawTag.startsWith("/")
            val body = rawTag.removePrefix("/").removeSuffix("/")
            val name = body.takeWhile { !it.isWhitespace() }.lowercase()
            val attrs = body.drop(name.length)

            if (name in skipTags) {
                if (closing) { if (skip > 0) skip-- } else skip++
                continue
            }
            if (skip > 0) continue

            if (closing) {
                when (name) {
                    "b", "strong", "i", "em", "u", "code", "kbd", "samp", "tt",
                    "s", "strike", "del", "a", "span", "small", "sub", "sup",
                    -> if (styleStack.isNotEmpty()) style = styleStack.removeAt(styleStack.size - 1)

                    "p", "div", "section", "article", "header", "footer", "figcaption",
                    "dd", "dt", "caption",
                    -> flushInline()

                    "h1", "h2", "h3", "h4", "h5", "h6" -> { flushInline(); heading = 0 }

                    "li" -> flushInline()

                    "ul", "ol" -> {
                        flushInline()
                        if (listStack.isNotEmpty()) listStack.removeAt(listStack.size - 1)
                    }

                    "blockquote" -> { flushInline(); if (quote > 0) quote-- }

                    "pre" -> {
                        inPre = false
                        val text = preBuf.toString().trim('\n')
                        if (text.isNotBlank()) blocks.add(Block.CodeBlock(text.split("\n"), preLang))
                        preBuf.setLength(0)
                        preLang = "text"
                    }

                    "td", "th" -> {
                        cells.add(cellBuf?.toString()?.trim().orEmpty())
                        cellBuf = null
                    }

                    "tr" -> { rows.add(cells.toList()); cells = ArrayList() }

                    "table" -> {
                        if (rows.isNotEmpty()) {
                            blocks.add(Block.TableBlock(rows.first(), rows.drop(1)))
                        }
                        rows = ArrayList(); cells = ArrayList(); inTable = false
                    }
                }
                continue
            }

            when (name) {
                "br" -> if (inPre) preBuf.append('\n') else append("\n")
                "hr" -> { flushInline(); blocks.add(Block.Rule) }
                "img" -> {
                    val alt = attr(attrs, "alt").ifBlank { attr(attrs, "src").substringAfterLast('/') }
                    flushInline()
                    blocks.add(Block.Picture(alt.ifBlank { "image" }))
                }
                "p", "div", "section", "article", "header", "footer", "figcaption",
                "dd", "dt", "caption",
                -> flushInline()

                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flushInline(); heading = name.drop(1).toInt()
                }

                "blockquote" -> { flushInline(); quote++ }

                "ul" -> { flushInline(); listStack.add(intArrayOf(0, 0)) }
                "ol" -> { flushInline(); listStack.add(intArrayOf(1, 0)) }
                "li" -> {
                    flushInline()
                    if (listStack.isEmpty()) listStack.add(intArrayOf(0, 0))
                    listStack.last()[1]++
                }

                "pre" -> {
                    flushInline(); inPre = true
                    preLang = languageFromClass(attr(attrs, "class"))
                }

                "table" -> { flushInline(); inTable = true; rows = ArrayList(); cells = ArrayList() }
                "tr" -> if (inTable) cells = ArrayList()
                "td", "th" -> if (inTable) cellBuf = StringBuilder()

                "b", "strong" -> { styleStack.add(style); style = style.copy(bold = true) }
                "i", "em", "cite", "var" -> { styleStack.add(style); style = style.copy(italic = true) }
                "code", "kbd", "samp", "tt" -> {
                    styleStack.add(style)
                    if (!inPre) style = style.copy(code = true)
                }
                "s", "strike", "del" -> { styleStack.add(style); style = style.copy(strike = true) }
                "a" -> {
                    styleStack.add(style)
                    val href = attr(attrs, "href")
                    if (href.isNotBlank()) style = style.copy(link = href)
                }
                "u", "span", "small", "sub", "sup" -> styleStack.add(style)
            }
        }
        flushInline()
        return blocks to outline
    }

    private fun languageFromClass(cls: String): String {
        val token = cls.split(' ').firstOrNull { it.startsWith("language-") || it.startsWith("lang-") }
            ?: return "text"
        val id = token.substringAfter('-').lowercase()
        return Kinds.codeLang[id] ?: if (Highlighter.isKnown(id)) id else "text"
    }

    private fun attr(attrs: String, name: String): String {
        val key = "$name="
        var idx = attrs.indexOf(key, ignoreCase = true)
        while (idx > 0) {
            val before = attrs[idx - 1]
            if (before.isWhitespace()) break
            idx = attrs.indexOf(key, idx + 1, ignoreCase = true)
        }
        if (idx < 0) return ""
        var v = idx + key.length
        if (v >= attrs.length) return ""
        val quote = attrs[v]
        return if (quote == '"' || quote == '\'') {
            val end = attrs.indexOf(quote, v + 1)
            if (end < 0) "" else attrs.substring(v + 1, end)
        } else {
            val end = attrs.indexOfFirst(v) { it.isWhitespace() }
            attrs.substring(v, if (end < 0) attrs.length else end)
        }
    }

    private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
        for (k in from until length) if (predicate(this[k])) return k
        return -1
    }

    /** Collapse the whitespace HTML does not honour, the way a browser would. */
    private fun squash(s: String): String {
        val sb = StringBuilder(s.length)
        var space = false
        for (c in s) {
            if (c == ' ' || c == '\n' || c == '\t' || c == '\r') {
                if (!space) { sb.append(' '); space = true }
            } else { sb.append(c); space = false }
        }
        return sb.toString()
    }

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "mdash" to "—", "ndash" to "–",
        "hellip" to "…", "ldquo" to "“", "rdquo" to "”",
        "lsquo" to "‘", "rsquo" to "’", "copy" to "©",
        "reg" to "®", "trade" to "™", "deg" to "°",
        "bull" to "•", "middot" to "·", "times" to "×",
        "euro" to "€", "pound" to "£", "laquo" to "«",
        "raquo" to "»", "shy" to "", "ensp" to " ", "emsp" to " ",
    )

    fun decode(s: String): String {
        if (!s.contains('&')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') { sb.append(c); i++; continue }
            val semi = s.indexOf(';', i)
            if (semi < 0 || semi - i > 10) { sb.append(c); i++; continue }
            val ent = s.substring(i + 1, semi)
            val rep = when {
                ent.startsWith("#x") || ent.startsWith("#X") ->
                    ent.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                ent.startsWith("#") -> ent.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> named[ent]
            }
            if (rep == null) { sb.append(c); i++ } else { sb.append(rep); i = semi + 1 }
        }
        return sb.toString()
    }

    /** Everything between the tags, used for titles and text-only extraction. */
    fun stripTags(s: String): String =
        decode(s.replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim()
}
