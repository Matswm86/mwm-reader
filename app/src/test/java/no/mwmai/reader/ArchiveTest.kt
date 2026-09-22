package no.mwmai.reader

import kotlinx.serialization.json.Json
import no.mwmai.reader.format.Epub
import no.mwmai.reader.format.Office
import no.mwmai.reader.model.AppState
import no.mwmai.reader.model.Block
import no.mwmai.reader.model.DocRef
import no.mwmai.reader.model.Recent
import no.mwmai.reader.model.Settings
import no.mwmai.reader.model.ThemeChoice
import no.mwmai.reader.model.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun zipOf(vararg entries: Pair<String, String>): ByteArrayInputStream {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, body) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(body.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return ByteArrayInputStream(out.toByteArray())
}

class OfficeTest {

    @Test
    fun `a docx gives headings, paragraphs and its table`() {
        val document = """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://x"><w:body>
              <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Trade plan</w:t></w:r></w:p>
              <w:p><w:r><w:t>Enter on the </w:t></w:r><w:r><w:t>sweep.</w:t></w:r></w:p>
              <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/></w:numPr></w:pPr><w:r><w:t>Stop below the low</w:t></w:r></w:p>
              <w:tbl>
                <w:tr><w:tc><w:p><w:r><w:t>Symbol</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Size</w:t></w:r></w:p></w:tc></w:tr>
                <w:tr><w:tc><w:p><w:r><w:t>MNQ</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>2</w:t></w:r></w:p></w:tc></w:tr>
              </w:tbl>
            </w:body></w:document>
        """.trimIndent()

        val result = Office.load("docx", zipOf("word/document.xml" to document))
        val heading = result.blocks.filterIsInstance<Block.Heading>().single()
        assertEquals("Trade plan", heading.inline.text())
        assertEquals(1, heading.level)
        assertEquals(listOf("Trade plan"), result.outline.map { it.title })

        val para = result.blocks.filterIsInstance<Block.Para>().first()
        assertEquals("Enter on the sweep.", para.inline.text())

        val bullet = result.blocks.filterIsInstance<Block.Bullet>().single()
        assertEquals("Stop below the low", bullet.inline.text())

        val table = result.blocks.filterIsInstance<Block.TableBlock>().single()
        assertEquals(listOf("Symbol", "Size"), table.header)
        assertEquals(listOf(listOf("MNQ", "2")), table.rows)
        assertTrue(result.note.isNotBlank())
    }

    @Test
    fun `an xlsx resolves its shared strings and sheet name`() {
        val workbook = """<workbook><sheets><sheet name="Trades" sheetId="1"/></sheets></workbook>"""
        val shared = """<sst><si><t>symbol</t></si><si><t>MNQ</t></si></sst>"""
        val sheet = """
            <worksheet><sheetData>
              <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1"><v>5</v></c></row>
              <row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2"><v>7</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()

        val result = Office.load(
            "xlsx",
            zipOf(
                "xl/workbook.xml" to workbook,
                "xl/sharedStrings.xml" to shared,
                "xl/worksheets/sheet1.xml" to sheet,
            ),
        )
        assertEquals("Trades", result.blocks.filterIsInstance<Block.Heading>().single().inline.text())
        val table = result.blocks.filterIsInstance<Block.TableBlock>().single()
        assertEquals(listOf("symbol", "5"), table.header)
        assertEquals(listOf(listOf("MNQ", "7")), table.rows)
    }

    @Test
    fun `a pptx gives one section per slide`() {
        val slide = { title: String, bullet: String ->
            """<p:sld><p:cSld><a:p><a:r><a:t>$title</a:t></a:r></a:p>
               <a:p><a:r><a:t>$bullet</a:t></a:r></a:p></p:cSld></p:sld>"""
        }
        val result = Office.load(
            "pptx",
            zipOf(
                "ppt/slides/slide1.xml" to slide("Setup", "Sweep then close back in"),
                "ppt/slides/slide2.xml" to slide("Risk", "One contract"),
            ),
        )
        assertEquals(2, result.outline.size)
        assertTrue(result.outline[0].title.contains("Setup"))
        assertTrue(result.outline[1].title.contains("Risk"))
        assertTrue(result.blocks.filterIsInstance<Block.Bullet>().any { it.inline.text() == "One contract" })
    }

    @Test
    fun `a legacy binary doc says so instead of showing rubbish`() {
        val result = Office.load("docx", ByteArrayInputStream(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())))
        assertTrue(result.blocks.filterIsInstance<Block.Para>().single().inline.text().contains(".doc"))
    }

    @Test
    fun `spreadsheet column letters map to indexes`() {
        assertEquals(0, Office.columnOf("A1"))
        assertEquals(1, Office.columnOf("B7"))
        assertEquals(26, Office.columnOf("AA3"))
        assertEquals(-1, Office.columnOf(""))
    }
}

class EpubTest {

    private val container = """
        <?xml version="1.0"?>
        <container><rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>
    """.trimIndent()

    private val opf = """
        <?xml version="1.0"?>
        <package><metadata>
          <dc:title>The Sweep</dc:title><dc:creator>A Writer</dc:creator>
        </metadata>
        <manifest>
          <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
          <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
          <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
        </manifest>
        <spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/></spine></package>
    """.trimIndent()

    private val ncx = """
        <?xml version="1.0"?>
        <ncx><navMap>
          <navPoint><navLabel><text>Opening</text></navLabel><content src="ch1.xhtml"/></navPoint>
          <navPoint><navLabel><text>The trade</text></navLabel><content src="text/ch2.xhtml#top"/></navPoint>
        </navMap></ncx>
    """.trimIndent()

    @Test
    fun `spine order, chapter names and text all come through`() {
        val book = Epub.load(
            zipOf(
                "META-INF/container.xml" to container,
                "OEBPS/book.opf" to opf,
                "OEBPS/toc.ncx" to ncx,
                "OEBPS/ch1.xhtml" to "<html><body><p>It began at the open.</p></body></html>",
                "OEBPS/text/ch2.xhtml" to "<html><body><p>Then price swept the high.</p></body></html>",
            ),
        )
        assertEquals("The Sweep", book.title)
        assertEquals("A Writer", book.author)
        assertEquals(listOf("Opening", "The trade"), book.outline.filter { it.level == 1 }.map { it.title })
        val prose = book.blocks.filterIsInstance<Block.Para>().joinToString(" ") { it.inline.text() }
        assertTrue(prose.contains("It began at the open."))
        assertTrue(prose.contains("Then price swept the high."))
        assertTrue(prose.indexOf("It began") < prose.indexOf("Then price"))
    }

    @Test
    fun `relative hrefs resolve against the package folder`() {
        assertEquals("OEBPS/ch1.xhtml", Epub.resolve("OEBPS", "ch1.xhtml"))
        assertEquals("OEBPS/text/ch2.xhtml", Epub.resolve("OEBPS", "text/ch2.xhtml"))
        assertEquals("images/x.png", Epub.resolve("OEBPS/text", "../../images/x.png"))
        assertEquals("ch1.xhtml", Epub.resolve("", "./ch1.xhtml"))
    }

    @Test
    fun `a zip that is not a book says so rather than crashing`() {
        val book = Epub.load(zipOf("readme.txt" to "not a book"))
        assertTrue(book.blocks.isNotEmpty())
    }
}

class StateTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `settings and recents survive a save and load`() {
        val state = AppState(
            settings = Settings(theme = ThemeChoice.SEPIA.name, fontSize = 21, wrapCode = false),
            recents = listOf(
                Recent(
                    doc = DocRef("content://x/1", "MWM_ORB.pine", "text/plain", 4096),
                    openedAt = 1_700_000_000_000,
                    lineIndex = 340,
                ),
            ),
        )
        val round = json.decodeFromString(AppState.serializer(), json.encodeToString(AppState.serializer(), state))
        assertEquals(state, round)
        assertEquals(ThemeChoice.SEPIA, round.settings.themeChoice)
        assertEquals(340, round.recents.single().lineIndex)
    }

    @Test
    fun `a settings file written by an older build still loads`() {
        val old = """{"settings":{"theme":"PAPER","fontSize":15,"somethingRemoved":true},"recents":[]}"""
        val state = json.decodeFromString(AppState.serializer(), old)
        assertEquals(15, state.settings.fontSize)
        assertEquals(ThemeChoice.PAPER, state.settings.themeChoice)
    }

    @Test
    fun `an unknown theme name does not crash the reader`() {
        assertEquals(ThemeChoice.SYSTEM, Settings(theme = "NEON").themeChoice)
    }
}
