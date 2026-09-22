package no.mwmai.reader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.reader.format.Kinds
import no.mwmai.reader.model.DocKind
import no.mwmai.reader.ui.theme.LocalPalette

fun iconFor(kind: DocKind, name: String): ImageVector = when (kind) {
    DocKind.PDF -> Icons.Filled.PictureAsPdf
    DocKind.EPUB -> Icons.Filled.MenuBook
    DocKind.IMAGE -> Icons.Filled.Image
    DocKind.TABLE -> Icons.Filled.TableChart
    DocKind.CODE -> Icons.Filled.Code
    DocKind.MARKDOWN -> Icons.Filled.Article
    DocKind.HTML -> Icons.Filled.Article
    DocKind.TEXT, DocKind.RTF -> Icons.Filled.Description
    DocKind.OFFICE -> when (Kinds.extensionOf(name)) {
        "xlsx", "xlsm", "ods" -> Icons.Filled.TableChart
        "pptx", "pptm", "odp" -> Icons.Filled.Slideshow
        else -> Icons.Filled.Description
    }
    DocKind.UNSUPPORTED -> Icons.Filled.InsertDriveFile
}

fun labelFor(kind: DocKind, name: String): String {
    val ext = Kinds.extensionOf(name).uppercase()
    return when (kind) {
        DocKind.CODE -> if (ext.length <= 12) ext else "Code"
        DocKind.MARKDOWN -> "Markdown"
        DocKind.PDF -> "PDF"
        DocKind.EPUB -> "EPUB"
        DocKind.IMAGE -> ext.ifBlank { "Image" }
        DocKind.TABLE -> ext.ifBlank { "Table" }
        DocKind.HTML -> "HTML"
        DocKind.RTF -> "RTF"
        DocKind.OFFICE -> ext.ifBlank { "Document" }
        DocKind.TEXT -> if (ext.length in 1..5) ext else "Text"
        DocKind.UNSUPPORTED -> "File"
    }
}

fun agoOf(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0L) return ""
    val secs = (now - millis) / 1000
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60} min ago"
        secs < 86_400 -> "${secs / 3600} h ago"
        secs < 172_800 -> "yesterday"
        secs < 2_592_000 -> "${secs / 86_400} days ago"
        else -> "${secs / 2_592_000} months ago"
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Text(
        text = text.uppercase(),
        color = palette.inkDim,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun ColumnBlock(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(padding)) { content() }
}
