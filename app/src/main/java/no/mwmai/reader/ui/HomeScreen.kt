package no.mwmai.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import no.mwmai.reader.model.Recent
import no.mwmai.reader.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalPalette.current

    Scaffold(
        containerColor = palette.page,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MWM Reader", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = palette.ink)
                        Text(
                            "No ads, no account, no network",
                            fontSize = 12.sp,
                            color = palette.inkDim,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, "Settings", tint = palette.inkDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.page),
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BigAction(
                        title = "Open a file",
                        subtitle = "Anything on the phone",
                        icon = { Icon(Icons.Filled.FileOpen, null, tint = palette.accent, modifier = Modifier.size(24.dp)) },
                        modifier = Modifier.weight(1f),
                        onClick = onPickFile,
                    )
                    BigAction(
                        title = "Add a folder",
                        subtitle = "Browse it in here",
                        icon = { Icon(Icons.Filled.CreateNewFolder, null, tint = palette.accent, modifier = Modifier.size(24.dp)) },
                        modifier = Modifier.weight(1f),
                        onClick = onPickFolder,
                    )
                }
            }

            if (vm.shelves.isNotEmpty()) {
                item { SectionLabel("Folders") }
                items(vm.shelves, key = { it.treeUri }) { shelf ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openShelf(shelf) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = palette.accent, modifier = Modifier.size(22.dp))
                        Text(
                            shelf.name,
                            color = palette.ink,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 14.dp),
                        )
                        IconButton(onClick = { vm.removeShelf(shelf) }) {
                            Icon(Icons.Filled.Close, "Remove folder", tint = palette.inkDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Recent", Modifier.weight(1f))
                    if (vm.recents.isNotEmpty()) {
                        Text(
                            "Clear",
                            color = palette.inkDim,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { vm.clearRecents() }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            if (vm.recents.isEmpty()) {
                item {
                    Text(
                        "Nothing opened yet. Pick a file and it stays on this list, " +
                            "with the place you stopped reading.",
                        color = palette.inkDim,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(vm.recents, key = { it.doc.uri }) { recent ->
                    RecentRow(recent, onOpen = { vm.reopen(recent) }, onForget = { vm.forget(recent) })
                }
            }

            item { Box(Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun BigAction(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Surface(
        modifier = modifier,
        color = palette.raised,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.rule),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            icon()
            Text(
                title,
                color = palette.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(subtitle, color = palette.inkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun RecentRow(recent: Recent, onOpen: () -> Unit, onForget: () -> Unit) {
    val palette = LocalPalette.current
    val kind = Kinds.kindOf(recent.doc.name, recent.doc.mime)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconFor(kind, recent.doc.name), null, tint = palette.inkDim, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                recent.doc.name,
                color = palette.ink,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val bits = listOfNotNull(
                labelFor(kind, recent.doc.name).ifBlank { null },
                Files.humanSize(recent.doc.size).ifBlank { null },
                agoOf(recent.openedAt).ifBlank { null },
            )
            Text(
                bits.joinToString("  ·  "),
                color = palette.inkDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onForget, modifier = Modifier.width(40.dp)) {
            Icon(Icons.Filled.Close, "Remove from recents", tint = palette.inkDim, modifier = Modifier.size(16.dp))
        }
    }
}
