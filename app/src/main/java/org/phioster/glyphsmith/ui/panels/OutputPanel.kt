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
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
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
    onCopy: () -> Unit,
    onShareImage: () -> Unit,
    onShareText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = state.params
    Column(modifier.fillMaxWidth()) {
        SectionHeader("output")

        TerminalSlider(
            label = "glyph size",
            value = params.fontSizePx.toFloat(),
            range = AsciiParams.FONT_SIZE_RANGE.first.toFloat()..AsciiParams.FONT_SIZE_RANGE.last.toFloat(),
            steps = AsciiParams.FONT_SIZE_RANGE.count() - 2,
            valueText = "${params.fontSizePx}px",
            onValueChange = { onChange(params.copy(fontSizePx = it.toInt())) },
        )

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
