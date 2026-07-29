package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.effects.BlendMode
import org.phioster.glyphsmith.effects.BlurSharpenParams
import org.phioster.glyphsmith.effects.ChromaticParams
import org.phioster.glyphsmith.effects.CmykHalftoneParams
import org.phioster.glyphsmith.effects.DiffractionStarsParams
import org.phioster.glyphsmith.effects.GlowParams
import org.phioster.glyphsmith.effects.JpegGlitchParams
import org.phioster.glyphsmith.effects.InterlaceParams
import org.phioster.glyphsmith.effects.PixelSortParams
import org.phioster.glyphsmith.effects.PostProcessingParams
import org.phioster.glyphsmith.effects.SliceShiftParams
import org.phioster.glyphsmith.effects.SortAxis
import org.phioster.glyphsmith.effects.SortKey
import org.phioster.glyphsmith.effects.SubtextureParams
import org.phioster.glyphsmith.effects.TextureKind
import org.phioster.glyphsmith.effects.TintMode
import org.phioster.glyphsmith.effects.TintParams
import org.phioster.glyphsmith.ui.HexColorField
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalChip
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

/**
 * The per-effect control blocks. Split out of [EffectsPanel] so that file stays about the
 * *chain* — which effects run, in what order — and this one stays about the knobs.
 */

@Composable
internal fun PostProcessingSection(
    params: PostProcessingParams,
    move: MoveHandler,
    onChange: (PostProcessingParams) -> Unit,
) {
    EffectSection("post processing", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "exposure", params.exposure.toFloat(), -100f..100f,
            { onChange(params.copy(exposure = it.toInt())) },
            valueText = "${params.exposure}/100",
        )
        TerminalSlider(
            "contrast", params.contrast.toFloat(), 0f..200f,
            { onChange(params.copy(contrast = it.toInt())) },
            valueText = "${params.contrast}/200",
        )
        TerminalSlider(
            "saturation", params.saturation.toFloat(), 0f..200f,
            { onChange(params.copy(saturation = it.toInt())) },
            valueText = "${params.saturation}/200",
        )
        TerminalSlider(
            "grain", params.grain.toFloat(), 0f..100f,
            { onChange(params.copy(grain = it.toInt())) },
            valueText = "${params.grain}/100",
        )
        TerminalSlider(
            "vignette", params.vignette.toFloat(), 0f..100f,
            { onChange(params.copy(vignette = it.toInt())) },
            valueText = "${params.vignette}/100",
        )
        TerminalSlider(
            "scanlines", params.scanlines.toFloat(), 0f..100f,
            { onChange(params.copy(scanlines = it.toInt())) },
            valueText = "${params.scanlines}/100",
        )
        if (params.scanlines > 0) {
            TerminalSlider(
                "scanline spacing", params.scanlineSpacing.toFloat(), 1f..16f,
                { onChange(params.copy(scanlineSpacing = it.toInt())) },
                steps = 14,
                valueText = "${params.scanlineSpacing}px",
            )
        }
    }
}

@Composable
internal fun BlurSharpenSection(
    params: BlurSharpenParams,
    move: MoveHandler,
    onChange: (BlurSharpenParams) -> Unit,
) {
    EffectSection("blur / sharpen", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "amount", params.amount.toFloat(), -100f..100f,
            { onChange(params.copy(amount = it.toInt())) },
            valueText = when {
                params.amount > 0 -> "blur ${params.amount}"
                params.amount < 0 -> "sharpen ${-params.amount}"
                else -> "off"
            },
        )
        TerminalSlider(
            "radius", params.radius.toFloat(), 1f..12f,
            { onChange(params.copy(radius = it.toInt())) },
            steps = 10,
            valueText = "${params.radius}px",
        )
        Text(
            "one slider, both directions — sharpening is the blur subtracted back out of " +
                "the original, so they are the same operation read either way",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun TintSection(params: TintParams, move: MoveHandler, onChange: (TintParams) -> Unit) {
    EffectSection("tint", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TintMode.entries.forEach { mode ->
                TerminalChip(
                    label = mode.name,
                    selected = params.mode == mode,
                    onClick = { onChange(params.copy(mode = mode)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (params.mode == TintMode.TINT) {
            HexColorField(
                label = "colour",
                color = params.color,
                onColorChange = { onChange(params.copy(color = it)) },
            )
        } else {
            HexColorField(
                label = "shadows",
                color = params.shadowColor,
                onColorChange = { onChange(params.copy(shadowColor = it)) },
            )
            HexColorField(
                label = "highlights",
                color = params.highlightColor,
                onColorChange = { onChange(params.copy(highlightColor = it)) },
            )
        }
        TerminalSlider(
            "amount", params.amount.toFloat(), 0f..100f,
            { onChange(params.copy(amount = it.toInt())) },
            valueText = "${params.amount}/100",
        )
    }
}

@Composable
internal fun ChromaticSection(
    params: ChromaticParams,
    move: MoveHandler,
    onChange: (ChromaticParams) -> Unit,
) {
    EffectSection("chromatic effects", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "max displace", params.maxDisplace.toFloat(), 0f..50f,
            { onChange(params.copy(maxDisplace = it.toInt())) },
            valueText = "${params.maxDisplace}px",
        )
        TerminalSlider(
            "red channel", params.redChannel.toFloat(), 0f..100f,
            { onChange(params.copy(redChannel = it.toInt())) },
            valueText = "${params.redChannel}/100",
        )
        TerminalSlider(
            "green channel", params.greenChannel.toFloat(), 0f..100f,
            { onChange(params.copy(greenChannel = it.toInt())) },
            valueText = "${params.greenChannel}/100",
        )
        TerminalSlider(
            "blue channel", params.blueChannel.toFloat(), 0f..100f,
            { onChange(params.copy(blueChannel = it.toInt())) },
            valueText = "${params.blueChannel}/100",
        )
        Text(
            "max displace gates the three channels — at 0 they do nothing however far you " +
                "move them. 50 is the middle. Align two of them to get yellow or cyan " +
                "fringing instead of a plain red/blue split; a channel that is not in the " +
                "picture will not move at all.",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        TerminalSlider(
            "angle", params.angle.toFloat(), 0f..359f,
            { onChange(params.copy(angle = it.toInt())) },
            valueText = "${params.angle}°",
        )
        TerminalSlider(
            "wave amplitude", params.waveAmplitude.toFloat(), 0f..100f,
            { onChange(params.copy(waveAmplitude = it.toInt())) },
            valueText = "${params.waveAmplitude}px",
        )
        TerminalSlider(
            "wave frequency", params.waveFrequency.toFloat(), 1f..100f,
            { onChange(params.copy(waveFrequency = it.toInt())) },
            valueText = "${params.waveFrequency}/100",
        )
        TerminalSlider(
            "wave noise", params.waveNoise.toFloat(), 0f..100f,
            { onChange(params.copy(waveNoise = it.toInt())) },
            valueText = "${params.waveNoise}/100",
        )
        SeedRow(params.seed) { onChange(params.copy(seed = it)) }
    }
}

@Composable
internal fun GlitchSection(
    params: JpegGlitchParams,
    move: MoveHandler,
    onChange: (JpegGlitchParams) -> Unit,
) {
    EffectSection("jpeg glitch", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        Text(
            "re-encodes as JPEG and damages the compressed bytes — the blocks are real decoder wreckage",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        TerminalSlider(
            "quality", params.quality.toFloat(), 1f..100f,
            { onChange(params.copy(quality = it.toInt())) },
            valueText = "${params.quality}/100",
        )
        TerminalSlider(
            "corruption", params.corruption.toFloat(), 0f..500f,
            { onChange(params.copy(corruption = it.toInt())) },
            valueText = "${params.corruption} bytes",
        )
        TerminalSlider(
            "start offset", params.startOffset.toFloat(), 0f..100f,
            { onChange(params.copy(startOffset = it.toInt())) },
            valueText = "${params.startOffset}/100",
        )
        SeedRow(params.seed) { onChange(params.copy(seed = it)) }
    }
}

@Composable
internal fun StarsSection(
    params: DiffractionStarsParams,
    move: MoveHandler,
    onChange: (DiffractionStarsParams) -> Unit,
) {
    EffectSection("diffraction stars", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "threshold", params.threshold.toFloat(), 0f..100f,
            { onChange(params.copy(threshold = it.toInt())) },
            valueText = "${params.threshold}/100",
        )
        TerminalSlider(
            "threshold smoothing", params.thresholdSmoothing.toFloat(), 0f..100f,
            { onChange(params.copy(thresholdSmoothing = it.toInt())) },
            valueText = "${params.thresholdSmoothing}/100",
        )
        TerminalSlider(
            "rays", params.rays.toFloat(), 2f..12f,
            { onChange(params.copy(rays = it.toInt())) },
            steps = 9,
            valueText = "${params.rays}",
        )
        TerminalSlider(
            "length", params.length.toFloat(), 0f..200f,
            { onChange(params.copy(length = it.toInt())) },
            valueText = "${params.length}px",
        )
        TerminalSlider(
            "intensity", params.intensity.toFloat(), 0f..1000f,
            { onChange(params.copy(intensity = it.toInt())) },
            valueText = "${params.intensity}/1000",
        )
        TerminalSlider(
            "angle", params.angle.toFloat(), 0f..359f,
            { onChange(params.copy(angle = it.toInt())) },
            valueText = "${params.angle}°",
        )
        TerminalSlider(
            "falloff n", params.falloff.toFloat(), 0f..50f,
            { onChange(params.copy(falloff = it.toInt())) },
            valueText = "${params.falloff}/50",
        )
    }
}

@Composable
internal fun SubtextureSection(
    params: SubtextureParams,
    move: MoveHandler,
    onChange: (SubtextureParams) -> Unit,
) {
    EffectSection("subtexture", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        StepperDropdown(
            label = "texture",
            items = TextureKind.entries.toList(),
            selectedIndex = TextureKind.entries.indexOf(params.kind),
            onSelect = { onChange(params.copy(kind = TextureKind.entries[it])) },
            itemLabel = { it.label },
        )
        StepperDropdown(
            label = "blend",
            items = BlendMode.entries.toList(),
            selectedIndex = BlendMode.entries.indexOf(params.blend),
            onSelect = { onChange(params.copy(blend = BlendMode.entries[it])) },
            itemLabel = { it.label },
        )
        TerminalSlider(
            "scale", params.scale.toFloat(), 1f..64f,
            { onChange(params.copy(scale = it.toInt())) },
            valueText = "${params.scale}px",
        )
        TerminalSlider(
            "intensity", params.intensity.toFloat(), 0f..100f,
            { onChange(params.copy(intensity = it.toInt())) },
            valueText = "${params.intensity}/100",
        )
        TerminalSlider(
            "angle", params.angle.toFloat(), 0f..359f,
            { onChange(params.copy(angle = it.toInt())) },
            valueText = "${params.angle}°",
        )
        TerminalToggle(
            label = "derive from image",
            checked = params.fromSource,
            onCheckedChange = { onChange(params.copy(fromSource = it)) },
        )
        Text(
            "with this on, the texture is modulated by the picture's own local detail — it " +
                "bites where there is structure and fades out over flat areas instead of " +
                "sitting on top like a sticker",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        SeedRow(params.seed) { onChange(params.copy(seed = it)) }
    }
}

@Composable
internal fun PixelSortSection(
    params: PixelSortParams,
    move: MoveHandler,
    onChange: (PixelSortParams) -> Unit,
) {
    EffectSection("pixel sort", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        Text(
            "only pixels whose brightness falls inside the band get reordered — narrow it " +
                "and the mid-tones bleed out of the edges, open it fully and the picture " +
                "becomes gradient stripes",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        StepperDropdown(
            label = "axis",
            items = SortAxis.entries.toList(),
            selectedIndex = SortAxis.entries.indexOf(params.axis),
            onSelect = { onChange(params.copy(axis = SortAxis.entries[it])) },
            itemLabel = { it.label },
        )
        StepperDropdown(
            label = "sort by",
            items = SortKey.entries.toList(),
            selectedIndex = SortKey.entries.indexOf(params.key),
            onSelect = { onChange(params.copy(key = SortKey.entries[it])) },
            itemLabel = { it.label },
        )
        TerminalSlider(
            "band low", params.thresholdLow.toFloat(), 0f..100f,
            { onChange(params.copy(thresholdLow = it.toInt())) },
            valueText = "${params.thresholdLow}/100",
        )
        TerminalSlider(
            "band high", params.thresholdHigh.toFloat(), 0f..100f,
            { onChange(params.copy(thresholdHigh = it.toInt())) },
            valueText = "${params.thresholdHigh}/100",
        )
        TerminalSlider(
            "max run", params.maxRun.toFloat(), 0f..400f,
            { onChange(params.copy(maxRun = it.toInt())) },
            valueText = if (params.maxRun == 0) "unlimited" else "${params.maxRun}px",
        )
        TerminalToggle(
            label = "reverse",
            checked = params.reverse,
            onCheckedChange = { onChange(params.copy(reverse = it)) },
        )
    }
}

@Composable
internal fun SliceShiftSection(
    params: SliceShiftParams,
    move: MoveHandler,
    onChange: (SliceShiftParams) -> Unit,
) {
    EffectSection("slice shift", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "slices", params.slices.toFloat(), 2f..120f,
            { onChange(params.copy(slices = it.toInt())) },
            valueText = "${params.slices}",
        )
        TerminalSlider(
            "max offset", params.maxOffset.toFloat(), 0f..100f,
            { onChange(params.copy(maxOffset = it.toInt())) },
            valueText = "${params.maxOffset}%",
        )
        TerminalSlider(
            "density", params.density.toFloat(), 0f..100f,
            { onChange(params.copy(density = it.toInt())) },
            valueText = "${params.density}/100",
        )
        TerminalSlider(
            "colour shift", params.colorShift.toFloat(), 0f..100f,
            { onChange(params.copy(colorShift = it.toInt())) },
            valueText = "${params.colorShift}/100",
        )
        TerminalToggle(
            label = "vertical bands",
            checked = params.vertical,
            onCheckedChange = { onChange(params.copy(vertical = it)) },
        )
        Text(
            "band heights are uneven on purpose — evenly cut slices read as a pattern, and " +
                "a pattern is the one thing a glitch must not look like",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        SeedRow(params.seed) { onChange(params.copy(seed = it)) }
    }
}

@Composable
internal fun InterlaceSection(
    params: InterlaceParams,
    move: MoveHandler,
    onChange: (InterlaceParams) -> Unit,
) {
    EffectSection("interlace", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        Text(
            "an interlaced frame is two fields — the even lines and the odd ones, sent " +
                "separately. Damage one and the other stays clean, so the corruption lands " +
                "on every other row rather than on a band. That comb is what makes it read " +
                "as a signal fault instead of a cut.",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        TerminalToggle(
            label = "damage the odd field",
            checked = params.oddField,
            onCheckedChange = { onChange(params.copy(oddField = it)) },
        )
        TerminalSlider(
            "shift", params.shift.toFloat(), 0f..100f,
            { onChange(params.copy(shift = it.toInt())) },
            valueText = "${params.shift}%",
        )
        TerminalSlider(
            "density", params.density.toFloat(), 0f..100f,
            { onChange(params.copy(density = it.toInt())) },
            valueText = "${params.density}/100",
        )
        TerminalSlider(
            "tear colour", params.tearColor.toFloat(), 0f..100f,
            { onChange(params.copy(tearColor = it.toInt())) },
            valueText = "${params.tearColor}/100",
        )
        TerminalToggle(
            label = "freeze the field",
            checked = params.freeze,
            onCheckedChange = { onChange(params.copy(freeze = it)) },
        )
        SeedRow(params.seed) { onChange(params.copy(seed = it)) }
    }
}

@Composable
internal fun CmykSection(
    params: CmykHalftoneParams,
    move: MoveHandler,
    onChange: (CmykHalftoneParams) -> Unit,
) {
    EffectSection("cmyk halftone", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        Text(
            "four screens at the classic printing angles — yellow 0°, cyan 15°, black 45°, " +
                "magenta 75°. Thirty degrees between the strong inks is what stops their " +
                "rosette collapsing into a moiré.",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        TerminalSlider(
            "screen", params.frequency.toFloat(), 2f..40f,
            { onChange(params.copy(frequency = it.toInt())) },
            valueText = "${params.frequency}px per dot",
        )
        TerminalSlider(
            "angle", params.angle.toFloat(), 0f..90f,
            { onChange(params.copy(angle = it.toInt())) },
            valueText = "+${params.angle}°",
        )
        TerminalSlider(
            "black ink", params.blackInk.toFloat(), 0f..100f,
            { onChange(params.copy(blackInk = it.toInt())) },
            valueText = "${params.blackInk}/100",
        )
        TerminalSlider(
            "mid-tone gain", params.midtoneGain.toFloat(), 1f..200f,
            { onChange(params.copy(midtoneGain = it.toInt())) },
            valueText = "${params.midtoneGain}/200",
        )
        TerminalSlider(
            "dot sharpness", params.sharpness.toFloat(), 0f..100f,
            { onChange(params.copy(sharpness = it.toInt())) },
            valueText = "${params.sharpness}/100",
        )
        Text(
            "black ink pulls the grey component out of the three chromatic inks — at 0 a " +
                "neutral is printed by all three at once, which goes muddy",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun GlowSection(params: GlowParams, move: MoveHandler, onChange: (GlowParams) -> Unit) {
    EffectSection("epsilon glow", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "threshold", params.threshold.toFloat(), 0f..100f,
            { onChange(params.copy(threshold = it.toInt())) },
            valueText = "${params.threshold}/100",
        )
        TerminalSlider(
            "threshold smoothing", params.thresholdSmoothing.toFloat(), 0f..100f,
            { onChange(params.copy(thresholdSmoothing = it.toInt())) },
            valueText = "${params.thresholdSmoothing}/100",
        )
        TerminalSlider(
            "radius", params.radius.toFloat(), 0f..200f,
            { onChange(params.copy(radius = it.toInt())) },
            valueText = "${params.radius}/200",
        )
        TerminalToggle(
            label = "radius compensation",
            checked = params.radiusCompensation,
            onCheckedChange = { onChange(params.copy(radiusCompensation = it)) },
        )
        TerminalSlider(
            "intensity", params.intensity.toFloat(), 0f..1000f,
            { onChange(params.copy(intensity = it.toInt())) },
            valueText = "${params.intensity}/1000",
        )
        TerminalSlider(
            "aspect ratio", params.aspectRatio.toFloat(), 0f..400f,
            { onChange(params.copy(aspectRatio = it.toInt())) },
            valueText = "${params.aspectRatio}/400",
        )
        TerminalSlider(
            "direction", params.direction.toFloat(), 0f..359f,
            { onChange(params.copy(direction = it.toInt())) },
            valueText = "${params.direction}°",
        )
        TerminalSlider(
            "falloff n", params.falloff.toFloat(), 0f..50f,
            { onChange(params.copy(falloff = it.toInt())) },
            valueText = "${params.falloff}/50",
        )
        TerminalSlider(
            "epsilon", params.epsilon.toFloat(), 0f..100f,
            { onChange(params.copy(epsilon = it.toInt())) },
            valueText = "${params.epsilon}/100",
        )
        TerminalSlider(
            "distance scale", params.distanceScale.toFloat(), 0f..500f,
            { onChange(params.copy(distanceScale = it.toInt())) },
            valueText = "${params.distanceScale}/500",
        )
        Text(
            "w(d) = 1 / ((d·scale)ⁿ + ε)",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
