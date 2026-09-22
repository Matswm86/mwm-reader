package no.mwmai.reader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import no.mwmai.reader.MainViewModel
import no.mwmai.reader.Screen
import no.mwmai.reader.ui.theme.LocalPalette

@Composable
fun ReaderApp(vm: MainViewModel) {
    val palette = LocalPalette.current
    var settingsOpen by remember { mutableStateOf(false) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.openUri(it, alsoPersistPermission = true) } }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { vm.addShelf(it) } }

    BackHandler(enabled = vm.screen != Screen.Home || settingsOpen) {
        if (settingsOpen) settingsOpen = false else vm.back()
    }

    Box(Modifier.fillMaxSize().background(palette.page)) {
        when (vm.screen) {
            Screen.Home -> HomeScreen(
                vm = vm,
                onPickFile = { pickFile.launch(arrayOf("*/*")) },
                onPickFolder = { pickFolder.launch(null) },
                onSettings = { settingsOpen = true },
            )
            Screen.Browse -> BrowseScreen(vm)
            Screen.Reader -> ReaderScreen(vm, onSettings = { settingsOpen = true })
        }
        if (settingsOpen) {
            SettingsSheet(vm) { settingsOpen = false }
        }
    }
}
