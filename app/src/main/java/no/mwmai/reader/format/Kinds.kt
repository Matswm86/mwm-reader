package no.mwmai.reader.format

import no.mwmai.reader.model.DocKind

/**
 * Extension and mime-type tables. Extension wins: a file manager reports
 * `application/octet-stream` for most source files, and `text/plain` for a
 * `.md` as often as `text/markdown`, so the name is the better evidence.
 */
object Kinds {

    /** Extensions that are source code, mapped to the highlighter language id. */
    val codeLang: Map<String, String> = buildMap {
        // The one that started this app.
        put("pine", "pine")

        put("kt", "kotlin"); put("kts", "kotlin")
        put("java", "java"); put("gradle", "java")
        put("scala", "java"); put("groovy", "java"); put("dart", "java")
        put("swift", "swift")
        put("js", "javascript"); put("mjs", "javascript"); put("cjs", "javascript")
        put("jsx", "javascript"); put("ts", "javascript"); put("tsx", "javascript")
        put("c", "c"); put("h", "c"); put("cpp", "c"); put("cc", "c"); put("cxx", "c")
        put("hpp", "c"); put("hh", "c"); put("m", "c"); put("mm", "c")
        put("cs", "csharp")
        put("go", "go")
        put("rs", "rust")
        put("py", "python"); put("pyw", "python"); put("pyi", "python")
        put("rb", "ruby")
        put("php", "php")
        put("lua", "lua")
        put("pl", "perl"); put("pm", "perl")
        put("r", "r")
        put("sh", "shell"); put("bash", "shell"); put("zsh", "shell")
        put("fish", "shell"); put("ps1", "shell")
        put("sql", "sql")
        put("json", "json"); put("jsonl", "json"); put("ndjson", "json")
        put("yaml", "yaml"); put("yml", "yaml")
        put("toml", "toml"); put("ini", "toml"); put("cfg", "toml"); put("conf", "toml")
        put("properties", "toml"); put("env", "toml")
        put("css", "css"); put("scss", "css"); put("less", "css")
        put("xml", "xml"); put("svg", "xml"); put("plist", "xml"); put("gradle.kts", "kotlin")
        put("tex", "tex")
        put("diff", "diff"); put("patch", "diff")
        put("dockerfile", "shell"); put("makefile", "shell"); put("mk", "shell")
        put("vim", "shell"); put("el", "lisp"); put("lisp", "lisp"); put("clj", "lisp")
        put("hs", "haskell")
        put("asm", "shell"); put("s", "shell")
        put("bat", "shell"); put("cmd", "shell")
        put("mq4", "c"); put("mq5", "c"); put("ex4", "c")
        put("afl", "c")
        put("thinkscript", "pine"); put("ts_script", "pine")
    }

    private val textExt = setOf(
        "txt", "text", "log", "nfo", "me", "readme", "license", "changelog",
        "srt", "vtt", "ass", "sub", "bib", "org", "rst", "adoc", "asciidoc",
    )

    private val markdownExt = setOf("md", "markdown", "mdown", "mkd", "mdx", "qmd")
    private val htmlExt = setOf("html", "htm", "xhtml")
    private val tableExt = setOf("csv", "tsv", "psv")
    private val imageExt = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif",
    )
    private val officeExt = setOf(
        "docx", "xlsx", "pptx", "odt", "ods", "odp", "docm", "xlsm", "pptm",
    )

    /** Every extension the app claims to open, for the README table. */
    fun kindOf(fileName: String, mime: String = ""): DocKind {
        val ext = extensionOf(fileName)
        when (ext) {
            "pdf" -> return DocKind.PDF
            "epub" -> return DocKind.EPUB
            "rtf" -> return DocKind.RTF
        }
        if (ext in markdownExt) return DocKind.MARKDOWN
        if (ext in htmlExt) return DocKind.HTML
        if (ext in tableExt) return DocKind.TABLE
        if (ext in imageExt) return DocKind.IMAGE
        if (ext in officeExt) return DocKind.OFFICE
        if (ext in codeLang) return DocKind.CODE
        if (ext in textExt) return DocKind.TEXT

        // No useful extension. Fall back to whatever the provider claimed.
        val m = mime.lowercase().substringBefore(';').trim()
        return when {
            m == "application/pdf" -> DocKind.PDF
            m == "application/epub+zip" -> DocKind.EPUB
            m == "application/rtf" || m == "text/rtf" -> DocKind.RTF
            m == "text/markdown" || m == "text/x-markdown" -> DocKind.MARKDOWN
            m == "text/html" || m == "application/xhtml+xml" -> DocKind.HTML
            m == "text/csv" || m == "text/tab-separated-values" -> DocKind.TABLE
            m.startsWith("image/") -> DocKind.IMAGE
            m in officeMimes -> DocKind.OFFICE
            m == "application/json" || m == "application/xml" || m == "text/xml" -> DocKind.CODE
            m.startsWith("text/") -> DocKind.TEXT
            else -> DocKind.UNSUPPORTED
        }
    }

    private val officeMimes = setOf(
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
    )

    /** Highlighter language id for a file name, `"text"` when there is none. */
    fun langOf(fileName: String): String {
        val name = fileName.lowercase()
        if (name == "dockerfile" || name == "makefile" || name.startsWith("makefile.")) return "shell"
        if (name.startsWith(".bash") || name.startsWith(".zsh") || name == ".profile") return "shell"
        if (name == "cmakelists.txt") return "shell"
        return codeLang[extensionOf(fileName)] ?: "text"
    }

    fun extensionOf(fileName: String): String {
        val base = fileName.substringAfterLast('/')
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || dot == base.length - 1) return base.lowercase()
        return base.substring(dot + 1).lowercase()
    }

    /** True when the bytes look like something other than text. */
    fun looksBinary(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        var nul = 0
        val limit = minOf(head.size, 8192)
        for (i in 0 until limit) if (head[i].toInt() == 0) nul++
        return nul * 100 > limit // more than one byte in a hundred is a NUL
    }
}
