package no.mwmai.reader.format

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Bytes to text, without guessing wrong and filling the screen with U+FFFD. */
object Texts {

    /**
     * A byte-order mark decides it. Otherwise UTF-8 is tried strictly, and only
     * when that fails does the text fall back to Latin-1, which can decode any
     * byte sequence and is the right answer for old European plain-text files.
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, StandardCharsets.ISO_8859_1)
        }
    }

    /** Split on any of the three line endings, keeping empty lines. */
    fun lines(text: String): List<String> {
        val out = ArrayList<String>(text.length / 40 + 8)
        var start = 0
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '\n') {
                out.add(text.substring(start, i)); i++; start = i
            } else if (c == '\r') {
                out.add(text.substring(start, i))
                i += if (i + 1 < n && text[i + 1] == '\n') 2 else 1
                start = i
            } else {
                i++
            }
        }
        if (start < n || out.isEmpty()) out.add(text.substring(start))
        return out
    }

    /** Turn a hard-wrapped file back into paragraphs for the prose renderer. */
    fun paragraphs(lines: List<String>): List<String> {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        for (line in lines) {
            if (line.isBlank()) {
                if (buf.isNotEmpty()) { out.add(buf.toString()); buf.setLength(0) }
            } else {
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(line.trim())
            }
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }
}

/** RFC 4180 delimited data, with quoted fields and embedded newlines. */
object Csv {

    fun delimiterFor(fileName: String, sample: String): Char = when {
        fileName.endsWith(".tsv", true) -> '\t'
        fileName.endsWith(".psv", true) -> '|'
        sample.count { it == '\t' } > sample.count { it == ',' } -> '\t'
        sample.count { it == ';' } > sample.count { it == ',' } -> ';'
        else -> ','
    }

    fun parse(text: String, delimiter: Char, maxRows: Int = 20_000): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length

        fun endField() { row.add(field.toString()); field.setLength(0) }
        fun endRow() {
            endField()
            if (row.size > 1 || row.firstOrNull()?.isNotEmpty() == true) rows.add(row)
            row = ArrayList()
        }

        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') { field.append('"'); i += 2; continue }
                    inQuotes = false; i++; continue
                }
                field.append(c); i++; continue
            }
            when (c) {
                '"' -> { inQuotes = true; i++ }
                delimiter -> { endField(); i++ }
                '\n' -> { endRow(); i++ }
                '\r' -> { endRow(); i += if (i + 1 < n && text[i + 1] == '\n') 2 else 1 }
                else -> { field.append(c); i++ }
            }
            if (rows.size >= maxRows) return rows
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        val width = rows.maxOfOrNull { it.size } ?: 0
        return rows.map { if (it.size < width) it + List(width - it.size) { "" } else it }
    }
}

/** Rich Text Format, reduced to its words. */
object Rtf {

    fun toText(src: String): String {
        val sb = StringBuilder(src.length / 2)
        var i = 0
        var depth = 0
        var skipGroupAt = -1
        val n = src.length

        while (i < n) {
            val c = src[i]
            when {
                c == '{' -> { depth++; i++ }
                c == '}' -> {
                    // Only the brace that closes the group the skip started in
                    // ends the skip; nested groups inside it stay skipped.
                    if (skipGroupAt == depth) skipGroupAt = -1
                    depth--; i++
                }
                c == '\\' -> {
                    if (i + 1 >= n) break
                    val next = src[i + 1]
                    if (!next.isLetter()) {
                        when (next) {
                            '\'' -> {
                                val hex = src.substring(i + 2, minOf(n, i + 4))
                                hex.toIntOrNull(16)?.let { if (skipGroupAt < 0) sb.append(it.toChar()) }
                                i += 4
                            }
                            '\\', '{', '}' -> { if (skipGroupAt < 0) sb.append(next); i += 2 }
                            '*' -> { skipGroupAt = depth; i += 2 }
                            '~' -> { if (skipGroupAt < 0) sb.append(' '); i += 2 }
                            else -> i += 2
                        }
                        continue
                    }
                    var j = i + 1
                    while (j < n && src[j].isLetter()) j++
                    val word = src.substring(i + 1, j)
                    // Optional numeric parameter.
                    var k = j
                    if (k < n && (src[k] == '-' || src[k].isDigit())) {
                        k++
                        while (k < n && src[k].isDigit()) k++
                    }
                    if (k < n && src[k] == ' ') k++
                    when (word) {
                        "par", "line", "pard" -> if (skipGroupAt < 0) sb.append('\n')
                        "tab" -> if (skipGroupAt < 0) sb.append('\t')
                        "cell", "row" -> if (skipGroupAt < 0) sb.append('\t')
                        "u" -> {
                            val code = src.substring(j, k).trim().toIntOrNull()
                            if (code != null && skipGroupAt < 0) sb.append(code.toChar())
                        }
                        "fonttbl", "colortbl", "stylesheet", "info", "pict", "generator",
                        "listtable", "listoverridetable", "rsidtbl", "themedata", "datastore",
                        -> skipGroupAt = depth
                    }
                    i = k
                }
                else -> { if (skipGroupAt < 0) sb.append(c); i++ }
            }
        }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
