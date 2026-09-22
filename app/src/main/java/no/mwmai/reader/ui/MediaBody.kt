package no.mwmai.reader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.mwmai.reader.ui.theme.LocalPalette
import java.io.File

/**
 * Android has drawn PDFs since 2014 and the engine is in the system, so the app
 * ships no PDF library at all. One renderer is open at a time and page
 * rendering is serialised, because PdfRenderer is not thread-safe.
 */
class PdfPages(file: File) {

    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    var count: Int = 0
        private set
    var ratio: Float = 1.4f // height / width of the first page
        private set

    init {
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(descriptor)
            fd = descriptor
            renderer = r
            count = r.pageCount
            if (count > 0) {
                r.openPage(0).use { page ->
                    if (page.width > 0) ratio = page.height.toFloat() / page.width
                }
            }
        } catch (e: Exception) {
            Log.w("MwmReader", "cannot open pdf", e)
        }
    }

    @Synchronized
    fun render(index: Int, targetWidth: Int): Bitmap? {
        val r = renderer ?: return null
        if (index < 0 || index >= count) return null
        return try {
            r.openPage(index).use { page ->
                val w = targetWidth.coerceIn(160, 2400)
                val h = (w.toFloat() * page.height / maxOf(1, page.width)).toInt().coerceIn(1, 6000)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            Log.w("MwmReader", "page $index failed to render", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w("MwmReader", "page $index too large to render", e)
            null
        }
    }

    @Synchronized
    fun close() {
        runCatching { renderer?.close() }
        runCatching { fd?.close() }
        renderer = null
        fd = null
    }
}

private val invert = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

@Composable
fun PdfBody(
    file: File,
    pageCount: Int,
    listState: LazyListState,
    startPage: Int,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    val pages = remember(file.path) { PdfPages(file) }
    var zoom by remember(file.path) { mutableFloatStateOf(1f) }

    DisposableEffect(pages) { onDispose { pages.close() } }

    LaunchedEffect(pages, startPage) {
        if (startPage in 1 until maxOf(1, pageCount)) listState.scrollToItem(startPage)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { onPage(it) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(if (palette.dark) palette.page else palette.chrome)
            .pointerInput(file.path) {
                detectTransformGestures { _, _, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                }
            },
    ) {
        val widthPx = with(density) { (maxWidth.toPx() * zoom).toInt() }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(pageCount, key = { it }) { index ->
                val bitmap by produceState<ImageBitmap?>(null, index, widthPx) {
                    value = withContext(Dispatchers.IO) { pages.render(index, widthPx)?.asImageBitmap() }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shown = bitmap
                    if (shown != null) {
                        Image(
                            bitmap = shown,
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            colorFilter = if (palette.dark) ColorFilter.colorMatrix(invert) else null,
                            modifier = Modifier.fillMaxWidth().background(palette.raised),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(maxWidth * pages.ratio)
                                .background(palette.raised),
                        )
                    }
                    Text(
                        "Page ${index + 1} / $pageCount",
                        color = palette.inkDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ImageBody(file: File, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    var zoom by remember(file.path) { mutableFloatStateOf(1f) }
    var offsetX by remember(file.path) { mutableFloatStateOf(0f) }
    var offsetY by remember(file.path) { mutableFloatStateOf(0f) }

    val bitmap by produceState<ImageBitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) { decodeScaled(file, 2048)?.asImageBitmap() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.page)
            .pointerInput(file.path) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                    if (zoom > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown == null) {
            Text("Loading…", color = palette.inkDim, fontSize = 14.sp)
        } else {
            Image(
                bitmap = shown,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .graphicsLayer(
                        scaleX = zoom,
                        scaleY = zoom,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        }
    }
}

private fun decodeScaled(file: File, maxEdge: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxEdge) sample *= 2
        BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    } catch (e: Exception) {
        Log.w("MwmReader", "cannot decode image", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w("MwmReader", "image too large", e)
        null
    }
}
