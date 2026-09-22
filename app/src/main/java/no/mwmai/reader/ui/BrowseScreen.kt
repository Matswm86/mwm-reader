package no.mwmai.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.reader.MainViewModel
import no.mwmai.reader.data.Files
import no.mwmai.reader.format.Kinds
import no.mwmai.reader.model.DocRef
import no.mwmai.reader.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(vm: MainViewModel) {
    val palette = LocalPalette.current
    val crumbs = vm.crumbs

    Scaffold(
        containerColor = palette.page,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.ink)
                    }
                },
                title = {
                    Column {
                        Text(
                            crumbs.lastOrNull()?.name ?: "Folder",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (crumbs.size > 1) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                crumbs.forEachIndexed { index, crumb ->
                                    if (index > 0) {
                                        Icon(
                                            Icons.Filled.ChevronRight, null,
                                            tint = palette.inkDim,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                    Text(
                                        crumb.name,
                                        color = if (index == crumbs.lastIndex) palette.accent else palette.inkDim,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        modifier = Modifier.clickable { vm.popTo(index) },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.page),
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                vm.listing -> CircularProgressIndicator(
                    color = palette.accent,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp).size(28.dp),
                )
                vm.entries.isEmpty() -> Text(
                    "This folder is empty.",
                    color = palette.inkDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.entries, key = { it.uri.toString() }) { entry ->
                        EntryRow(entry) {
                            if (entry.isDir) {
                                vm.enterFolder(entry)
                            } else {
                                vm.open(
                                    DocRef(
                                        uri = entry.uri.toString(),
                                        name = entry.name,
                                        mime = entry.mime,
                                        size = entry.size,
                                    ),
                                )
                            }
                        }
                    }
                    item { Box(Modifier.size(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Files.Entry, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (entry.isDir) {
            Icon(Icons.Filled.Folder, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        } else {
            val kind = Kinds.kindOf(entry.name, entry.mime)
            Icon(iconFor(kind, entry.name), null, tint = palette.inkDim, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                color = palette.ink,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.isDir) {
                val kind = Kinds.kindOf(entry.name, entry.mime)
                val bits = listOfNotNull(
                    labelFor(kind, entry.name).ifBlank { null },
                    Files.humanSize(entry.size).ifBlank { null },
                )
                if (bits.isNotEmpty()) {
                    Text(
                        bits.joinToString("  ·  "),
                        color = palette.inkDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (entry.isDir) {
            Icon(Icons.Filled.ChevronRight, null, tint = palette.inkDim, modifier = Modifier.size(18.dp))
        }
    }
}
