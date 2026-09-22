package no.mwmai.reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import no.mwmai.reader.model.DocRef
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Everything that touches a Uri. Two kinds arrive: `content://` from the system
 * picker or another app, and `file://` from a file manager that still sends
 * paths. Both are handled here so nothing above this layer has to care.
 */
object Files {

    private const val TAG = "MwmReader"

    data class Entry(
        val uri: Uri,
        val name: String,
        val mime: String,
        val size: Long,
        val isDir: Boolean,
        val modified: Long,
        val documentId: String = "",
    )

    fun metadata(context: Context, uri: Uri): DocRef {
        if (uri.scheme == "file") {
            val f = File(uri.path.orEmpty())
            return DocRef(uri.toString(), f.name.ifBlank { "file" }, guessMime(f.name), f.length())
        }
        var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        var size = -1L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        }.onFailure { Log.w(TAG, "metadata query failed for $uri", it) }
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        return DocRef(uri.toString(), name.ifBlank { "document" }, mime, size)
    }

    fun open(context: Context, uri: Uri): InputStream? = runCatching {
        if (uri.scheme == "file") File(uri.path.orEmpty()).inputStream()
        else context.contentResolver.openInputStream(uri)
    }.onFailure { Log.w(TAG, "cannot open $uri", it) }.getOrNull()

    fun readAll(context: Context, uri: Uri, limitBytes: Int): Pair<ByteArray, Boolean>? {
        val stream = open(context, uri) ?: return null
        stream.use { input ->
            val buf = java.io.ByteArrayOutputStream(64 * 1024)
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                val room = limitBytes - total
                if (read > room) {
                    buf.write(chunk, 0, room)
                    return buf.toByteArray() to true
                }
                buf.write(chunk, 0, read)
                total += read
            }
            return buf.toByteArray() to false
        }
    }

    /**
     * PdfRenderer and BitmapFactory both want a real seekable file, and a
     * content provider is not obliged to give one, so the bytes land in the
     * cache first. The cache is cleared on every launch.
     */
    fun cacheCopy(context: Context, uri: Uri, name: String): File? {
        if (uri.scheme == "file") {
            val direct = File(uri.path.orEmpty())
            if (direct.canRead()) return direct
        }
        val dir = File(context.cacheDir, "open").apply { mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(64).ifBlank { "doc" }
        val out = File(dir, "${uri.toString().hashCode().toUInt().toString(16)}_$safe")
        if (out.isFile && out.length() > 0) return out
        val stream = open(context, uri) ?: return null
        return runCatching {
            stream.use { input -> FileOutputStream(out).use { input.copyTo(it) } }
            out
        }.onFailure { Log.w(TAG, "cache copy failed for $uri", it) }.getOrNull()
    }

    fun clearCache(context: Context) {
        runCatching { File(context.cacheDir, "open").deleteRecursively() }
    }

    /** Ask the provider to keep letting us read this after a reboot. */
    fun persist(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        true
    }.getOrElse {
        Log.i(TAG, "no persistable permission for $uri: ${it.message}")
        false
    }

    fun canStillRead(context: Context, uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).canRead()
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrElse { false }

    // ------------------------------------------------------------- browsing

    fun treeName(context: Context, treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return treeUri.lastPathSegment.orEmpty()
        val child = runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, docId) }.getOrNull()
            ?: return docId.substringAfterLast('/')
        return metadata(context, child).name.ifBlank { docId.substringAfterLast(':').substringAfterLast('/') }
    }

    /** Children of a folder, folders first and then names, case-insensitively. */
    fun children(context: Context, treeUri: Uri, documentId: String?): List<Entry> {
        if (treeUri.scheme == "file") return fileChildren(File(treeUri.path.orEmpty()))
        val parentId = documentId
            ?: runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        }.getOrNull() ?: return emptyList()

        val out = ArrayList<Entry>()
        runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: id.substringAfterLast('/')
                    val mime = c.getString(2).orEmpty()
                    val size = if (c.isNull(3)) -1L else c.getLong(3)
                    val modified = if (c.isNull(4)) 0L else c.getLong(4)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    out.add(
                        Entry(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            name = name,
                            mime = mime,
                            size = size,
                            isDir = isDir,
                            modified = modified,
                            documentId = id,
                        ),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "listing failed for $childrenUri", it) }
        return sortEntries(out)
    }

    private fun fileChildren(dir: File): List<Entry> {
        val kids = dir.listFiles().orEmpty()
        return sortEntries(
            kids.map {
                Entry(
                    uri = Uri.fromFile(it),
                    name = it.name,
                    mime = if (it.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else guessMime(it.name),
                    size = if (it.isDirectory) -1L else it.length(),
                    isDir = it.isDirectory,
                    modified = it.lastModified(),
                    documentId = it.absolutePath,
                )
            },
        )
    }

    private fun sortEntries(list: List<Entry>): List<Entry> =
        list.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "md", "markdown" -> "text/markdown"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "text/plain"
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1048576.0)
        else -> String.format("%.1f GB", bytes / 1073741824.0)
    }
}
