package no.mwmai.reader

import no.mwmai.reader.format.Csv
import no.mwmai.reader.format.Highlighter
import no.mwmai.reader.format.Html
import no.mwmai.reader.format.Kinds
import no.mwmai.reader.format.Markdown
import no.mwmai.reader.format.Rtf
import no.mwmai.reader.format.Texts
import no.mwmai.reader.format.TokType
import no.mwmai.reader.model.Block
import no.mwmai.reader.model.DocKind
import no.mwmai.reader.model.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KindsTest {

    @Test
    fun `the extension decides, not the mime type a file manager guessed`() {
        // File managers routinely call a .pine file application/octet-stream.
        assertEquals(DocKind.CODE, Kinds.kindOf("MWM_ORB.pine", "application/octet-stream"))
        assertEquals("pine", Kinds.langOf("MWM_ORB.pine"))
        assertEquals(DocKind.MARKDOWN, Kinds.kindOf("NOTES.md", "text/plain"))
        assertEquals(DocKind.PDF, Kinds.kindOf("paper.PDF", ""))
        assertEquals(DocKind.TABLE, Kinds.kindOf("trades.csv", ""))
        assertEquals(DocKind.OFFICE, Kinds.kindOf("report.docx", ""))
        assertEquals(DocKind.IMAGE, Kinds.kindOf("chart.PNG", ""))
        assertEquals(DocKind.EPUB, Kinds.kindOf("book.epub", ""))
    }

    @Test
    fun `mime type is the fallback when there is no extension`() {
        assertEquals(DocKind.PDF, Kinds.kindOf("document", "application/pdf"))
        assertEquals(DocKind.TEXT, Kinds.kindOf("document", "text/plain"))
        assertEquals(DocKind.UNSUPPORTED, Kinds.kindOf("document", "application/zip"))
    }

    @Test
    fun `files named only by convention still get a language`() {
        assertEquals("shell", Kinds.langOf("Dockerfile"))
        assertEquals("shell", Kinds.langOf("Makefile"))
        assertEquals("kotlin", Kinds.langOf("build.gradle.kts"))
        assertEquals("text", Kinds.langOf("something.unknownext"))
    }

    @Test
    fun `binary content is recognised by its NUL bytes`() {
        assertTrue(Kinds.looksBinary(ByteArray(600) { if (it % 8 == 0) 0 else 65 }))
        assertFalse(Kinds.looksBinary("plain ascii text".toByteArray()))
        assertFalse(Kinds.looksBinary(ByteArray(0)))
    }
}

class TextsTest {

    @Test
    fun `a UTF-8 byte order mark is stripped`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hei".toByteArray()
        assertEquals("hei", Texts.decode(bytes))
    }

    @Test
    fun `invalid UTF-8 falls back to Latin-1 rather than filling the page with question marks`() {
        // 0xE5 alone is not valid UTF-8, but it is "å" in ISO-8859-1.
        val bytes = byteArrayOf(0x6D, 0xE5.toByte(), 0x6C)
        assertEquals("mål", Texts.decode(bytes))
    }

    @Test
    fun `all three line endings split the same way`() {
        assertEquals(listOf("a", "b", "c"), Texts.lines("a\nb\nc"))
        assertEquals(listOf("a", "b", "c"), Texts.lines("a\r\nb\r\nc"))
        assertEquals(listOf("a", "", "b"), Texts.lines("a\n\nb"))
        assertEquals(listOf(""), Texts.lines(""))
    }

    @Test
    fun `hard-wrapped prose becomes paragraphs again`() {
        val lines = listOf("one two", "three", "", "second para")
        assertEquals(listOf("one two three", "second para"), Texts.paragraphs(lines))
    }
}

class HighlighterTest {

    @Test
    fun `pine keywords and the version annotation are coloured apart`() {
        val lang = Highlighter.lang("pine")
        val (version, _) = Highlighter.scan("//@version=6", lang, 0)
        assertEquals(TokType.META, version.first().type)

        val (toks, _) = Highlighter.scan("""indicator("ORB", overlay = true)""", lang, 0)
        val types = toks.map { it.type }
        assertTrue(TokType.KEYWORD in types)
        assertTrue(TokType.STRING in types)
        assertTrue(TokType.KEYWORD in types)
    }

    @Test
    fun `a plain comment stays a comment`() {
        val lang = Highlighter.lang("pine")
        val (toks, _) = Highlighter.scan("// entry on the sweep", lang, 0)
        assertEquals(listOf(TokType.COMMENT), toks.map { it.type })
    }

    @Test
    fun `a block comment carries its state to the next line`() {
        val lang = Highlighter.lang("kotlin")
        val lines = listOf("/* start", "still inside", "done */ val x = 1")
        val states = Highlighter.states(lines, lang)
        assertEquals(Highlighter.STATE_NORMAL, states[0])
        assertEquals(Highlighter.STATE_BLOCK_COMMENT, states[1])
        assertEquals(Highlighter.STATE_BLOCK_COMMENT, states[2])
        assertEquals(Highlighter.STATE_NORMAL, states[3])

        val (last, _) = Highlighter.scan(lines[2], lang, states[2])
        assertEquals(TokType.COMMENT, last.first().type)
        assertTrue(last.any { it.type == TokType.KEYWORD })
    }

    @Test
    fun `python triple quotes span lines`() {
        val lang = Highlighter.lang("python")
        val lines = listOf("x = \"\"\"", "docstring", "\"\"\"", "y = 2")
        val states = Highlighter.states(lines, lang)
        assertEquals(Highlighter.STATE_TRIPLE, states[1])
        assertEquals(Highlighter.STATE_TRIPLE, states[2])
        assertEquals(Highlighter.STATE_NORMAL, states[3])
    }

    @Test
    fun `numbers with underscores and hex prefixes are one token`() {
        val lang = Highlighter.lang("kotlin")
        val (toks, _) = Highlighter.scan("val n = 0xFF_FF", lang, 0)
        val number = toks.first { it.type == TokType.NUMBER }
        assertEquals("0xFF_FF", "val n = 0xFF_FF".substring(number.start, number.end))
    }

    @Test
    fun `an unknown language colours nothing and never throws`() {
        val lang = Highlighter.lang("no-such-language")
        val (toks, state) = Highlighter.scan("anything at all ### 12", lang, 0)
        assertEquals(Highlighter.STATE_NORMAL, state)
        assertTrue(toks.none { it.type == TokType.KEYWORD })
    }
}

class MarkdownTest {

    @Test
    fun `headings, rules and paragraphs come out in order`() {
        val (blocks, outline) = Markdown.parse(
            listOf("# Title", "", "Some prose.", "", "---", "", "## Second"),
        )
        assertTrue(blocks[0] is Block.Heading)
        assertEquals(1, (blocks[0] as Block.Heading).level)
        assertTrue(blocks[1] is Block.Para)
        assertTrue(blocks[2] is Block.Rule)
        assertEquals(2, (blocks[3] as Block.Heading).level)
        assertEquals(listOf("Title", "Second"), outline.map { it.title })
    }

    @Test
    fun `a fenced block keeps its language and its blank lines`() {
        val (blocks, _) = Markdown.parse(
            listOf("```pine", "//@version=6", "", "plot(close)", "```", "after"),
        )
        val code = blocks.filterIsInstance<Block.CodeBlock>().single()
        assertEquals("pine", code.langId)
        assertEquals(listOf("//@version=6", "", "plot(close)"), code.lines)
        assertTrue(blocks.last() is Block.Para)
    }

    @Test
    fun `bullets, numbers and task boxes are told apart`() {
        val (blocks, _) = Markdown.parse(
            listOf("- one", "- [ ] todo", "- [x] done", "1. first"),
        )
        val bullets = blocks.filterIsInstance<Block.Bullet>()
        assertEquals(listOf("•", "todo", "done", "1."), bullets.map { it.marker })
        assertEquals("todo", bullets[1].inline.text())
    }

    @Test
    fun `a pipe table becomes a table block`() {
        val (blocks, _) = Markdown.parse(
            listOf("| a | b |", "|---|---|", "| 1 | 2 |", "| 3 | 4 |"),
        )
        val table = blocks.filterIsInstance<Block.TableBlock>().single()
        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), table.rows)
    }

    @Test
    fun `emphasis, code spans and links parse, and snake_case survives`() {
        val spans = Markdown.inline("**bold** and *thin* and `code` and [link](http://x)")
        assertTrue(spans.any { it.bold && it.text == "bold" })
        assertTrue(spans.any { it.italic && it.text == "thin" })
        assertTrue(spans.any { it.code && it.text == "code" })
        assertTrue(spans.any { it.link == "http://x" && it.text == "link" })

        val snake = Markdown.inline("call some_long_name now")
        assertEquals("call some_long_name now", snake.text())
        assertTrue(snake.none { it.italic })
    }

    @Test
    fun `front matter is shown as yaml rather than as a horizontal rule`() {
        val (blocks, _) = Markdown.parse(listOf("---", "title: x", "---", "body"))
        val code = blocks.first() as Block.CodeBlock
        assertEquals(listOf("title: x"), code.lines)
        assertEquals("yaml", code.langId)
    }
}

class HtmlTest {

    @Test
    fun `tags become blocks and entities are decoded`() {
        val (blocks, outline) = Html.parse(
            "<html><head><style>p{}</style></head><body>" +
                "<h1>Head &amp; Shoulders</h1><p>Some <b>bold</b> text.</p>" +
                "<ul><li>one</li><li>two</li></ul>" +
                "<script>alert(1)</script></body></html>",
        )
        val heading = blocks.filterIsInstance<Block.Heading>().single()
        assertEquals("Head & Shoulders", heading.inline.text())
        assertEquals(listOf("Head & Shoulders"), outline.map { it.title })
        assertTrue(blocks.filterIsInstance<Block.Para>().any { it.inline.any { s -> s.bold } })
        assertEquals(2, blocks.filterIsInstance<Block.Bullet>().size)
        // Script and style contents must not reach the page.
        assertTrue(blocks.none { it is Block.Para && "alert" in it.inline.text() })
    }

    @Test
    fun `numeric entities and pre blocks survive`() {
        val (blocks, _) = Html.parse("<pre class=\"language-python\">def f():\n    pass</pre><p>&#8212;</p>")
        val code = blocks.filterIsInstance<Block.CodeBlock>().single()
        assertEquals("python", code.langId)
        assertEquals(listOf("def f():", "    pass"), code.lines)
        assertTrue(blocks.filterIsInstance<Block.Para>().single().inline.text().contains("—"))
    }
}

class CsvTest {

    @Test
    fun `quoted fields keep their commas and newlines`() {
        val rows = Csv.parse("a,b\n\"x,y\",\"line1\nline2\"\n", ',')
        assertEquals(listOf("a", "b"), rows[0])
        assertEquals("x,y", rows[1][0])
        assertEquals("line1\nline2", rows[1][1])
    }

    @Test
    fun `a doubled quote is one quote`() {
        val rows = Csv.parse("\"he said \"\"hi\"\"\"\n", ',')
        assertEquals("he said \"hi\"", rows[0][0])
    }

    @Test
    fun `the delimiter is guessed from the file and its contents`() {
        assertEquals('\t', Csv.delimiterFor("x.tsv", "a\tb"))
        assertEquals(';', Csv.delimiterFor("x.csv", "a;b;c"))
        assertEquals(',', Csv.delimiterFor("x.csv", "a,b,c"))
    }

    @Test
    fun `short rows are padded so the grid lines up`() {
        val rows = Csv.parse("a,b,c\n1\n", ',')
        assertEquals(3, rows[1].size)
    }
}

class RtfTest {

    @Test
    fun `control words go and the words stay`() {
        val rtf = """{\rtf1\ansi{\fonttbl{\f0 Times;}}\f0\fs24 Hello\par World\par}"""
        val text = Rtf.toText(rtf)
        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("World"))
        assertFalse(text.contains("fonttbl"))
        assertFalse(text.contains("\\par"))
    }
}
