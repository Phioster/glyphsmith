package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ascii.RenderSettings
import org.phioster.glyphsmith.ascii.EdgeDetect
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.StylePicker
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term
import java.util.Locale
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.Dither

/**
 * Everything between a cell's luminance and the glyph it ends up with: the tone curve, the
 * dithering that spreads quantisation error across neighbours, and the edge detection that
 * can override brightness with direction.
 */
@Composable
fun MappingPanel(
    params: RenderSettings,
    onChange: (RenderSettings) -> Unit,
    favourites: Set<String>,
    onToggleFavourite: (String) -> Unit,
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
        TerminalSlider(
            label = "midtones",
            value = params.midtones.toFloat(),
            range = 0f..100f,
            valueText = if (params.midtones == 50) "neutral" else "${params.midtones}/100",
            onValueChange = { onChange(params.copy(midtones = it.toInt())) },
        )
        TerminalSlider(
            label = "highlights",
            value = params.highlights.toFloat(),
            range = 0f..100f,
            valueText = if (params.highlights == 50) "neutral" else "${params.highlights}/100",
            onValueChange = { onChange(params.copy(highlights = it.toInt())) },
        )
        Text(
            "order: gamma → contrast → brightness → midtones → highlights, then the ramp",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )

        SectionHeader("pre-dither")

        TerminalSlider(
            label = "saturation",
            value = params.saturation.toFloat(),
            range = 0f..200f,
            valueText = if (params.saturation == 100) "neutral" else "${params.saturation}/200",
            onValueChange = { onChange(params.copy(saturation = it.toInt())) },
        )
        TerminalSlider(
            label = "hue",
            value = params.hue.toFloat(),
            range = 0f..359f,
            valueText = "${params.hue}°",
            onValueChange = { onChange(params.copy(hue = it.toInt())) },
        )
        TerminalSlider(
            label = "denoise",
            value = params.denoise.toFloat(),
            range = 0f..RenderSettings.NEIGHBOURHOOD_RANGE.last.toFloat(),
            steps = RenderSettings.NEIGHBOURHOOD_RANGE.count() - 2,
            valueText = if (params.denoise == 0) "off" else "${params.denoise} cells",
            onValueChange = { onChange(params.copy(denoise = it.toInt())) },
        )
        TerminalSlider(
            label = "blur",
            value = params.preBlur.toFloat(),
            range = 0f..RenderSettings.NEIGHBOURHOOD_RANGE.last.toFloat(),
            steps = RenderSettings.NEIGHBOURHOOD_RANGE.count() - 2,
            valueText = if (params.preBlur == 0) "off" else "${params.preBlur} cells",
            onValueChange = { onChange(params.copy(preBlur = it.toInt())) },
        )
        Text(
            "these run before the dither, so they change what the algorithm sees and the " +
                "pattern itself moves. saturation and blur exist in FX as well — there they " +
                "only change how the finished glyphs look. denoise drops stray cells without " +
                "softening edges; blur softens everything.",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )

        SectionHeader("dither")

        StylePicker(
            label = "mode",
            selected = params.ditherMode,
            favourites = favourites,
            onSelect = { onChange(params.copy(ditherMode = it)) },
            onToggleFavourite = { onToggleFavourite(it.name) },
            itemDetail = {
                when {
                    it == DitherMode.NONE -> "nearest glyph, error discarded"
                    Dither.isModulation(it) -> "a pattern drives the threshold"
                    Dither.isOrdered(it) -> "regular, repeating texture"
                    else -> "noisy, photographic"
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
            if (!Dither.isThresholdBased(params.ditherMode)) {
                TerminalToggle(
                    label = "serpentine scan",
                    checked = params.serpentine,
                    onCheckedChange = { onChange(params.copy(serpentine = it)) },
                )
                Text(
                    "spreads the rounding error onto neighbouring cells — this is what makes " +
                        "a short ramp read as a gradient instead of bands",
                    color = Term.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (Dither.isThresholdBased(params.ditherMode)) {
            TerminalSlider(
                label = "pattern scale",
                value = params.ditherScale.toFloat(),
                range = RenderSettings.DITHER_SCALE_RANGE.first.toFloat()..
                    RenderSettings.DITHER_SCALE_RANGE.last.toFloat(),
                valueText = "${params.ditherScale}%",
                onValueChange = { onChange(params.copy(ditherScale = it.toInt())) },
            )
            Text(
                "sizes the pattern without touching the cell size — pull the two apart far " +
                    "enough and the algorithm breaks down, which is the point",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (Dither.isModulation(params.ditherMode)) {
            TerminalSlider(
                // Named after what it does for *this* style. It is one stored value, but
                // calling it "period" while it sets a dot size makes the panel feel arbitrary.
                label = Dither.periodLabel(params.ditherMode),
                value = params.modScale.toFloat(),
                range = RenderSettings.MOD_SCALE_RANGE.first.toFloat()..
                    RenderSettings.MOD_SCALE_RANGE.last.toFloat(),
                valueText = "${params.modScale} cells",
                onValueChange = { onChange(params.copy(modScale = it.toInt())) },
            )
            Dither.densityLabel(params.ditherMode)?.let { label ->
                TerminalSlider(
                    label = label,
                    value = params.patternDensity.toFloat(),
                    range = 0f..100f,
                    valueText = "${params.patternDensity}/100",
                    onValueChange = { onChange(params.copy(patternDensity = it.toInt())) },
                )
            }
            TerminalSlider(
                label = "angle",
                value = params.modAngle.toFloat(),
                range = 0f..RenderSettings.MOD_ANGLE_RANGE.last.toFloat(),
                valueText = "${params.modAngle}°",
                onValueChange = { onChange(params.copy(modAngle = it.toInt())) },
            )
            TerminalSlider(
                label = "phase",
                value = params.modPhase.toFloat(),
                range = 0f..100f,
                valueText = "${params.modPhase}%",
                onValueChange = { onChange(params.copy(modPhase = it.toInt())) },
            )
            if (params.ditherMode == DitherMode.MOD_ORB ||
                params.ditherMode == DitherMode.BEEHIVE
            ) {
                SectionHeader("orb")

                TerminalSlider(
                    label = "count",
                    value = params.orbCount.toFloat(),
                    range = 1f..20f,
                    valueText = "${params.orbCount} per period",
                    onValueChange = { onChange(params.copy(orbCount = it.toInt())) },
                )
                TerminalSlider(
                    label = "size",
                    value = params.orbSize.toFloat(),
                    range = 5f..100f,
                    valueText = "${params.orbSize}/100",
                    onValueChange = { onChange(params.copy(orbSize = it.toInt())) },
                )
                TerminalSlider(
                    label = "intensity",
                    value = params.orbIntensity.toFloat(),
                    range = 0f..100f,
                    valueText = if (params.orbIntensity == 0) "soft gradient" else "${params.orbIntensity}/100",
                    onValueChange = { onChange(params.copy(orbIntensity = it.toInt())) },
                )
                TerminalSlider(
                    label = "random",
                    value = params.orbRandom.toFloat(),
                    range = 0f..100f,
                    valueText = "${params.orbRandom}/100",
                    onValueChange = { onChange(params.copy(orbRandom = it.toInt())) },
                )
                TerminalSlider(
                    label = "offset",
                    value = params.orbOffset.toFloat(),
                    range = -100f..100f,
                    valueText = "${params.orbOffset}/100",
                    onValueChange = { onChange(params.copy(orbOffset = it.toInt())) },
                )
                TerminalSlider(
                    label = "direction",
                    value = params.orbDirection.toFloat(),
                    range = 0f..359f,
                    valueText = "${params.orbDirection}°",
                    onValueChange = { onChange(params.copy(orbDirection = it.toInt())) },
                )
                Text(
                    "count and size pull against each other: many small orbs give a fine " +
                        "stipple, few large ones give fat overlapping blobs. intensity is " +
                        "the hardness of the rim — 0 is a smooth gradient, 100 a flat disc.",
                    color = Term.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                "animate the phase in ANIM to make the pattern travel",
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
                        saturation = 100,
                        midtones = 50,
                        highlights = 50,
                        hue = 0,
                        preBlur = 0,
                        denoise = 0,
                        ditherMode = DitherMode.NONE,
                        ditherStrength = 100,
                        serpentine = true,
                        ditherScale = 100,
                        modScale = 8,
                        modAngle = 0,
                        modPhase = 0,
                        orbCount = 1,
                        orbSize = 100,
                        orbIntensity = 0,
                        orbRandom = 0,
                        orbOffset = 0,
                        orbDirection = 0,
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
