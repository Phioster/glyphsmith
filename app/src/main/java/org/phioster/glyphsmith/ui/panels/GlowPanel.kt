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
import org.phioster.glyphsmith.effects.GlowParams
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

/**
 * Epsilon Glow, with the same control names and ranges as the original panel.
 *
 * The glow is a post-effect on the *rendered glyphs*, so it does not change the character
 * grid — a .txt export is unaffected by anything in here.
 */
@Composable
fun GlowPanel(
    params: AsciiParams,
    onChange: (AsciiParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glow = params.glow
    fun update(block: GlowParams.() -> GlowParams) = onChange(params.copy(glow = glow.block()))

    Column(modifier.fillMaxWidth()) {
        SectionHeader("epsilon glow")

        TerminalToggle(
            label = "enable epsilon glow",
            checked = glow.enabled,
            onCheckedChange = { enabled -> update { copy(enabled = enabled) } },
        )

        TerminalSlider(
            label = "threshold",
            value = glow.threshold.toFloat(),
            range = 0f..100f,
            valueText = "${glow.threshold}/100",
            onValueChange = { v -> update { copy(threshold = v.toInt()) } },
        )
        TerminalSlider(
            label = "threshold smoothing",
            value = glow.thresholdSmoothing.toFloat(),
            range = 0f..100f,
            valueText = "${glow.thresholdSmoothing}/100",
            onValueChange = { v -> update { copy(thresholdSmoothing = v.toInt()) } },
        )
        TerminalSlider(
            label = "radius",
            value = glow.radius.toFloat(),
            range = 0f..200f,
            valueText = "${glow.radius}/200",
            onValueChange = { v -> update { copy(radius = v.toInt()) } },
        )
        TerminalToggle(
            label = "radius compensation",
            checked = glow.radiusCompensation,
            onCheckedChange = { on -> update { copy(radiusCompensation = on) } },
        )
        TerminalSlider(
            label = "intensity",
            value = glow.intensity.toFloat(),
            range = 0f..1000f,
            valueText = "${glow.intensity}/1000",
            onValueChange = { v -> update { copy(intensity = v.toInt()) } },
        )
        TerminalSlider(
            label = "aspect ratio",
            value = glow.aspectRatio.toFloat(),
            range = 0f..400f,
            valueText = "${glow.aspectRatio}/400",
            onValueChange = { v -> update { copy(aspectRatio = v.toInt()) } },
        )
        TerminalSlider(
            label = "direction (°)",
            value = glow.direction.toFloat(),
            range = 0f..359f,
            valueText = "${glow.direction}°",
            onValueChange = { v -> update { copy(direction = v.toInt()) } },
        )
        TerminalSlider(
            label = "falloff n",
            value = glow.falloff.toFloat(),
            range = 0f..50f,
            valueText = "${glow.falloff}/50",
            onValueChange = { v -> update { copy(falloff = v.toInt()) } },
        )
        TerminalSlider(
            label = "epsilon",
            value = glow.epsilon.toFloat(),
            range = 0f..100f,
            valueText = "${glow.epsilon}/100",
            onValueChange = { v -> update { copy(epsilon = v.toInt()) } },
        )
        TerminalSlider(
            label = "distance scale",
            value = glow.distanceScale.toFloat(),
            range = 0f..500f,
            valueText = "${glow.distanceScale}/500",
            onValueChange = { v -> update { copy(distanceScale = v.toInt()) } },
        )

        Text(
            "w(d) = 1 / ((d·scale)ⁿ + ε)",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        TerminalButton(
            label = "reset to default",
            onClick = { onChange(params.copy(glow = GlowParams(enabled = glow.enabled))) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}
