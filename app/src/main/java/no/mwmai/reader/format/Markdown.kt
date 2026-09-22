package no.mwmai.reader.format

import no.mwmai.reader.model.Block
import no.mwmai.reader.model.Inline
import no.mwmai.reader.model.Outline
import no.mwmai.reader.model.Span

/**
 * A CommonMark subset: headings (both styles), fenced code, lists, block
 * quotes, tables, rules, YAML front matter, and the inline run of emphasis,
 * code, strike and links. Pure Kotlin with no Android import, so the tests
 * drive it directly.
 */
object Markdown {

    fun parse(lines: List<String>): Pair<List<Block>, List<Outline>> {
        val blocks = ArrayList<Block>()
        val outline = ArrayList<Outline>()
        var i = 0
        val n = lines.size

        // YAML front matter, which a note-taking app puts at the top.
        if (n > 1 && lines[0].trim() == "---") {
            var end = -1
            for (j in 1 until n) if (lines[j].trim() == "---") { end = j; break }
            if (end > 0) {
                blocks.add(Block.CodeBlock(lines.subList(1, end).toList(), "yaml"))
                i = end + 1
            }
        }

        val para = ArrayList<String>()

        fun flushPara() {
            if (para.isEmpty()) return
            blocks.add(Block.Para(inline(para.joinToString("\n"))))
            para.clear()
        }

        fun addHeading(level: Int, raw: String) {
            flushPara()
            val text = inline(raw)
            outline.add(Outline(text.joinToString("") { it.text }, level, blocks.size))
            blocks.add(Block.Heading(level, text))
        }

        while (i < n) {
            val line = lines[i]
            val trimmed = line.trim()

            // Fenced code.
            val fence = fenceOf(trimmed)
            if (fence != null) {
                flushPara()
                val info = trimmed.drop(fence.length).trim().substringBefore(' ')
                val body = ArrayList<String>()
                var j = i + 1
                while (j < n && fenceOf(lines[j].trim())?.startsWith(fence.take(1)) != true) {
                    body.add(lines[j]); j++
                }
                blocks.add(Block.CodeBlock(body, langIdFor(info)))
                i = if (j < n) j + 1 else j
                continue
            }

            if (trimmed.isEmpty()) { flushPara(); i++; continue }

            // ATX heading.
            if (trimmed.startsWith("#")) {
                val hashes = trimmed.takeWhile { it == '#' }.length
                if (hashes in 1..6 && (trimmed.length == hashes || trimmed[hashes] == ' ')) {
                    addHeading(hashes, trimmed.drop(hashes).trim().trimEnd('#').trim())
                    i++; continue
                }
            }

            // Setext heading: text with ==== or ---- underneath it.
            if (i + 1 < n && para.isEmpty()) {
                val next = lines[i + 1].trim()
                if (next.length >= 2 && next.all { it == '=' }) { addHeading(1, trimmed); i += 2; continue }
                if (next.length >= 2 && next.all { it == '-' } && !isRule(next)) {
                    addHeading(2, trimmed); i += 2; continue
                }
            }

            if (isRule(trimmed)) { flushPara(); blocks.add(Block.Rule); i++; continue }

            // Table: a header row, then a delimiter row of dashes and pipes.
            if (trimmed.contains('|') && i + 1 < n && isTableDelimiter(lines[i + 1])) {
                flushPara()
                val header = splitRow(trimmed)
                val rows = ArrayList<List<String>>()
                var j = i + 2
                while (j < n && lines[j].contains('|') && lines[j].isNotBlank()) {
                    rows.add(splitRow(lines[j].trim())); j++
                }
                blocks.add(Block.TableBlock(header, rows))
                i = j; continue
            }

            // Block quote.
            if (trimmed.startsWith(">")) {
                flushPara()
                var depth = 0
                var rest = trimmed
                while (rest.startsWith(">")) { depth++; rest = rest.drop(1).trimStart() }
                val body = ArrayList<String>()
                body.add(rest)
                var j = i + 1
                while (j < n && lines[j].trim().startsWith(">")) {
                    body.add(lines[j].trim().trimStart('>').trimStart()); j++
                }
                blocks.add(Block.Quote(inline(body.joinToString("\n")), depth))
                i = j; continue
            }

            // List item.
            val item = listItemOf(line)
            if (item != null) {
                flushPara()
                val (indent, marker, rest) = item
                // Continuation lines are indented under the marker.
                val body = StringBuilder(rest)
                var j = i + 1
                while (j < n && lines[j].isNotBlank() && listItemOf(lines[j]) == null &&
                    lines[j].takeWhile { it == ' ' }.length > indent
                ) {
                    body.append('\n').append(lines[j].trim()); j++
                }
                // A standalone image line is a picture, not a bullet.
                blocks.add(Block.Bullet(inline(body.toString()), marker, indent / 2))
                i = j; continue
            }

            // A line that is only an image.
            val img = imageOnly(trimmed)
            if (img != null) { flushPara(); blocks.add(Block.Picture(img)); i++; continue }

            para.add(trimmed)
            i++
        }
        flushPara()
        return blocks to outline
    }

    private fun fenceOf(t: String): String? {
        if (t.startsWith("```")) return t.takeWhile { it == '`' }
        if (t.startsWith("~~~")) return t.takeWhile { it == '~' }
        return null
    }

    private fun langIdFor(info: String): String {
        val key = info.lowercase().removePrefix(".")
        if (key.isEmpty()) return "text"
        Kinds.codeLang[key]?.let { return it }
        return when (key) {
            "pinescript", "pine-script", "tradingview" -> "pine"
            "sh", "console", "terminal", "bash" -> "shell"
            "js", "ts", "typescript" -> "javascript"
            "py" -> "python"
            "kt" -> "kotlin"
            "yml" -> "yaml"
            "c++", "cpp", "objc" -> "c"
            "html" -> "xml"
            else -> if (Highlighter.isKnown(key)) key else "text"
        }
    }

    private fun isRule(t: String): Boolean {
        if (t.length < 3) return false
        val c = t[0]
        if (c != '-' && c != '*' && c != '_') return false
        return t.all { it == c || it == ' ' } && t.count { it == c } >= 3
    }

    private fun isTableDelimiter(line: String): Boolean {
        val t = line.trim()
        if (!t.contains('-')) return false
        return t.all { it == '-' || it == '|' || it == ':' || it == ' ' } && t.contains('|')
    }

    private fun splitRow(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }

    private fun listItemOf(line: String): Triple<Int, String, String>? {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
            .fold(0) { width, ch -> width + if (ch == '\t') 4 else 1 }
        val t = line.trimStart()
        if (t.length < 2) return null
        val c = t[0]
        if ((c == '-' || c == '*' || c == '+') && t[1] == ' ') {
            val rest = t.drop(2).trim()
            // "- [ ] task" and "- [x] done" get a box instead of a dot.
            if (rest.startsWith("[ ] ")) return Triple(indent, "todo", rest.drop(4))
            if (rest.startsWith("[x] ", true)) return Triple(indent, "done", rest.drop(4))
            return Triple(indent, "•", rest)
        }
        val digits = t.takeWhile { it.isDigit() }
        if (digits.isNotEmpty() && digits.length <= 3 && t.length > digits.length + 1) {
            val sep = t[digits.length]
            if ((sep == '.' || sep == ')') && t[digits.length + 1] == ' ') {
                return Triple(indent, "$digits.", t.drop(digits.length + 2).trim())
            }
        }
        return null
    }

    private fun imageOnly(t: String): String? {
        if (!t.startsWith("![") || !t.endsWith(")")) return null
        val close = t.indexOf("](")
        if (close < 0) return null
        return t.substring(2, close).ifBlank { "image" }
    }

    // --------------------------------------------------------------- inline

    fun inline(src: String): Inline = inline(src, Span(""))

    private fun inline(src: String, base: Span): Inline {
        val out = ArrayList<Span>()
        val sb = StringBuilder()
        var i = 0
        val n = src.length

        fun flush() {
            if (sb.isNotEmpty()) { out.add(base.copy(text = sb.toString())); sb.clear() }
        }

        while (i < n) {
            val c = src[i]

            if (c == '\\' && i + 1 < n && !src[i + 1].isLetterOrDigit()) {
                sb.append(src[i + 1]); i += 2; continue
            }

            if (c == '`') {
                val tick = src.substring(i).takeWhile { it == '`' }
                val end = src.indexOf(tick, i + tick.length)
                if (end > 0) {
                    flush()
                    out.add(base.copy(text = src.substring(i + tick.length, end).trim(), code = true))
                    i = end + tick.length; continue
                }
            }

            if (c == '!' && i + 1 < n && src[i + 1] == '[') {
                val link = linkAt(src, i + 1)
                if (link != null) {
                    flush()
                    val alt = link.first.ifBlank { "image" }
                    out.add(base.copy(text = "❑ $alt", italic = true))
                    i = link.third; continue
                }
            }

            if (c == '[') {
                val link = linkAt(src, i)
                if (link != null) {
                    flush()
                    out.addAll(inline(link.first, base.copy(link = link.second)))
                    i = link.third; continue
                }
            }

            if (c == '<') {
                val end = src.indexOf('>', i)
                val body = if (end > i) src.substring(i + 1, end) else ""
                if (end > i && (body.startsWith("http") || body.contains('@')) && !body.contains(' ')) {
                    flush()
                    out.add(base.copy(text = body, link = body))
                    i = end + 1; continue
                }
            }

            if (c == '~' && src.startsWith("~~", i)) {
                val end = src.indexOf("~~", i + 2)
                if (end > 0) {
                    flush()
                    out.addAll(inline(src.substring(i + 2, end), base.copy(strike = true)))
                    i = end + 2; continue
                }
            }

            if ((c == '*' || c == '_') && canOpen(src, i, c)) {
                val double = src.startsWith("$c$c", i)
                val delim = if (double) "$c$c" else "$c"
                val end = closingAt(src, i + delim.length, delim, c)
                if (end > 0) {
                    flush()
                    val inner = src.substring(i + delim.length, end)
                    val next = if (double) base.copy(bold = true) else base.copy(italic = true)
                    out.addAll(inline(inner, next))
                    i = end + delim.length; continue
                }
            }

            sb.append(c); i++
        }
        flush()
        return if (out.isEmpty()) listOf(base.copy(text = "")) else out
    }

    /** `[text](target)` starting at `[`, returning text, target and the index after it. */
    private fun linkAt(src: String, start: Int): Triple<String, String, Int>? {
        var depth = 0
        var i = start
        while (i < src.length) {
            when (src[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) break }
                '\\' -> i++
            }
            i++
        }
        if (i >= src.length || depth != 0) return null
        val text = src.substring(start + 1, i)
        if (i + 1 >= src.length || src[i + 1] != '(') return null
        val close = src.indexOf(')', i + 2)
        if (close < 0) return null
        val target = src.substring(i + 2, close).substringBefore(' ').trim()
        return Triple(text, target, close + 1)
    }

    /** `_` only opens emphasis at a word boundary, so snake_case survives. */
    private fun canOpen(src: String, i: Int, c: Char): Boolean {
        val next = src.getOrNull(i + if (src.startsWith("$c$c", i)) 2 else 1) ?: return false
        if (next == ' ' || next == c) return false
        if (c == '_') {
            val prev = src.getOrNull(i - 1)
            if (prev != null && (prev.isLetterOrDigit() || prev == '_')) return false
        }
        return true
    }

    private fun closingAt(src: String, from: Int, delim: String, c: Char): Int {
        var i = from
        while (i < src.length) {
            if (src[i] == '\\') { i += 2; continue }
            if (src.startsWith(delim, i) && src[i - 1] != ' ') {
                if (c == '_') {
                    val after = src.getOrNull(i + delim.length)
                    if (after != null && (after.isLetterOrDigit() || after == '_')) { i++; continue }
                }
                return i
            }
            i++
        }
        return -1
    }
}
