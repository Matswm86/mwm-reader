package no.mwmai.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.mwmai.reader.data.Files
import no.mwmai.reader.data.Loader
import no.mwmai.reader.data.Store
import no.mwmai.reader.model.AppState
import no.mwmai.reader.model.DocRef
import no.mwmai.reader.model.LoadedDoc
import no.mwmai.reader.model.Recent
import no.mwmai.reader.model.Settings
import no.mwmai.reader.model.Shelf

/** Where the app is. Three places, so no navigation library is needed. */
sealed interface Screen {
    data object Home : Screen
    data object Browse : Screen
    data object Reader : Screen
}

data class Crumb(val treeUri: String, val documentId: String?, val name: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    var settings by mutableStateOf(Settings())
        private set
    var recents by mutableStateOf<List<Recent>>(emptyList())
        private set
    var shelves by mutableStateOf<List<Shelf>>(emptyList())
        private set

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    // ------------------------------------------------------------- the file
    var current by mutableStateOf<DocRef?>(null)
        private set
    var doc by mutableStateOf<LoadedDoc?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    /** Markdown and HTML can be shown as the source they came from. */
    var showSource by mutableStateOf(false)

    var query by mutableStateOf("")
    var searchOpen by mutableStateOf(false)
    var hitIndex by mutableStateOf(0)

    /** Set when the reader should jump somewhere: a saved position, a search
     *  hit, or an outline entry. Cleared by the screen once it has scrolled. */
    var jumpTo by mutableStateOf<Int?>(null)
    var startPage by mutableStateOf(0)
        private set

    // -------------------------------------------------------------- folders
    var crumbs by mutableStateOf<List<Crumb>>(emptyList())
        private set
    var entries by mutableStateOf<List<Files.Entry>>(emptyList())
        private set
    var listing by mutableStateOf(false)
        private set

    init {
        val state = store.load()
        settings = state.settings
        recents = state.recents
        shelves = state.shelves
        Files.clearCache(app)
    }

    private fun persist() {
        store.save(AppState(settings, recents.take(40), shelves))
    }

    fun update(change: (Settings) -> Settings) {
        settings = change(settings)
        persist()
    }

    // ---------------------------------------------------------------- files

    fun open(ref: DocRef, alsoPersistPermission: Boolean = false) {
        val app = getApplication<Application>()
        if (alsoPersistPermission && ref.uri.startsWith("content:")) {
            Files.persist(app, Uri.parse(ref.uri))
        }
        current = ref
        doc = null
        loading = true
        showSource = false
        query = ""
        searchOpen = false
        hitIndex = 0
        screen = Screen.Reader

        val saved = recents.firstOrNull { it.doc.uri == ref.uri }
        startPage = saved?.page ?: 0
        jumpTo = saved?.lineIndex?.takeIf { it > 0 }

        viewModelScope.launch {
            val result = Loader.load(app, ref)
            if (current?.uri == ref.uri) {
                doc = result
                loading = false
            }
            remember(ref, saved)
        }
    }

    fun openUri(uri: Uri, alsoPersistPermission: Boolean = false) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ref = withContext(Dispatchers.IO) { Files.metadata(app, uri) }
            open(ref, alsoPersistPermission)
        }
    }

    fun reopen(recent: Recent) = open(recent.doc)

    private fun remember(ref: DocRef, previous: Recent?) {
        val entry = (previous ?: Recent(ref)).copy(
            doc = ref,
            openedAt = System.currentTimeMillis(),
        )
        recents = listOf(entry) + recents.filterNot { it.doc.uri == ref.uri }
        persist()
    }

    fun rememberPosition(line: Int, page: Int) {
        val ref = current ?: return
        val existing = recents.firstOrNull { it.doc.uri == ref.uri } ?: return
        if (existing.lineIndex == line && existing.page == page) return
        recents = recents.map {
            if (it.doc.uri == ref.uri) it.copy(lineIndex = line, page = page) else it
        }
        persist()
    }

    fun forget(recent: Recent) {
        recents = recents.filterNot { it.doc.uri == recent.doc.uri }
        persist()
    }

    fun clearRecents() {
        recents = emptyList()
        persist()
    }

    // -------------------------------------------------------------- folders

    fun addShelf(treeUri: Uri) {
        val app = getApplication<Application>()
        Files.persist(app, treeUri)
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { Files.treeName(app, treeUri) }
            val shelf = Shelf(treeUri.toString(), name.ifBlank { "Folder" })
            shelves = listOf(shelf) + shelves.filterNot { it.treeUri == shelf.treeUri }
            persist()
            openShelf(shelf)
        }
    }

    fun removeShelf(shelf: Shelf) {
        shelves = shelves.filterNot { it.treeUri == shelf.treeUri }
        persist()
    }

    fun openShelf(shelf: Shelf) {
        crumbs = listOf(Crumb(shelf.treeUri, null, shelf.name))
        screen = Screen.Browse
        refreshListing()
    }

    fun enterFolder(entry: Files.Entry) {
        val top = crumbs.lastOrNull() ?: return
        crumbs = crumbs + Crumb(top.treeUri, entry.documentId, entry.name)
        refreshListing()
    }

    fun popTo(index: Int) {
        if (index < 0 || index >= crumbs.size) return
        crumbs = crumbs.take(index + 1)
        refreshListing()
    }

    private fun refreshListing() {
        val top = crumbs.lastOrNull() ?: return
        val app = getApplication<Application>()
        listing = true
        entries = emptyList()
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                Files.children(app, Uri.parse(top.treeUri), top.documentId)
            }
            entries = list
            listing = false
        }
    }

    // --------------------------------------------------------------- screen

    fun goHome() {
        screen = Screen.Home
        current = null
        doc = null
        loading = false
    }

    /** True when it handled the back press itself. */
    fun back(): Boolean = when (screen) {
        Screen.Reader -> {
            if (searchOpen) { searchOpen = false; query = "" } else goHome()
            true
        }
        Screen.Browse -> {
            if (crumbs.size > 1) popTo(crumbs.size - 2) else screen = Screen.Home
            true
        }
        Screen.Home -> false
    }
}
