package no.mwmai.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.reader.MainViewModel
import no.mwmai.reader.model.ReadingFont
import no.mwmai.reader.model.ThemeChoice
import no.mwmai.reader.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: MainViewModel, onClose: () -> Unit) {
    val palette = LocalPalette.current
    val settings = vm.settings
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = palette.chrome,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Label("Page")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoice.entries.forEach { choice ->
                    Pill(
                        text = when (choice) {
                            ThemeChoice.SYSTEM -> "Auto"
                            ThemeChoice.PAPER -> "Paper"
                            ThemeChoice.SEPIA -> "Sepia"
                            ThemeChoice.DUSK -> "Dusk"
                            ThemeChoice.BLACK -> "Black"
                        },
                        selected = settings.themeChoice == choice,
                        modifier = Modifier.weight(1f),
                    ) { vm.update { it.copy(theme = choice.name) } }
                }
            }

            Label("Typeface")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadingFont.entries.forEach { font ->
                    Pill(
                        text = when (font) {
                            ReadingFont.SANS -> "Sans"
                            ReadingFont.SERIF -> "Serif"
                            ReadingFont.MONO -> "Mono"
                        },
                        selected = settings.readingFont == font,
                        modifier = Modifier.weight(1f),
                    ) { vm.update { it.copy(font = font.name) } }
                }
            }
            Text(
                "Code is always shown in JetBrains Mono, whatever is picked here. " +
                    "Pinch on any page of text to change the size without coming in here; " +
                    "on a PDF or an image, pinch magnifies the page itself and a double tap " +
                    "jumps between fitted and 2.5\u00d7.",
                color = palette.inkDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )

            SliderRow(
                label = "Text size",
                value = settings.fontSize.toFloat(),
                range = 9f..40f,
                steps = 30,
                display = "${settings.fontSize} pt",
            ) { vm.update { s -> s.copy(fontSize = it.toInt()) } }

            SliderRow(
                label = "Line spacing",
                value = settings.lineHeight.toFloat(),
                range = 110f..210f,
                steps = 9,
                display = "${settings.lineHeight}%",
            ) { vm.update { s -> s.copy(lineHeight = (it / 10).toInt() * 10) } }

            SliderRow(
                label = "Side margin",
                value = settings.margin.toFloat(),
                range = 0f..40f,
                steps = 7,
                display = "${settings.margin} dp",
            ) { vm.update { s -> s.copy(margin = it.toInt()) } }

            Label("While reading")
            SwitchRow("Keep the screen on", settings.keepScreenOn) {
                vm.update { s -> s.copy(keepScreenOn = it) }
            }
            SwitchRow("Wrap long code lines", settings.wrapCode) {
                vm.update { s -> s.copy(wrapCode = it) }
            }
            SwitchRow("Line numbers in code", settings.lineNumbers) {
                vm.update { s -> s.copy(lineNumbers = it) }
            }

            Text(
                "MWM Reader holds no internet permission, so it cannot show an " +
                    "advert, call home, or send a page anywhere. Nothing leaves the phone.",
                color = palette.inkDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    val palette = LocalPalette.current
    Text(
        text.uppercase(),
        color = palette.inkDim,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Text(
        text,
        color = if (selected) palette.onAccent else palette.ink,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        modifier = modifier
            .background(
                if (selected) palette.accent else palette.raised,
                RoundedCornerShape(20.dp),
            )
            .border(
                1.dp,
                if (selected) palette.accent else palette.rule,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = palette.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(display, color = palette.inkDim, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.rule,
                activeTickColor = palette.accent,
                inactiveTickColor = palette.rule,
            ),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onAccent,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.inkDim,
                uncheckedTrackColor = palette.raised,
                uncheckedBorderColor = palette.rule,
            ),
        )
    }
}
