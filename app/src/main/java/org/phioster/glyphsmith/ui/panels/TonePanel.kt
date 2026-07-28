package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.theme.Term
import java.util.Locale

/**
 * The tone curve applied to each cell's luminance *before* it picks a glyph. This is what
 * decides how much of the ramp an image actually uses — a flat photo otherwise lands in the
 * middle third of the ramp and looks like mush.
 */
@Composable
fun TonePanel(
    params: AsciiParams,
    onChange: (AsciiParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader("tone")

        TerminalSlider(
            label = "brightness",
            value = params.brightness,
            range = -1f..1f,
            valueText = String.format(Locale.US, "%+.2f", params.brightness),
            onValueChange = { onChange(params.copy(brightness = it)) },
        )
        TerminalSlider(
            label = "contrast",
            value = params.contrast,
            range = 0.2f..3f,
            valueText = String.format(Locale.US, "%.2f×", params.contrast),
            onValueChange = { onChange(params.copy(contrast = it)) },
        )
        TerminalSlider(
            label = "gamma",
            value = params.gamma,
            range = 0.2f..3f,
            valueText = String.format(Locale.US, "%.2f", params.gamma),
            onValueChange = { onChange(params.copy(gamma = it)) },
        )

        Text(
            "order: gamma → contrast → brightness, then the ramp",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        TerminalButton(
            label = "reset to default",
            onClick = { onChange(params.copy(brightness = 0f, contrast = 1f, gamma = 1f)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}
