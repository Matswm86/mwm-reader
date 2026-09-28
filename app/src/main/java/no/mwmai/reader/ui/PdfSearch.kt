package no.mwmai.reader.ui

import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** One match in a PDF. [rects] are fractions of the page's width and height,
 *  so they line up with the page bitmap at any zoom. */
data class PdfHit(val page: Int, val rects: List<RectF>)

/**
 * Text search inside a PDF, done by the same system PDF engine that draws the
 * pages. The engine gained a text layer in Android 15 (API 35), and Android
 * 12 to 14 get the same code as PdfRendererPreV through the S extension 13
 * system update. Older phones have no text layer to search, so [supported]
 * is false there and the reader hides the search button on PDFs.
 */
object PdfSearch {

    val supported: Boolean
        get() = Build.VERSION.SDK_INT >= 35 || preV()

    private fun preV(): Boolean =
        Build.VERSION.SDK_INT >= 31 &&
            runCatching { SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13 }.getOrDefault(false)

    /** At most this many hits are collected, so a one-letter query on a
     *  thousand-page book cannot fill memory. */
    const val MAX_HITS = 2000

    /**
     * Searches every page in order and reports the hits found so far after
     * each page that had any, so the counter climbs while a long book is
     * still being read. Cancelling the calling coroutine stops the search
     * between pages. Opens its own renderer, so it never waits on the one
     * drawing the pages.
     */
    suspend fun search(file: File, query: String, onProgress: (hits: List<PdfHit>, pagesDone: Int) -> Unit) {
        if (query.isBlank()) return
        val fd = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.w("MwmReader", "pdf search: cannot open file", e)
            return
        }
        fd.use {
            val found = ArrayList<PdfHit>()
            if (Build.VERSION.SDK_INT >= 35) {
                PdfRenderer(fd).use { r ->
                    for (i in 0 until r.pageCount) {
                        currentCoroutineContext().ensureActive()
                        val page = r.openPage(i)
                        val hits = try {
                            toHits(i, page.width, page.height, page.searchText(query).map { it.bounds })
                        } finally {
                            page.close()
                        }
                        if (collect(found, hits, i, onProgress)) return
                    }
                    onProgress(found.toList(), r.pageCount)
                }
            } else if (preV()) {
                PdfRendererPreV(fd).use { r ->
                    for (i in 0 until r.pageCount) {
                        currentCoroutineContext().ensureActive()
                        val page = r.openPage(i)
                        val hits = try {
                            toHits(i, page.width, page.height, page.searchText(query).map { it.bounds })
                        } finally {
                            page.close()
                        }
                        if (collect(found, hits, i, onProgress)) return
                    }
                    onProgress(found.toList(), r.pageCount)
                }
            }
        }
    }

    /** Adds [hits] to [found] and reports progress. True once the cap is hit. */
    private fun collect(
        found: MutableList<PdfHit>,
        hits: List<PdfHit>,
        pageIndex: Int,
        onProgress: (List<PdfHit>, Int) -> Unit,
    ): Boolean {
        if (hits.isEmpty()) return false
        found += hits.take(MAX_HITS - found.size)
        onProgress(found.toList(), pageIndex + 1)
        return found.size >= MAX_HITS
    }

    /** Page bounds come back in PDF points, the same units as the page's
     *  width and height, so dividing by those gives fractions of the page. */
    private fun toHits(pageIndex: Int, width: Int, height: Int, matches: List<List<RectF>>): List<PdfHit> {
        if (width <= 0 || height <= 0) return emptyList()
        val w = width.toFloat()
        val h = height.toFloat()
        return matches.map { rects ->
            PdfHit(pageIndex, rects.map { RectF(it.left / w, it.top / h, it.right / w, it.bottom / h) })
        }
    }
}
