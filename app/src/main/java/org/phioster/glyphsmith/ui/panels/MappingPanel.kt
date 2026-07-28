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
import org.phioster.glyphsmith.ascii.Dither
import org.phioster.glyphsmith.ascii.DitherMode
import org.phioster.glyphsmith.ascii.EdgeDetect
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term
import java.util.Locale

/**
 * Everything between a cell's luminance and the glyph it ends up with: the tone curve, the
 * dithering that spreads quantisation error across neighbours, and the edge detection that
 * can override brightness with direction.
 */
@Composable
fun MappingPanel(
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
            modifier = Modifier.padding(top = 6.dp),
        )

        SectionHeader("dither")

        StepperDropdown(
            label = "mode",
            items = DitherMode.entries.toList(),
            selectedIndex = DitherMode.entries.indexOf(params.ditherMode),
            onSelect = { onChange(params.copy(ditherMode = DitherMode.entries[it])) },
            itemLabel = { it.label },
            itemDetail = {
                when {
                    it == DitherMode.NONE -> "nearest glyph, error discarded"
                    Dither.isOrdered(it) -> "ordered matrix — regular, repeating texture"
                    else -> "error diffusion — noisy, photographic"
                }
            },
        )

        if (params.ditherMode != DitherMode.NONE) {
            TerminalSlider(
                label = "strength",
                value = params.ditherStrength.toFloat(),
                range = 0f..100f,
                valueText = "${params.ditherStrength}/100",
                onValueChange = { onChange(params.copy(ditherStrength = it.toInt())) },
            )
            if (!Dither.isOrdered(params.ditherMode)) {
                TerminalToggle(
                    label = "serpentine scan",
                    checked = params.serpentine,
                    onCheckedChange = { onChange(params.copy(serpentine = it)) },
                )
            }
            Text(
                "spreads the rounding error onto neighbouring cells — this is what makes a " +
                    "short ramp read as a gradient instead of bands",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SectionHeader("edges")

        TerminalToggle(
            label = "directional edge glyphs",
            checked = params.edgeEnabled,
            onCheckedChange = { onChange(params.copy(edgeEnabled = it)) },
        )

        if (params.edgeEnabled) {
            TerminalSlider(
                label = "threshold",
                value = params.edgeThreshold.toFloat(),
                range = 0f..100f,
                valueText = "${params.edgeThreshold}/100",
                onValueChange = { onChange(params.copy(edgeThreshold = it.toInt())) },
            )
            StepperDropdown(
                label = "edge glyphs",
                items = EdgeDetect.sets,
                selectedIndex = EdgeDetect.sets.indexOfFirst { it.id == params.edgeSetId }
                    .coerceAtLeast(0),
                onSelect = { onChange(params.copy(edgeSetId = EdgeDetect.sets[it].id)) },
                itemLabel = { it.name },
                itemDetail = { it.glyphs },
            )
            TerminalToggle(
                label = "edges only",
                checked = params.edgeOnly,
                onCheckedChange = { onChange(params.copy(edgeOnly = it)) },
            )
            Text(
                "a cell whose gradient clears the threshold takes the glyph matching the " +
                    "edge's direction rather than its brightness",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        TerminalButton(
            label = "reset mapping",
            onClick = {
                onChange(
                    params.copy(
                        brightness = 0f,
                        contrast = 1f,
                        gamma = 1f,
                        ditherMode = DitherMode.NONE,
                        ditherStrength = 100,
                        serpentine = true,
                        edgeEnabled = false,
                        edgeThreshold = 25,
                        edgeOnly = false,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}
