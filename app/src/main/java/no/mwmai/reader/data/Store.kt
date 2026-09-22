package no.mwmai.reader.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import no.mwmai.reader.model.AppState
import java.io.File

/**
 * One JSON file holds the whole app: settings, the recent list, and the folders
 * the user put on the shelf. There is no database because there is nothing to
 * query, and a file that will not parse is renamed rather than overwritten, so
 * a bad write never silently eats someone's reading positions.
 */
class Store(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val broken = File(context.filesDir, "$FILE_NAME.broken")

    fun load(): AppState {
        if (!file.isFile) return AppState()
        return try {
            json.decodeFromString(AppState.serializer(), file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "unreadable state file, keeping it at ${broken.name}", e)
            runCatching { file.copyTo(broken, overwrite = true) }
            AppState()
        }
    }

    fun save(state: AppState) {
        try {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(AppState.serializer(), state))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not save state", e)
        }
    }

    private companion object {
        const val TAG = "MwmReader"
        const val FILE_NAME = "reader_state.json"
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}
