package no.mwmai.reader.format

import no.mwmai.reader.model.Block
import no.mwmai.reader.model.Outline
import no.mwmai.reader.model.plain
import java.io.InputStream

/**
 * Word, Excel, PowerPoint and OpenDocument files are zipped XML, so their text
 * can be pulled out without a 20 MB parsing library. What comes back is the
 * words, the headings, the lists and the tables, in order. It is not a layout
 * engine: page breaks, columns, fonts and floating images are not reproduced,
 * and the reader says so on screen rather than pretending otherwise.
 */
object Office {

    data class Result(val blocks: List<Block>, val outline: List<Outline>, val note: String)

    fun load(ext: String, input: InputStream): Result = when (ext) {
        "docx", "docm" -> docx(input)
        "xlsx", "xlsm" -> xlsx(input)
        "pptx", "pptm" -> pptx(input)
        "odt" -> odfText(input, slides = false)
        "odp" -> odfText(input, slides = true)
        "ods" -> odfSheet(input)
        else -> Result(listOf(Block.Para(plain("MWM Reader cannot read “.$ext”."))), emptyList(), "")
    }

    // ------------------------------------------------------------------ docx

    private fun docx(input: InputStream): Result {
        val zip = Zips.read(input) { it == "word/document.xml" }
        val doc = Zips.text(zip.find("word/document.xml"))
        if (doc.isEmpty()) return failed("word/document.xml")

        val body = Xml.bodies(doc, "w:body").firstOrNull() ?: doc
        val blocks = ArrayList<Block>()
        val outline = ArrayList<Outline>()
        var i = 0
        while (i < body.length) {
            val pAt = nextTag(body, i, "w:p")
            val tAt = nextTag(body, i, "w:tbl")
            if (pAt < 0 && tAt < 0) break
            val takeTable = tAt >= 0 && (pAt < 0 || tAt < pAt)
            val start = if (takeTable) tAt else pAt
            val gt = body.indexOf('>', start)
            if (gt < 0) break
            if (body[gt - 1] == '/') { i = gt + 1; continue }
            val closeTag = if (takeTable) "</w:tbl>" else "</w:p>"
            val end = body.indexOf(closeTag, gt)
            val frag = if (end < 0) body.substring(gt + 1) else body.substring(gt + 1, end)
            if (takeTable) {
                tableFrom(frag, "w:tr", "w:tc") { Xml.textOf(it, "w:t") }?.let { blocks.add(it) }
            } else {
                paragraph(frag, blocks, outline)
            }
            i = if (end < 0) body.length else end + closeTag.length
        }
        return Result(
            blocks,
            outline,
            "Text extracted from the document XML. Page layout, fonts and floating images are not reproduced.",
        )
    }

    private fun paragraph(p: String, blocks: MutableList<Block>, outline: MutableList<Outline>) {
        val text = Xml.textOf(p, "w:t").trim()
        if (text.isEmpty()) return
        val style = Xml.attr(p, "w:pStyle", "w:val")
        val level = headingLevel(style)
        when {
            level > 0 -> {
                outline.add(Outline(text, level, blocks.size))
                blocks.add(Block.Heading(level, plain(text)))
            }
            style.startsWith("Quote", true) ->
                blocks.add(Block.Quote(plain(text)))
            p.contains("<w:numPr") || style.contains("ListParagraph", true) ->
                blocks.add(Block.Bullet(plain(text), "•", 0))
            else -> blocks.add(Block.Para(plain(text)))
        }
    }

    private fun headingLevel(style: String): Int {
        if (style.equals("Title", true)) return 1
        if (style.equals("Subtitle", true)) return 2
        if (!style.startsWith("Heading", true)) return 0
        return style.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 6) ?: 0
    }

    // ------------------------------------------------------------------ xlsx

    private fun xlsx(input: InputStream): Result {
        val zip = Zips.read(input) {
            it == "xl/sharedStrings.xml" || it == "xl/workbook.xml" ||
                (it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml"))
        }
        val shared = Xml.bodies(Zips.text(zip.find("xl/sharedStrings.xml")), "si")
            .map { Xml.textOf(it, "t") }
        val names = Xml.bodies(Zips.text(zip.find("xl/workbook.xml")), "sheets")
            .firstOrNull()
            ?.let { frag -> Regex("<sheet[^>]*>").findAll(frag).map { Xml.attrIn(it.value, "name") }.toList() }
            .orEmpty()

        val sheetFiles = zip.names()
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { name -> name.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        if (sheetFiles.isEmpty()) return failed("xl/worksheets")

        val blocks = ArrayList<Block>()
        val outline = ArrayList<Outline>()
        sheetFiles.forEachIndexed { index, file ->
            val title = names.getOrNull(index)?.ifBlank { null } ?: "Sheet ${index + 1}"
            outline.add(Outline(title, 1, blocks.size))
            blocks.add(Block.Heading(1, plain(title)))
            val rows = sheetRows(Zips.text(zip.find(file)), shared)
            if (rows.isEmpty()) blocks.add(Block.Para(plain("(empty sheet)")))
            else blocks.add(Block.TableBlock(rows.first(), rows.drop(1)))
        }
        return Result(
            blocks,
            outline,
            "Cell values as last saved. Formulas show their stored result, and charts are not drawn.",
        )
    }

    private fun sheetRows(sheet: String, shared: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>()
        var width = 0
        for (row in Xml.bodies(sheet, "row")) {
            val cells = ArrayList<String>()
            var i = 0
            while (true) {
                val s = nextTag(row, i, "c")
                if (s < 0) break
                val gt = row.indexOf('>', s)
                if (gt < 0) break
                val open = row.substring(s, gt + 1)
                val selfClosing = row[gt - 1] == '/'
                val end = if (selfClosing) -1 else row.indexOf("</c>", gt)
                val cellBody = if (selfClosing || end < 0) "" else row.substring(gt + 1, end)
                val value = when (Xml.attrIn(open, "t")) {
                    "s" -> Xml.textOf(cellBody, "v").toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> Xml.textOf(cellBody, "t")
                    else -> Xml.textOf(cellBody, "v")
                }
                val col = columnOf(Xml.attrIn(open, "r"))
                while (col > 0 && cells.size < col) cells.add("")
                cells.add(value)
                i = if (selfClosing) gt + 1 else if (end < 0) row.length else end + 4
            }
            width = maxOf(width, cells.size)
            out.add(cells)
            if (out.size >= 5000) break
        }
        while (out.isNotEmpty() && out.last().all { it.isBlank() }) out.removeAt(out.size - 1)
        return out.map { r -> if (r.size < width) r + List(width - r.size) { "" } else r }
    }

    /** `BC12` -> column index 54, `""` -> -1. */
    fun columnOf(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (!c.isLetter()) break
            n = n * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    // ------------------------------------------------------------------ pptx

    private fun pptx(input: InputStream): Result {
        val zip = Zips.read(input) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
        val slides = zip.names()
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .sortedBy { name -> name.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        if (slides.isEmpty()) return failed("ppt/slides")

        val blocks = ArrayList<Block>()
        val outline = ArrayList<Outline>()
        slides.forEachIndexed { index, file ->
            val xml = Zips.text(zip.find(file))
            val paras = Xml.bodies(xml, "a:p")
                .map { Xml.textOf(it, "a:t").trim() }
                .filter { it.isNotEmpty() }
            val title = paras.firstOrNull()?.take(80) ?: "Slide ${index + 1}"
            outline.add(Outline("${index + 1}. $title", 1, blocks.size))
            blocks.add(Block.Heading(2, plain("Slide ${index + 1} · $title")))
            paras.drop(1).forEach { blocks.add(Block.Bullet(plain(it), "•", 0)) }
            if (index < slides.size - 1) blocks.add(Block.Rule)
        }
        return Result(blocks, outline, "Slide text in reading order. Shapes, images and animations are not drawn.")
    }

    // -------------------------------------------------------- open document

    private fun odfText(input: InputStream, slides: Boolean): Result {
        val body = odfBody(input) ?: return failed("content.xml")
        val blocks = ArrayList<Block>()
        val outline = ArrayList<Outline>()
        val frames = if (slides) Xml.bodies(body, "draw:page") else listOf(body)
        frames.forEachIndexed { index, frame ->
            if (slides) {
                outline.add(Outline("Slide ${index + 1}", 1, blocks.size))
                blocks.add(Block.Heading(2, plain("Slide ${index + 1}")))
            }
            var i = 0
            while (i < frame.length) {
                val h = nextTag(frame, i, "text:h")
                val p = nextTag(frame, i, "text:p")
                if (h < 0 && p < 0) break
                val isHeading = h >= 0 && (p < 0 || h < p)
                val tag = if (isHeading) "text:h" else "text:p"
                val start = if (isHeading) h else p
                val gt = frame.indexOf('>', start)
                if (gt < 0) break
                if (frame[gt - 1] == '/') { i = gt + 1; continue }
                val close = "</$tag>"
                val end = frame.indexOf(close, gt)
                if (end < 0) break
                val text = Html.decode(frame.substring(gt + 1, end).replace(Regex("<[^>]*>"), "")).trim()
                if (text.isNotEmpty()) {
                    if (isHeading) {
                        val level = Xml.attrIn(frame.substring(start, gt + 1), "text:outline-level")
                            .toIntOrNull()?.coerceIn(1, 6) ?: 1
                        outline.add(Outline(text, level, blocks.size))
                        blocks.add(Block.Heading(level, plain(text)))
                    } else {
                        blocks.add(Block.Para(plain(text)))
                    }
                }
                i = end + close.length
            }
        }
        return Result(blocks, outline, "Text extracted from content.xml. Layout is not reproduced.")
    }

    private fun odfSheet(input: InputStream): Result {
        val body = odfBody(input) ?: return failed("content.xml")
        val blocks = ArrayList<Block>()
        val outline = ArrayList<Outline>()
        var i = 0
        var index = 0
        while (i < body.length) {
            val at = nextTag(body, i, "table:table")
            if (at < 0) break
            val gt = body.indexOf('>', at)
            if (gt < 0) break
            if (body[gt - 1] == '/') { i = gt + 1; continue }
            val open = body.substring(at, gt + 1)
            val close = "</table:table>"
            val end = body.indexOf(close, gt)
            val frag = if (end < 0) body.substring(gt + 1) else body.substring(gt + 1, end)
            index++
            val name = Xml.attrIn(open, "table:name").ifBlank { "Sheet $index" }
            outline.add(Outline(name, 1, blocks.size))
            blocks.add(Block.Heading(1, plain(name)))
            val rows = Xml.bodies(frag, "table:table-row")
                .map { row -> Xml.bodies(row, "table:table-cell").map { Xml.textOf(it, "text:p").trim() } }
                .filter { row -> row.any { it.isNotBlank() } }
            if (rows.isEmpty()) blocks.add(Block.Para(plain("(empty sheet)")))
            else blocks.add(Block.TableBlock(rows.first(), rows.drop(1)))
            i = if (end < 0) body.length else end + close.length
        }
        return Result(blocks, outline, "Cell values only.")
    }

    private fun odfBody(input: InputStream): String? {
        val zip = Zips.read(input) { it == "content.xml" }
        val xml = Zips.text(zip.find("content.xml"))
        if (xml.isEmpty()) return null
        return Xml.bodies(xml, "office:body").firstOrNull() ?: xml
    }

    // ---------------------------------------------------------------- shared

    fun nextTag(src: String, from: Int, tag: String): Int {
        var i = from
        while (true) {
            val at = src.indexOf("<$tag", i)
            if (at < 0) return -1
            if (boundary(src, at + tag.length + 1)) return at
            i = at + tag.length + 1
        }
    }

    private fun boundary(src: String, at: Int): Boolean {
        val c = src.getOrNull(at) ?: return false
        return c.isWhitespace() || c == '>' || c == '/'
    }

    private fun tableFrom(
        frag: String,
        rowTag: String,
        cellTag: String,
        cellText: (String) -> String,
    ): Block.TableBlock? {
        val rows = Xml.bodies(frag, rowTag)
            .map { row -> Xml.bodies(row, cellTag).map { cellText(it).trim() } }
            .filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        return Block.TableBlock(rows.first(), rows.drop(1))
    }

    private fun failed(what: String): Result = Result(
        listOf(
            Block.Para(
                plain(
                    "This file has no $what inside it, so there is nothing to show. " +
                        "A legacy .doc, .xls or .ppt has to be re-saved in the modern format first.",
                ),
            ),
        ),
        emptyList(),
        "",
    )
}
