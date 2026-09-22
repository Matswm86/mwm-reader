package no.mwmai.reader.format

import no.mwmai.reader.model.Block
import no.mwmai.reader.model.Outline
import no.mwmai.reader.model.plain
import java.io.InputStream

/**
 * EPUB 2 and 3. The archive is read once, the OPF gives the reading order, and
 * every spine document goes through the same HTML converter the reader uses for
 * a loose .html file, so a book and a web page look the same on screen.
 *
 * Embedded images, fonts and stylesheets are skipped: the point is the text.
 */
object Epub {

    data class Book(
        val title: String,
        val author: String,
        val blocks: List<Block>,
        val outline: List<Outline>,
    )

    private fun isDocument(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm") ||
            n.endsWith(".opf") || n.endsWith(".ncx") || n.endsWith(".xml")
    }

    fun load(input: InputStream): Book {
        val zip = Zips.read(input) { isDocument(it) }

        val container = Zips.text(zip.find("META-INF/container.xml"))
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1)
            ?: zip.names().firstOrNull { it.lowercase().endsWith(".opf") }
            ?: return Book("", "", listOf(Block.Para(plain("No OPF package file: this does not look like an EPUB."))), emptyList())

        val opf = Zips.text(zip.find(opfPath))
        val base = opfPath.substringBeforeLast('/', "")

        val title = Xml.bodies(opf, "dc:title").firstOrNull()?.let { Html.stripTags(it) }.orEmpty()
        val author = Xml.bodies(opf, "dc:creator").firstOrNull()?.let { Html.stripTags(it) }.orEmpty()

        // manifest id -> href
        val hrefById = HashMap<String, String>()
        val idByHref = HashMap<String, String>()
        for (m in Regex("<item\\b[^>]*>").findAll(opf)) {
            val id = Xml.attrIn(m.value, "id")
            val href = Xml.attrIn(m.value, "href")
            if (id.isNotEmpty() && href.isNotEmpty()) {
                hrefById[id] = href
                idByHref[href] = id
            }
        }
        val spine = Regex("<itemref\\b[^>]*>").findAll(opf)
            .mapNotNull { hrefById[Xml.attrIn(it.value, "idref")] }
            .toList()
            .ifEmpty { hrefById.values.filter { it.lowercase().endsWith(".xhtml") || it.lowercase().endsWith(".html") } }

        val titles = tocTitles(zip, base, hrefById)

        val blocks = ArrayList<Block>()
        val outline = ArrayList<Outline>()
        var chapter = 0
        for (href in spine) {
            val path = resolve(base, href.substringBefore('#'))
            val bytes = zip.find(path) ?: continue
            val html = bytes.toString(Charsets.UTF_8)
            val (chBlocks, chOutline) = Html.parse(html)
            if (chBlocks.isEmpty()) continue
            chapter++
            val name = titles[href.substringBefore('#')]
                ?: titles[path]
                ?: Xml.bodies(html, "title").firstOrNull()?.let { Html.stripTags(it) }?.ifBlank { null }
                ?: chBlocks.filterIsInstance<Block.Heading>().firstOrNull()?.inline?.joinToString("") { it.text }
                ?: "Chapter $chapter"

            outline.add(Outline(name, 1, blocks.size))
            if (chBlocks.firstOrNull() !is Block.Heading) {
                blocks.add(Block.Heading(2, plain(name)))
            }
            val offset = blocks.size
            blocks.addAll(chBlocks)
            chOutline.forEach { outline.add(it.copy(level = minOf(6, it.level + 1), blockIndex = it.blockIndex + offset)) }
            blocks.add(Block.Rule)
        }
        if (blocks.isEmpty()) {
            blocks.add(Block.Para(plain("The book has no readable text sections.")))
        }
        return Book(title, author, blocks, outline)
    }

    /** Chapter names from the EPUB 2 NCX or the EPUB 3 nav document. */
    private fun tocTitles(
        zip: Zips.Archive,
        base: String,
        hrefById: Map<String, String>,
    ): Map<String, String> {
        val out = HashMap<String, String>()

        val ncxName = zip.names().firstOrNull { it.lowercase().endsWith(".ncx") }
        if (ncxName != null) {
            val ncx = Zips.text(zip.find(ncxName))
            for (point in Xml.bodies(ncx, "navPoint")) {
                val label = Xml.bodies(point, "navLabel").firstOrNull()
                    ?.let { Xml.textOf(it, "text") }.orEmpty().trim()
                val src = Regex("<content\\b[^>]*>").find(point)?.let { Xml.attrIn(it.value, "src") }
                    .orEmpty().substringBefore('#')
                if (label.isNotEmpty() && src.isNotEmpty()) {
                    out[src] = label
                    out[resolve(base, src)] = label
                }
            }
        }

        val navName = hrefById.values.firstOrNull { it.contains("nav", true) && it.endsWith(".xhtml", true) }
        if (navName != null) {
            val nav = Zips.text(zip.find(resolve(base, navName)))
            for (m in Regex("<a\\b[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(nav)) {
                val href = m.groupValues[1].substringBefore('#')
                val label = Html.stripTags(m.groupValues[2])
                if (href.isNotEmpty() && label.isNotEmpty()) {
                    out.putIfAbsent(href, label)
                    out.putIfAbsent(resolve(base, href), label)
                }
            }
        }
        return out
    }

    /** Join an OPF-relative href onto the package directory, resolving `..`. */
    fun resolve(base: String, href: String): String {
        val clean = href.removePrefix("./")
        if (clean.startsWith("/")) return clean.trimStart('/')
        val parts = ArrayList<String>()
        if (base.isNotEmpty()) parts.addAll(base.split('/').filter { it.isNotEmpty() })
        for (part in clean.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }
}
