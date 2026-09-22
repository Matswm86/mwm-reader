package no.mwmai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.reader.format.Highlighter
import no.mwmai.reader.model.Settings
import no.mwmai.reader.ui.theme.LocalPalette
import no.mwmai.reader.ui.theme.LocalSettings
import no.mwmai.reader.ui.theme.Mono
import no.mwmai.reader.ui.theme.familyFor

/**
 * Source code and plain text: one lazy row per line, so a 200,000-line file
 * opens as fast as a short one and only the visible lines are ever coloured.
 */
@Composable
fun CodeBody(
    lines: List<String>,
    langId: String,
    listState: LazyListState,
    prose: Boolean,
    modifier: Modifier = Modifier,
    query: String = "",
) {
    val palette = LocalPalette.current
    val settings: Settings = LocalSettings.current
    val lang = remember(langId) { Highlighter.lang(langId) }
    val states = remember(lines, lang) { Highlighter.states(lines, lang) }

    val fontSize = settings.fontSize.sp
    val lineHeight = (settings.fontSize * settings.lineHeight / 100f).sp
    val family = if (prose) familyFor(settings.readingFont) else Mono
    val showNumbers = settings.lineNumbers && !prose
    val wrap = prose || settings.wrapCode
    val digits = remember(lines.size) { maxOf(2, lines.size.toString().length) }

    val content: @Composable (Modifier) -> Unit = { innerModifier ->
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = innerModifier,
            ) {
                itemsIndexed(lines) { index, line ->
                    val marks = remember(line, query) { Ink.matches(line, query) }
                    val text = remember(line, index, query, palette) {
                        if (line.isEmpty()) {
                            AnnotatedString(" ")
                        } else if (line.length > 2000) {
                            AnnotatedString(line)
                        } else {
                            Ink.codeLine(
                                text = line,
                                toks = Highlighter.scan(line, lang, states.getOrElse(index) { 0 }).first,
                                ink = palette.code,
                                marks = marks,
                                markColor = palette.mark,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        if (showNumbers) {
                            Text(
                                text = (index + 1).toString().padStart(digits),
                                color = palette.gutter,
                                fontFamily = Mono,
                                fontSize = fontSize * 0.82f,
                                lineHeight = lineHeight,
                                softWrap = false,
                                modifier = Modifier.padding(start = 10.dp, end = 12.dp),
                            )
                        }
                        Text(
                            text = text,
                            color = palette.ink,
                            fontFamily = family,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            softWrap = wrap,
                            modifier = Modifier
                                .then(if (wrap) Modifier.weight(1f) else Modifier)
                                .padding(end = settings.margin.dp),
                        )
                    }
                }
                item { Box(Modifier.padding(bottom = 48.dp)) }
            }
        }
    }

    if (wrap) {
        Box(modifier.fillMaxSize().background(palette.page).padding(start = if (showNumbers) 0.dp else settings.margin.dp)) {
            content(Modifier.fillMaxSize())
        }
    } else {
        val density = LocalDensity.current
        val longest = remember(lines) { lines.maxOfOrNull { it.length } ?: 0 }
        val gutter = if (showNumbers) digits + 2 else 0
        BoxWithConstraints(modifier.fillMaxSize().background(palette.page)) {
            val needed: Dp = with(density) {
                ((longest + gutter) * fontSize.toPx() * 0.62f).toDp()
            }.coerceAtMost(24_000.dp)
            val wide = if (needed > maxWidth) needed else maxWidth
            Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                content(Modifier.width(wide).fillMaxHeight())
            }
        }
    }
}
