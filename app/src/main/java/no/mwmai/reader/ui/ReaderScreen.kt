package no.mwmai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import no.mwmai.reader.MainViewModel
import no.mwmai.reader.data.Loader
import no.mwmai.reader.format.Kinds
import no.mwmai.reader.model.Block
import no.mwmai.reader.model.DocKind
import no.mwmai.reader.model.LoadedDoc
import no.mwmai.reader.model.Outline
import no.mwmai.reader.model.text
import no.mwmai.reader.ui.theme.LocalPalette

private fun blockText(block: Block): String = when (block) {
    is Block.Heading -> block.inline.text()
    is Block.Para -> block.inline.text()
    is Block.Quote -> block.inline.text()
    is Block.Bullet -> block.inline.text()
    is Block.CodeBlock -> block.lines.joinToString("\n")
    is Block.TableBlock -> (block.header + block.rows.flatten()).joinToString(" ")
    is Block.Picture -> block.alt
    Block.Rule -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: MainViewModel, onSettings: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val ref = vm.current
    val doc = vm.doc
    val listState = remember(ref?.uri) { LazyListState() }
    var outlineOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchFocus = remember { FocusRequester() }

    val kind = remember(ref) { ref?.let { Kinds.kindOf(it.name, it.mime) } ?: DocKind.TEXT }
    val sourceMode = vm.showSource

    val outline: List<Outline> = remember(doc, sourceMode) {
        when (val d = doc) {
            is LoadedDoc.Rendered -> if (sourceMode) emptyList() else d.outline
            is LoadedDoc.Lines -> Loader.outlineFor(d.lines, d.langId)
            else -> emptyList()
        }
    }

    val hits: List<Int> = remember(doc, vm.query, sourceMode) {
        val q = vm.query
        if (q.isBlank()) {
            emptyList()
        } else {
            when (val d = doc) {
                is LoadedDoc.Lines -> d.lines.indices.filter { d.lines[it].contains(q, true) }
                is LoadedDoc.Rendered -> {
                    val raw = d.raw
                    if (sourceMode && raw != null) raw.indices.filter { raw[it].contains(q, true) }
                    else d.blocks.indices.filter { blockText(d.blocks[it]).contains(q, true) }
                }
                is LoadedDoc.Table -> d.rows.indices.filter { row -> d.rows[row].any { it.contains(q, true) } }
                else -> emptyList()
            }
        }
    }

    // Restore the saved reading position, or follow an outline / search jump.
    LaunchedEffect(doc, vm.jumpTo) {
        val target = vm.jumpTo ?: return@LaunchedEffect
        if (doc == null) return@LaunchedEffect
        listState.scrollToItem(target.coerceAtLeast(0))
        vm.jumpTo = null
    }

    LaunchedEffect(hits, vm.hitIndex) {
        if (hits.isEmpty()) return@LaunchedEffect
        val index = vm.hitIndex.coerceIn(0, hits.size - 1)
        listState.scrollToItem(hits[index])
    }

    LaunchedEffect(listState, ref) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { line ->
            if (kind != DocKind.PDF) vm.rememberPosition(line, 0)
        }
    }

    LaunchedEffect(vm.searchOpen) {
        if (vm.searchOpen) runCatching { searchFocus.requestFocus() }
    }

    Scaffold(
        containerColor = palette.page,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.goHome() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.ink)
                    }
                },
                title = {
                    Column {
                        Text(
                            ref?.name.orEmpty(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            subtitleFor(kind, ref?.name.orEmpty(), doc, sourceMode),
                            fontSize = 11.sp,
                            color = palette.inkDim,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    if (outline.isNotEmpty()) {
                        IconButton(onClick = { outlineOpen = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatListBulleted,
                                "Contents",
                                tint = palette.inkDim,
                            )
                        }
                    }
                    if ((doc as? LoadedDoc.Rendered)?.raw != null) {
                        IconButton(onClick = { vm.showSource = !vm.showSource }) {
                            Icon(
                                Icons.Filled.Code,
                                "Show the source",
                                tint = if (sourceMode) palette.accent else palette.inkDim,
                            )
                        }
                    }
                    if (kind != DocKind.PDF && kind != DocKind.IMAGE) {
                        IconButton(onClick = { vm.searchOpen = !vm.searchOpen; if (!vm.searchOpen) vm.query = "" }) {
                            Icon(Icons.Filled.Search, "Find in file", tint = if (vm.searchOpen) palette.accent else palette.inkDim)
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Tune, "Reading settings", tint = palette.inkDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.page),
            )
        },
        bottomBar = {
            if (vm.searchOpen) {
                SearchBar(
                    query = vm.query,
                    onQuery = { vm.query = it; vm.hitIndex = 0 },
                    hits = hits.size,
                    index = if (hits.isEmpty()) 0 else vm.hitIndex.coerceIn(0, hits.size - 1) + 1,
                    focusRequester = searchFocus,
                    onPrev = { if (hits.isNotEmpty()) vm.hitIndex = (vm.hitIndex - 1 + hits.size) % hits.size },
                    onNext = { if (hits.isNotEmpty()) vm.hitIndex = (vm.hitIndex + 1) % hits.size },
                    onClose = { vm.searchOpen = false; vm.query = "" },
                )
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                vm.loading || doc == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(28.dp))
                }

                doc is LoadedDoc.Failed -> Failed(doc.message, doc.detail)

                doc is LoadedDoc.Lines -> CodeBody(
                    lines = doc.lines,
                    langId = doc.langId,
                    listState = listState,
                    prose = doc.langId == "text",
                    query = vm.query,
                )

                doc is LoadedDoc.Rendered -> {
                    val raw = doc.raw
                    if (sourceMode && raw != null) {
                        CodeBody(
                            lines = raw,
                            langId = doc.rawLangId,
                            listState = listState,
                            prose = false,
                            query = vm.query,
                        )
                    } else {
                        RenderedBody(doc.blocks, listState, query = vm.query)
                    }
                }

                doc is LoadedDoc.Table -> TableBody(doc.header, doc.rows, listState)

                doc is LoadedDoc.Pdf -> PdfBody(
                    file = doc.file,
                    pageCount = doc.pageCount,
                    listState = listState,
                    startPage = vm.startPage,
                    onPage = { vm.rememberPosition(0, it) },
                )

                doc is LoadedDoc.Picture -> ImageBody(doc.file)
            }
        }
    }

    if (outlineOpen) {
        ModalBottomSheet(
            onDismissRequest = { outlineOpen = false },
            sheetState = sheetState,
            containerColor = palette.chrome,
        ) {
            Text(
                "Contents",
                color = palette.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
                items(outline.size) { i ->
                    val entry = outline[i]
                    Text(
                        entry.title,
                        color = if (entry.level <= 1) palette.ink else palette.inkDim,
                        fontSize = if (entry.level <= 1) 15.sp else 14.sp,
                        fontWeight = if (entry.level <= 1) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                outlineOpen = false
                                scope.launch {
                                    sheetState.hide()
                                    listState.scrollToItem(entry.blockIndex)
                                }
                            }
                            .padding(
                                start = 20.dp + ((entry.level - 1).coerceIn(0, 4) * 14).dp,
                                end = 20.dp,
                                top = 10.dp,
                                bottom = 10.dp,
                            ),
                    )
                }
            }
        }
    }
}

private fun subtitleFor(kind: DocKind, name: String, doc: LoadedDoc?, sourceMode: Boolean): String {
    val label = labelFor(kind, name)
    val extra = when (doc) {
        is LoadedDoc.Lines -> "${doc.lines.size} lines" + if (doc.truncated) " (cut off)" else ""
        is LoadedDoc.Rendered -> if (sourceMode) "source" else "${doc.blocks.size} blocks"
        is LoadedDoc.Table -> "${doc.rows.size} rows × ${doc.header.size} columns"
        is LoadedDoc.Pdf -> "${doc.pageCount} pages"
        else -> ""
    }
    return listOf(label, extra).filter { it.isNotBlank() }.joinToString("  ·  ")
}

@Composable
private fun Failed(message: String, detail: String) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = palette.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (detail.isNotBlank()) {
            Text(
                detail,
                color = palette.inkDim,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    hits: Int,
    index: Int,
    focusRequester: FocusRequester,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(palette.chrome).navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = palette.rule)
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Find in this file", color = palette.inkDim, fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = palette.ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            Text(
                if (query.isBlank()) "" else if (hits == 0) "none" else "$index/$hits",
                color = palette.inkDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.KeyboardArrowUp, "Previous hit", tint = palette.inkDim)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowDown, "Next hit", tint = palette.inkDim)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, "Close search", tint = palette.inkDim)
            }
        }
    }
}
