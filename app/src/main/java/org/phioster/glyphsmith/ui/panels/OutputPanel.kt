package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.UiState
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.export.SvgMode
import org.phioster.glyphsmith.ui.NumberField
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

/**
 * Output size and the two exports that matter: the rendered PNG, and the character grid as
 * .txt — the grid is the only export you can paste back into a terminal or a README.
 */
@Composable
fun OutputPanel(
    state: UiState,
    onChange: (AsciiParams) -> Unit,
    onFormatChange: (ImageFormat) -> Unit,
    onExportPng: () -> Unit,
    onExportTxt: () -> Unit,
    onExportSvg: (SvgMode) -> Unit,
    onCopy: () -> Unit,
    onShareImage: () -> Unit,
    onShareText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = state.params
    Column(modifier.fillMaxWidth()) {
        SectionHeader("canvas")

        TerminalToggle(
            label = "fixed canvas size",
            checked = params.canvasEnabled,
            onCheckedChange = { onChange(params.copy(canvasEnabled = it)) },
        )

        if (params.canvasEnabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = "width",
                    value = params.canvasWidth,
                    range = AsciiParams.CANVAS_SIZE_RANGE,
                    onValueChange = { onChange(params.copy(canvasWidth = it)) },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "height",
                    value = params.canvasHeight,
                    range = AsciiParams.CANVAS_SIZE_RANGE,
                    onValueChange = { onChange(params.copy(canvasHeight = it)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "the grid is scaled to fill the canvas and centred; glyph size is computed, not set",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CANVAS_PRESETS.forEach { (label, size) ->
                    TerminalButton(
                        label = label,
                        onClick = { onChange(params.copy(canvasWidth = size.first, canvasHeight = size.second)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SectionHeader("output")

        if (!params.canvasEnabled) {
            TerminalSlider(
                label = "glyph size",
                value = params.fontSizePx.toFloat(),
                range = AsciiParams.FONT_SIZE_RANGE.first.toFloat()..AsciiParams.FONT_SIZE_RANGE.last.toFloat(),
                steps = AsciiParams.FONT_SIZE_RANGE.count() - 2,
                valueText = "${params.fontSizePx}px",
                onValueChange = { onChange(params.copy(fontSizePx = it.toInt())) },
            )
        }

        InfoRow("grid", "${state.cols} × ${state.rows} cells")
        InfoRow("characters", "${state.cols * state.rows}")
        InfoRow("image", "${state.outputWidth} × ${state.outputHeight} px")

        if (state.outputWidth >= MAX_SIDE || state.outputHeight >= MAX_SIDE) {
            Text(
                "capped at ${MAX_SIDE}px — glyph size was reduced to fit",
                color = Term.Amber,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionHeader("export")

        StepperDropdown(
            label = "image format",
            items = ImageFormat.entries.toList(),
            selectedIndex = ImageFormat.entries.indexOf(state.exportFormat),
            onSelect = { onFormatChange(ImageFormat.entries[it]) },
            itemLabel = { it.name },
        )

        if (params.transparentBackground && !state.exportFormat.supportsTransparency) {
            Text(
                "${state.exportFormat.name} has no alpha — the transparent background will export black",
                color = Term.Amber,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        val enabled = state.hasImage && !state.working
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton("save image", onExportPng, Modifier.weight(1f), enabled)
            TerminalButton("save txt", onExportTxt, Modifier.weight(1f), enabled)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalButton("copy txt", onCopy, Modifier.weight(1f), enabled)
            TerminalButton("share img", onShareImage, Modifier.weight(1f), enabled)
            TerminalButton("share txt", onShareText, Modifier.weight(1f), enabled)
        }

        SectionHeader("vector")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton(
                "svg text", { onExportSvg(SvgMode.TEXT) }, Modifier.weight(1f), enabled,
            )
            TerminalButton(
                "svg outlines", { onExportSvg(SvgMode.OUTLINES) }, Modifier.weight(1f), enabled,
            )
        }
        Text(
            "text keeps the glyphs editable but needs the font on the other machine; " +
                "outlines are real paths and render anywhere — that is the one for print " +
                "and embroidery. Effects are not included: they work on pixels.",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label.uppercase(), color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Term.Ink, style = MaterialTheme.typography.bodySmall)
    }
}

private const val MAX_SIDE = 8192

/** Handy targets: a phone wallpaper, a square post, and 4K. */
private val CANVAS_PRESETS = listOf(
    "1080×1440" to (1080 to 1440),
    "1080²" to (1080 to 1080),
    "3840×2160" to (3840 to 2160),
)
