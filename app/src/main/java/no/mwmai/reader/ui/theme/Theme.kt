package no.mwmai.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import no.mwmai.reader.R
import no.mwmai.reader.model.ReadingFont
import no.mwmai.reader.model.Settings
import no.mwmai.reader.model.ThemeChoice

/** Syntax colours. Two sets: one for light paper, one for a dark page. */
data class CodeInk(
    val keyword: Color,
    val type: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val meta: Color,
    val func: Color,
    val punct: Color,
    val added: Color,
    val removed: Color,
)

data class Palette(
    val dark: Boolean,
    val page: Color,
    val chrome: Color,
    val raised: Color,
    val ink: Color,
    val inkDim: Color,
    val accent: Color,
    val onAccent: Color,
    val rule: Color,
    val mark: Color,
    val codePage: Color,
    val gutter: Color,
    val code: CodeInk,
)

private val lightCode = CodeInk(
    keyword = Color(0xFF9A3E7A),
    type = Color(0xFF1F6F8B),
    string = Color(0xFF2E7D4F),
    comment = Color(0xFF8A8577),
    number = Color(0xFFB2610E),
    meta = Color(0xFF6F5BC0),
    func = Color(0xFF2F5FA8),
    punct = Color(0xFF6E6A62),
    added = Color(0xFF2E7D4F),
    removed = Color(0xFFB3352E),
)

private val darkCode = CodeInk(
    keyword = Color(0xFFE29CD2),
    type = Color(0xFF79C6DE),
    string = Color(0xFF9BD4A6),
    comment = Color(0xFF79808C),
    number = Color(0xFFE8B86D),
    meta = Color(0xFFB5A6F0),
    func = Color(0xFF8FB7EE),
    punct = Color(0xFF9198A3),
    added = Color(0xFF9BD4A6),
    removed = Color(0xFFE5737F),
)

/** Warm white, the colour of a decent paperback page under a lamp. */
val Paper = Palette(
    dark = false,
    page = Color(0xFFFBFAF8),
    chrome = Color(0xFFF3F1EC),
    raised = Color(0xFFFFFFFF),
    ink = Color(0xFF1B1D21),
    inkDim = Color(0xFF6B7079),
    accent = Color(0xFF2F6F62),
    onAccent = Color(0xFFFFFFFF),
    rule = Color(0xFFE4E1DB),
    mark = Color(0xFFFFE08A),
    codePage = Color(0xFFF5F3EE),
    gutter = Color(0xFFB4AFA4),
    code = lightCode,
)

val Sepia = Palette(
    dark = false,
    page = Color(0xFFF4ECD8),
    chrome = Color(0xFFEDE3CB),
    raised = Color(0xFFF9F3E3),
    ink = Color(0xFF4A3B2A),
    inkDim = Color(0xFF8A7558),
    accent = Color(0xFF8C5A2B),
    onAccent = Color(0xFFFFF8EC),
    rule = Color(0xFFDCCFB0),
    mark = Color(0xFFE8C877),
    codePage = Color(0xFFEDE3CB),
    gutter = Color(0xFFB09A75),
    code = lightCode,
)

val Dusk = Palette(
    dark = true,
    page = Color(0xFF16181C),
    chrome = Color(0xFF1D2026),
    raised = Color(0xFF232730),
    ink = Color(0xFFE3E5E9),
    inkDim = Color(0xFF9198A3),
    accent = Color(0xFF6FD1BB),
    onAccent = Color(0xFF0E1614),
    rule = Color(0xFF2C313A),
    mark = Color(0xFF4A5B3E),
    codePage = Color(0xFF12141A),
    gutter = Color(0xFF555C68),
    code = darkCode,
)

val Black = Palette(
    dark = true,
    page = Color(0xFF000000),
    chrome = Color(0xFF0B0D10),
    raised = Color(0xFF14171B),
    ink = Color(0xFFE6E8EC),
    inkDim = Color(0xFF878D97),
    accent = Color(0xFF6FD1BB),
    onAccent = Color(0xFF0E1614),
    rule = Color(0xFF23272D),
    mark = Color(0xFF3E4D34),
    codePage = Color(0xFF07090B),
    gutter = Color(0xFF4A5059),
    code = darkCode,
)

fun paletteFor(choice: ThemeChoice, systemDark: Boolean): Palette = when (choice) {
    ThemeChoice.PAPER -> Paper
    ThemeChoice.SEPIA -> Sepia
    ThemeChoice.DUSK -> Dusk
    ThemeChoice.BLACK -> Black
    ThemeChoice.SYSTEM -> if (systemDark) Dusk else Paper
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun monoFont(weight: Int) = Font(
    R.font.jetbrains_mono_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** JetBrains Mono, shipped with the app so code looks the same on every phone. */
val Mono = FontFamily(monoFont(400), monoFont(500), monoFont(700))

fun familyFor(font: ReadingFont): FontFamily = when (font) {
    ReadingFont.SANS -> FontFamily.SansSerif
    ReadingFont.SERIF -> FontFamily.Serif
    ReadingFont.MONO -> Mono
}

val LocalPalette = staticCompositionLocalOf { Paper }
val LocalSettings = staticCompositionLocalOf { Settings() }

@Composable
fun ReaderTheme(
    palette: Palette,
    settings: Settings,
    content: @Composable () -> Unit,
) {
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent,
            background = palette.page,
            onBackground = palette.ink,
            surface = palette.chrome,
            onSurface = palette.ink,
            surfaceVariant = palette.raised,
            onSurfaceVariant = palette.inkDim,
            surfaceContainer = palette.chrome,
            surfaceContainerHigh = palette.raised,
            surfaceContainerHighest = palette.raised,
            surfaceContainerLow = palette.chrome,
            outline = palette.rule,
            outlineVariant = palette.rule,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accent,
            background = palette.page,
            onBackground = palette.ink,
            surface = palette.chrome,
            onSurface = palette.ink,
            surfaceVariant = palette.raised,
            onSurfaceVariant = palette.inkDim,
            surfaceContainer = palette.chrome,
            surfaceContainerHigh = palette.raised,
            surfaceContainerHighest = palette.raised,
            surfaceContainerLow = palette.chrome,
            outline = palette.rule,
            outlineVariant = palette.rule,
        )
    }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalSettings provides settings,
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
