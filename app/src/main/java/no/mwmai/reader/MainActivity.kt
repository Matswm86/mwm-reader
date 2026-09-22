package no.mwmai.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import no.mwmai.reader.ui.ReaderApp
import no.mwmai.reader.ui.theme.ReaderTheme
import no.mwmai.reader.ui.theme.paletteFor

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var pendingFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val settings = vm.settings
            val palette = paletteFor(settings.themeChoice, isSystemInDarkTheme())
            val view = LocalView.current

            LaunchedEffect(palette.dark, view) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !palette.dark
                controller.isAppearanceLightNavigationBars = !palette.dark
            }

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            ReaderTheme(palette, settings) {
                ReaderApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** A file handed over by a file manager, a browser download, or a share. */
    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.streamExtra()
            else -> null
        } ?: return

        val path = uri.path.orEmpty()
        val sharedPath = path.startsWith("/storage") || path.startsWith("/sdcard")
        if (uri.scheme == "file" && sharedPath && needsLegacyStoragePermission()) {
            pendingFileUri = uri
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_READ)
            return
        }
        vm.openUri(uri)
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun needsLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_READ) return
        val uri = pendingFileUri ?: return
        pendingFileUri = null
        // Try either way: a denied permission still gives a clear message on screen.
        vm.openUri(uri)
    }

    private companion object {
        const val REQUEST_READ = 41
    }
}
