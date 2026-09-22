package no.mwmai.reader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import no.mwmai.reader.format.Tok
import no.mwmai.reader.format.TokType
import no.mwmai.reader.model.Inline
import no.mwmai.reader.ui.theme.CodeInk
import no.mwmai.reader.ui.theme.Palette

/** Turning parsed text into something Compose can draw. */
object Ink {

    fun colorFor(type: TokType, ink: CodeInk): Color? = when (type) {
        TokType.KEYWORD -> ink.keyword
        TokType.TYPE -> ink.type
        TokType.STRING -> ink.string
        TokType.COMMENT -> ink.comment
        TokType.NUMBER -> ink.number
        TokType.META -> ink.meta
        TokType.FUNC -> ink.func
        TokType.PUNCT -> ink.punct
        TokType.ADD -> ink.added
        TokType.DEL -> ink.removed
        TokType.PLAIN -> null
    }

    fun codeLine(
        text: String,
        toks: List<Tok>,
        ink: CodeInk,
        marks: List<IntRange> = emptyList(),
        markColor: Color = Color.Unspecified,
    ): AnnotatedString = buildAnnotatedString {
        append(text)
        val n = text.length
        for (t in toks) {
            val color = colorFor(t.type, ink) ?: continue
            val end = minOf(t.end, n)
            if (t.start in 0 until end) addStyle(SpanStyle(color = color), t.start, end)
        }
        for (r in marks) {
            val end = minOf(r.last + 1, n)
            if (r.first in 0 until end) {
                addStyle(SpanStyle(background = markColor, fontWeight = FontWeight.Bold), r.first, end)
            }
        }
    }

    fun inline(
        spans: Inline,
        palette: Palette,
        mono: FontFamily,
        marks: List<IntRange> = emptyList(),
    ): AnnotatedString = buildAnnotatedString {
        for (s in spans) {
            val start = length
            append(s.text)
            if (length == start) continue
            addStyle(
                SpanStyle(
                    fontWeight = if (s.bold) FontWeight.SemiBold else null,
                    fontStyle = if (s.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        s.strike -> TextDecoration.LineThrough
                        s.link != null -> TextDecoration.Underline
                        else -> null
                    },
                    color = if (s.link != null) palette.accent else Color.Unspecified,
                    fontFamily = if (s.code) mono else null,
                    background = if (s.code) palette.codePage else Color.Unspecified,
                ),
                start,
                length,
            )
        }
        val n = length
        for (r in marks) {
            val end = minOf(r.last + 1, n)
            if (r.first in 0 until end) {
                addStyle(SpanStyle(background = palette.mark, fontWeight = FontWeight.Bold), r.first, end)
            }
        }
    }

    /** Every case-insensitive hit of [query] in [text]. */
    fun matches(text: String, query: String): List<IntRange> {
        if (query.isBlank()) return emptyList()
        val out = ArrayList<IntRange>(4)
        var from = 0
        while (true) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            out.add(at until (at + query.length))
            from = at + query.length
        }
        return out
    }
}
