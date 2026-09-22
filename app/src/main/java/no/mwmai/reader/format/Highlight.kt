package no.mwmai.reader.format

/**
 * A small hand-written lexer. It is not a parser: it colours comments, strings,
 * numbers, keywords, type names, annotations and call sites, and leaves the
 * rest alone. That is enough to read code and it never gets slow, which matters
 * because a 200,000-line file is coloured one visible line at a time.
 *
 * Block comments and triple-quoted strings run past a line ending, so every
 * line carries the lexer state it starts in. [Highlighter.states] computes that
 * column once when the file loads.
 */
enum class TokType { PLAIN, KEYWORD, TYPE, STRING, COMMENT, NUMBER, META, FUNC, PUNCT, ADD, DEL }

data class Tok(val start: Int, val end: Int, val type: TokType)

data class Lang(
    val id: String,
    val keywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val lineComment: String? = null,
    val altLineComment: String? = null,
    val blockOpen: String? = null,
    val blockClose: String? = null,
    val quotes: String = "\"'",
    val tripleQuote: String? = null,
    val annotationPrefix: Char? = null,
    val hashIsPreprocessor: Boolean = false,
)

private fun words(s: String): Set<String> =
    s.split(' ', '\n').filter { it.isNotBlank() }.toSet()

object Highlighter {

    const val STATE_NORMAL = 0
    const val STATE_BLOCK_COMMENT = 1
    const val STATE_TRIPLE = 2

    // ------------------------------------------------------------ languages

    private val pine = Lang(
        id = "pine",
        keywords = words(
            """
            indicator strategy library import export method type enum var varip
            if else for to by while switch break continue return and or not na
            true false input request plot plotshape plotchar plotcandle plotbar
            plotarrow hline fill bgcolor barcolor alert alertcondition
            """,
        ),
        types = words(
            """
            int float bool string color line label box table array matrix map
            linefill polyline series simple const chart open high low close
            volume time time_close bar_index hl2 hlc3 ohlc4 hlcc4 last_bar_index
            ta math str syminfo timeframe ticker barstate dayofweek session
            runtime log currency dividends earnings nz timenow year month
            weekofyear dayofmonth hour minute second na_value
            """,
        ),
        lineComment = "//",
        quotes = "\"'",
    )

    private val cLike = words(
        """
        if else for while do switch case default break continue return new
        delete this super null true false void try catch finally throw throws
        class interface enum extends implements public private protected static
        final abstract synchronized volatile transient native import package
        instanceof typeof in of let const var function async await yield
        namespace using struct union typedef sizeof goto inline operator
        template typename virtual explicit friend constexpr nullptr auto
        """,
    )

    private val cTypes = words(
        """
        int long short char float double bool boolean byte unsigned signed
        String Int Long Double Float Boolean Char Byte Short Any Unit Nothing
        List Map Set Array Object Number Promise void size_t uint8_t uint16_t
        uint32_t uint64_t int8_t int16_t int32_t int64_t
        """,
    )

    private val langs: Map<String, Lang> = buildMap {
        put("pine", pine)
        put(
            "kotlin",
            Lang(
                "kotlin",
                keywords = cLike + words(
                    """
                    fun val object companion data sealed suspend when is as by
                    lateinit init override open internal inout reified crossinline
                    noinline tailrec vararg get set constructor annotation actual
                    expect typealias out where
                    """,
                ),
                types = cTypes + words("Sequence Flow StateFlow MutableList MutableMap CharSequence"),
                lineComment = "//",
                blockOpen = "/*",
                blockClose = "*/",
                tripleQuote = "\"\"\"",
                annotationPrefix = '@',
            ),
        )
        put(
            "java",
            Lang(
                "java", cLike + words("record yield var sealed permits module requires"),
                cTypes, "//", null, "/*", "*/", "\"'", null, '@',
            ),
        )
        put(
            "javascript",
            Lang(
                "javascript",
                cLike + words("export default from as require module exports undefined debugger declare readonly type"),
                cTypes + words("Symbol BigInt RegExp Date JSON Math console window document"),
                "//", null, "/*", "*/", "\"'`", null, '@',
            ),
        )
        put(
            "c",
            Lang(
                "c", cLike, cTypes, "//", null, "/*", "*/", "\"'", null, null,
                hashIsPreprocessor = true,
            ),
        )
        put(
            "csharp",
            Lang(
                "csharp",
                cLike + words("using namespace partial readonly ref out params base where async await var record"),
                cTypes + words("string object decimal var Task IEnumerable"),
                "//", null, "/*", "*/", "\"'", null, null,
            ),
        )
        put(
            "go",
            Lang(
                "go",
                words(
                    """
                    package import func var const type struct interface map chan go
                    defer select if else for range switch case default break continue
                    return fallthrough goto nil true false make new len cap append copy
                    panic recover
                    """,
                ),
                words("int int8 int16 int32 int64 uint uintptr float32 float64 complex64 complex128 string bool byte rune error any"),
                "//", null, "/*", "*/", "\"'`", null, null,
            ),
        )
        put(
            "rust",
            Lang(
                "rust",
                words(
                    """
                    fn let mut const static struct enum impl trait for in if else
                    match loop while break continue return use mod pub crate self
                    super as dyn ref move where unsafe async await type extern box
                    true false None Some Ok Err
                    """,
                ),
                words("i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str String Vec Option Result HashMap Box Rc Arc"),
                "//", null, "/*", "*/", "\"'", null, '#',
            ),
        )
        put(
            "swift",
            Lang(
                "swift",
                cLike + words("func let guard defer extension protocol associatedtype mutating some any lazy willSet didSet repeat"),
                cTypes + words("Double Int String Bool Array Dictionary Optional Self"),
                "//", null, "/*", "*/", "\"'", "\"\"\"", '@',
            ),
        )
        put(
            "python",
            Lang(
                "python",
                words(
                    """
                    def class lambda if elif else for while break continue return
                    yield import from as pass raise try except finally with global
                    nonlocal assert del and or not in is None True False async await
                    match case self
                    """,
                ),
                words("int float str bool list dict set tuple bytes object type range enumerate zip len print open super Exception ValueError TypeError KeyError Path"),
                "#", null, null, null, "\"'", "\"\"\"", '@',
            ),
        )
        put(
            "ruby",
            Lang(
                "ruby",
                words("def class module end if elsif else unless case when while until for in do begin rescue ensure raise yield return self nil true false require require_relative attr_accessor attr_reader lambda proc puts"),
                emptySet(), "#", null, null, null, "\"'", null, null,
            ),
        )
        put(
            "php",
            Lang(
                "php",
                cLike + words("echo print elseif endif foreach endforeach fn global isset unset array"),
                cTypes, "//", "#", "/*", "*/", "\"'", null, null,
            ),
        )
        put(
            "lua",
            Lang(
                "lua",
                words("and break do else elseif end false for function goto if in local nil not or repeat return then true until while"),
                words("string table math io os coroutine"),
                "--", null, "--[[", "]]", "\"'", null, null,
            ),
        )
        put(
            "perl",
            Lang(
                "perl",
                words("my our local sub if elsif else unless while until for foreach do return last next redo use require package BEGIN END print printf die warn"),
                emptySet(), "#", null, null, null, "\"'", null, null,
            ),
        )
        put(
            "r",
            Lang(
                "r",
                words("if else for while repeat function return break next TRUE FALSE NULL NA Inf NaN library require"),
                words("c vector list matrix data.frame factor numeric character logical integer"),
                "#", null, null, null, "\"'", null, null,
            ),
        )
        put(
            "shell",
            Lang(
                "shell",
                words(
                    """
                    if then else elif fi for in do done while until case esac
                    function return exit break continue local export readonly
                    declare source set unset shift trap echo printf cd test
                    FROM RUN CMD COPY ADD ENV WORKDIR ENTRYPOINT EXPOSE ARG LABEL VOLUME USER
                    """,
                ),
                emptySet(), "#", null, null, null, "\"'", null, null,
            ),
        )
        put(
            "sql",
            Lang(
                "sql",
                words(
                    """
                    select from where group by having order limit offset insert
                    into values update set delete create table view index drop
                    alter add column primary key foreign references join left
                    right inner outer full on as union all distinct and or not
                    null is in between like exists case when then else end with
                    returning conflict do nothing default constraint unique check
                    begin commit rollback transaction
                    SELECT FROM WHERE GROUP BY HAVING ORDER LIMIT OFFSET INSERT
                    INTO VALUES UPDATE SET DELETE CREATE TABLE VIEW INDEX DROP
                    ALTER ADD COLUMN PRIMARY KEY FOREIGN REFERENCES JOIN LEFT
                    RIGHT INNER OUTER FULL ON AS UNION ALL DISTINCT AND OR NOT
                    NULL IS IN BETWEEN LIKE EXISTS CASE WHEN THEN ELSE END WITH
                    """,
                ),
                words("int integer bigint smallint text varchar char decimal numeric real double float date timestamp boolean serial uuid json jsonb"),
                "--", null, "/*", "*/", "\"'", null, null,
            ),
        )
        put(
            "json",
            Lang("json", words("true false null"), emptySet(), null, null, null, null, "\""),
        )
        put(
            "yaml",
            Lang(
                "yaml", words("true false null yes no on off"), emptySet(),
                "#", null, null, null, "\"'", null, null,
            ),
        )
        put(
            "toml",
            Lang(
                "toml", words("true false"), emptySet(), "#", ";", null, null, "\"'", null, null,
            ),
        )
        put(
            "css",
            Lang(
                "css",
                words("important media import charset keyframes supports font-face root from to and not only"),
                emptySet(), null, null, "/*", "*/", "\"'", null, '@',
            ),
        )
        put(
            "xml",
            Lang("xml", emptySet(), emptySet(), null, null, "<!--", "-->", "\"'"),
        )
        put(
            "tex",
            Lang("tex", emptySet(), emptySet(), "%", null, null, null, "\"", null, '\\'),
        )
        put(
            "lisp",
            Lang(
                "lisp",
                words("defun defmacro defvar defparameter let let* lambda if cond when unless setq setf progn loop do dolist dotimes defn def fn ns require"),
                emptySet(), ";", null, null, null, "\"", null, null,
            ),
        )
        put(
            "haskell",
            Lang(
                "haskell",
                words("module where import data type newtype class instance deriving do case of let in if then else"),
                words("Int Integer Double Float Bool Char String Maybe Either IO"),
                "--", null, "{-", "-}", "\"'", null, null,
            ),
        )
        put("diff", Lang("diff"))
        put("text", Lang("text"))
    }

    fun lang(id: String): Lang = langs[id] ?: langs.getValue("text")

    fun isKnown(id: String): Boolean = id != "text" && langs.containsKey(id)

    // ----------------------------------------------------------- line states

    /** The lexer state each line begins in. Computed once per file. */
    fun states(lines: List<String>, lang: Lang): IntArray {
        val out = IntArray(lines.size + 1)
        if (lang.blockOpen == null && lang.tripleQuote == null) return out
        var s = STATE_NORMAL
        for (i in lines.indices) {
            out[i] = s
            s = scan(lines[i], lang, s).second
        }
        out[lines.size] = s
        return out
    }

    // ---------------------------------------------------------------- lexing

    /** Tokens for one line, plus the state the next line starts in. */
    fun scan(line: String, lang: Lang, stateIn: Int): Pair<List<Tok>, Int> {
        if (lang.id == "diff") return scanDiff(line) to STATE_NORMAL
        if (line.isEmpty()) return emptyList<Tok>() to stateIn

        val out = ArrayList<Tok>(16)
        var i = 0
        var state = stateIn

        if (state == STATE_BLOCK_COMMENT) {
            val close = lang.blockClose
            val at = if (close == null) -1 else line.indexOf(close)
            if (at < 0) {
                out.add(Tok(0, line.length, TokType.COMMENT))
                return out to STATE_BLOCK_COMMENT
            }
            out.add(Tok(0, at + close!!.length, TokType.COMMENT))
            i = at + close.length
            state = STATE_NORMAL
        } else if (state == STATE_TRIPLE) {
            val q = lang.tripleQuote
            val at = if (q == null) -1 else line.indexOf(q)
            if (at < 0) {
                out.add(Tok(0, line.length, TokType.STRING))
                return out to STATE_TRIPLE
            }
            out.add(Tok(0, at + q!!.length, TokType.STRING))
            i = at + q.length
            state = STATE_NORMAL
        }

        val n = line.length
        while (i < n) {
            val c = line[i]

            if (c == ' ' || c == '\t') { i++; continue }

            // Preprocessor / shebang lines.
            if (lang.hashIsPreprocessor && c == '#' && line.take(i).isBlank()) {
                out.add(Tok(i, n, TokType.META)); break
            }

            // Line comments. `//@version=6` in Pine is an annotation, not a note.
            val lc = lang.lineComment
            val alc = lang.altLineComment
            if (lc != null && line.startsWith(lc, i)) {
                val after = i + lc.length
                val type = if (after < n && line[after] == '@') TokType.META else TokType.COMMENT
                out.add(Tok(i, n, type)); break
            }
            if (alc != null && line.startsWith(alc, i)) {
                out.add(Tok(i, n, TokType.COMMENT)); break
            }

            // Block comments.
            val bo = lang.blockOpen
            val bc = lang.blockClose
            if (bo != null && bc != null && line.startsWith(bo, i)) {
                val at = line.indexOf(bc, i + bo.length)
                if (at < 0) {
                    out.add(Tok(i, n, TokType.COMMENT))
                    return out to STATE_BLOCK_COMMENT
                }
                out.add(Tok(i, at + bc.length, TokType.COMMENT))
                i = at + bc.length
                continue
            }

            // Triple-quoted strings.
            val tq = lang.tripleQuote
            if (tq != null && line.startsWith(tq, i)) {
                val at = line.indexOf(tq, i + tq.length)
                if (at < 0) {
                    out.add(Tok(i, n, TokType.STRING))
                    return out to STATE_TRIPLE
                }
                out.add(Tok(i, at + tq.length, TokType.STRING))
                i = at + tq.length
                continue
            }

            // Single-line strings, with backslash escapes.
            if (lang.quotes.indexOf(c) >= 0) {
                var j = i + 1
                while (j < n) {
                    if (line[j] == '\\') { j += 2; continue }
                    if (line[j] == c) { j++; break }
                    j++
                }
                out.add(Tok(i, minOf(j, n), TokType.STRING))
                i = minOf(j, n)
                continue
            }

            // Annotations and attributes: @Composable, #[derive], \section.
            val ap = lang.annotationPrefix
            if (ap != null && c == ap) {
                var j = i + 1
                if (j < n && line[j] == '[') {
                    val close = line.indexOf(']', j)
                    j = if (close < 0) n else close + 1
                } else {
                    while (j < n && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '.')) j++
                }
                if (j > i + 1) { out.add(Tok(i, j, TokType.META)); i = j; continue }
            }

            // Numbers, including 0x/0b, underscores, exponents and suffixes.
            if (c.isDigit() || (c == '.' && i + 1 < n && line[i + 1].isDigit() && !prevIsIdent(line, i))) {
                var j = i
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) {
                    if ((line[j] == 'e' || line[j] == 'E') && j + 1 < n &&
                        (line[j + 1] == '+' || line[j + 1] == '-')
                    ) {
                        j += 2; continue
                    }
                    j++
                }
                out.add(Tok(i, j, TokType.NUMBER)); i = j; continue
            }

            // Identifiers.
            if (c.isLetter() || c == '_' || c == '$') {
                var j = i
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '$')) j++
                val word = line.substring(i, j)
                val type = when {
                    word in lang.keywords -> TokType.KEYWORD
                    word in lang.types -> TokType.TYPE
                    j < n && line[j] == '(' -> TokType.FUNC
                    word.isNotEmpty() && word[0].isUpperCase() && lang.types.isNotEmpty() -> TokType.TYPE
                    else -> TokType.PLAIN
                }
                if (type != TokType.PLAIN) out.add(Tok(i, j, type))
                i = j; continue
            }

            // Everything else is punctuation, merged into runs.
            var j = i
            while (j < n && !line[j].isLetterOrDigit() && line[j] != ' ' && line[j] != '\t' &&
                line[j] != '_' && line[j] != '$' && lang.quotes.indexOf(line[j]) < 0
            ) {
                if (lc != null && line.startsWith(lc, j)) break
                if (bo != null && line.startsWith(bo, j)) break
                if (ap != null && line[j] == ap) break
                j++
            }
            if (j == i) j++
            out.add(Tok(i, j, TokType.PUNCT))
            i = j
        }
        return out to state
    }

    private fun prevIsIdent(line: String, i: Int): Boolean {
        if (i == 0) return false
        val p = line[i - 1]
        return p.isLetterOrDigit() || p == '_' || p == ')'
    }

    private fun scanDiff(line: String): List<Tok> = when {
        line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") ->
            listOf(Tok(0, line.length, TokType.META))
        line.startsWith("@@") -> listOf(Tok(0, line.length, TokType.META))
        line.startsWith("+") -> listOf(Tok(0, line.length, TokType.ADD))
        line.startsWith("-") -> listOf(Tok(0, line.length, TokType.DEL))
        else -> emptyList()
    }
}
