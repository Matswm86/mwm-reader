package no.mwmai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.reader.format.Highlighter
import no.mwmai.reader.model.Block
import no.mwmai.reader.model.text
import no.mwmai.reader.ui.theme.LocalPalette
import no.mwmai.reader.ui.theme.LocalSettings
import no.mwmai.reader.ui.theme.Mono
import no.mwmai.reader.ui.theme.familyFor

/** Markdown, HTML, EPUB chapters and extracted office text all land here. */
@Composable
fun RenderedBody(
    blocks: List<Block>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    query: String = "",
) {
    val palette = LocalPalette.current
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().background(palette.page),
        ) {
            itemsIndexed(blocks) { _, block -> BlockView(block, query) }
            item { Box(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun BlockView(block: Block, query: String) {
    val palette = LocalPalette.current
    val settings = LocalSettings.current
    val side = settings.margin.dp + 4.dp
    val base = settings.fontSize.sp
    val family = familyFor(settings.readingFont)
    val lineHeight = (settings.fontSize * settings.lineHeight / 100f).sp

    when (block) {
        is Block.Heading -> {
            val scale = when (block.level) {
                1 -> 1.7f
                2 -> 1.42f
                3 -> 1.2f
                else -> 1.05f
            }
            val marks = remember(block, query) { Ink.matches(block.inline.text(), query) }
            Text(
                text = Ink.inline(block.inline, palette, Mono, marks),
                color = palette.ink,
                fontFamily = family,
                fontSize = base * scale,
                fontWeight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold,
                lineHeight = base * scale * 1.3f,
                modifier = Modifier.padding(
                    start = side,
                    end = side,
                    top = if (block.level <= 2) 26.dp else 18.dp,
                    bottom = 6.dp,
                ),
            )
        }

        is Block.Para -> {
            val marks = remember(block, query) { Ink.matches(block.inline.text(), query) }
            Text(
                text = Ink.inline(block.inline, palette, Mono, marks),
                color = palette.ink,
                fontFamily = family,
                fontSize = base,
                lineHeight = lineHeight,
                modifier = Modifier.padding(start = side, end = side, top = 6.dp, bottom = 6.dp),
            )
        }

        is Block.Quote -> {
            val marks = remember(block, query) { Ink.matches(block.inline.text(), query) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = side, end = side, top = 8.dp, bottom = 8.dp),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(lineHeight.value.dp * 1.2f)
                        .background(palette.accent, RoundedCornerShape(2.dp)),
                )
                Text(
                    text = Ink.inline(block.inline, palette, Mono, marks),
                    color = palette.inkDim,
                    fontFamily = family,
                    fontStyle = FontStyle.Italic,
                    fontSize = base,
                    lineHeight = lineHeight,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        is Block.Bullet -> {
            val marks = remember(block, query) { Ink.matches(block.inline.text(), query) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = side + (block.indent.coerceIn(0, 4) * 18).dp,
                        end = side,
                        top = 3.dp,
                        bottom = 3.dp,
                    ),
            ) {
                Text(
                    text = when (block.marker) {
                        "todo" -> "☐"
                        "done" -> "☑"
                        else -> block.marker
                    },
                    color = if (block.marker == "done") palette.accent else palette.inkDim,
                    fontFamily = family,
                    fontSize = base,
                    lineHeight = lineHeight,
                    modifier = Modifier.width(26.dp),
                )
                Text(
                    text = Ink.inline(block.inline, palette, Mono, marks),
                    color = palette.ink,
                    fontFamily = family,
                    fontSize = base,
                    lineHeight = lineHeight,
                )
            }
        }

        is Block.CodeBlock -> {
            val lang = remember(block.langId) { Highlighter.lang(block.langId) }
            val states = remember(block.lines, lang) { Highlighter.states(block.lines, lang) }
            Box(Modifier.fillMaxWidth().padding(start = side, end = side, top = 10.dp, bottom = 10.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(palette.codePage, RoundedCornerShape(10.dp))
                        .border(1.dp, palette.rule, RoundedCornerShape(10.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    block.lines.forEachIndexed { index, line ->
                        Text(
                            text = if (line.isEmpty()) {
                                androidx.compose.ui.text.AnnotatedString(" ")
                            } else {
                                Ink.codeLine(
                                    text = line,
                                    toks = Highlighter.scan(line, lang, states.getOrElse(index) { 0 }).first,
                                    ink = palette.code,
                                    marks = Ink.matches(line, query),
                                    markColor = palette.mark,
                                )
                            },
                            color = palette.ink,
                            fontFamily = Mono,
                            fontSize = base * 0.88f,
                            lineHeight = base * 1.4f,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        is Block.TableBlock -> TableGrid(block.header, block.rows, side)

        is Block.Picture -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = side, end = side, top = 8.dp, bottom = 8.dp)
                    .background(palette.codePage, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    "❑  ${block.alt}",
                    color = palette.inkDim,
                    fontFamily = family,
                    fontSize = base * 0.9f,
                )
            }
        }

        Block.Rule -> HorizontalDivider(
            color = palette.rule,
            modifier = Modifier.padding(horizontal = side, vertical = 16.dp),
        )
    }
}

@Composable
fun TableGrid(
    header: List<String>,
    rows: List<List<String>>,
    side: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val settings = LocalSettings.current
    val base = (settings.fontSize * 0.88f).sp
    val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return

    val widths = remember(header, rows) {
        (0 until columns).map { col ->
            val longest = maxOf(
                header.getOrNull(col)?.length ?: 0,
                rows.take(200).maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0,
            )
            (longest.coerceIn(3, 40) * 9 + 24).dp
        }
    }

    Box(modifier.fillMaxWidth().padding(start = side, end = side, top = 10.dp, bottom = 10.dp)) {
        Column(
            Modifier
                .border(1.dp, palette.rule, RoundedCornerShape(10.dp))
                .horizontalScroll(rememberScrollState()),
        ) {
            Row(Modifier.background(palette.chrome)) {
                (0 until columns).forEach { col ->
                    Text(
                        header.getOrNull(col).orEmpty(),
                        color = palette.ink,
                        fontSize = base,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.width(widths[col]).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            rows.forEachIndexed { index, row ->
                HorizontalDivider(color = palette.rule)
                Row(Modifier.background(if (index % 2 == 1) palette.codePage else palette.page)) {
                    (0 until columns).forEach { col ->
                        Text(
                            row.getOrNull(col).orEmpty(),
                            color = palette.ink,
                            fontSize = base,
                            maxLines = 4,
                            modifier = Modifier.width(widths[col]).padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A standalone CSV or spreadsheet: the same grid, but it owns the screen. */
@Composable
fun TableBody(
    header: List<String>,
    rows: List<List<String>>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val settings = LocalSettings.current
    val base = (settings.fontSize * 0.88f).sp
    val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    val scroll = rememberScrollState()

    val widths = remember(header, rows) {
        (0 until columns).map { col ->
            val longest = maxOf(
                header.getOrNull(col)?.length ?: 0,
                rows.take(300).maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0,
            )
            (longest.coerceIn(3, 40) * 9 + 24).dp
        }
    }

    Column(modifier.fillMaxSize().background(palette.page)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(palette.chrome)
                .horizontalScroll(scroll),
        ) {
            Box(Modifier.width(44.dp))
            (0 until columns).forEach { col ->
                Text(
                    header.getOrNull(col).orEmpty(),
                    color = palette.ink,
                    fontSize = base,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.width(widths[col]).padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }
        HorizontalDivider(color = palette.rule)
        SelectionContainer {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(rows) { index, row ->
                    Row(
                        Modifier
                            .background(if (index % 2 == 1) palette.codePage else palette.page)
                            .horizontalScroll(scroll),
                    ) {
                        Text(
                            (index + 1).toString(),
                            color = palette.gutter,
                            fontSize = base * 0.85f,
                            modifier = Modifier.width(44.dp).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                        )
                        (0 until columns).forEach { col ->
                            Text(
                                row.getOrNull(col).orEmpty(),
                                color = palette.ink,
                                fontSize = base,
                                maxLines = 4,
                                modifier = Modifier.width(widths[col]).padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = palette.rule)
                }
                item { Box(Modifier.height(64.dp)) }
            }
        }
    }
}
