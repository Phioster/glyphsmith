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
import org.phioster.glyphsmith.effects.BlurSharpenParams
import org.phioster.glyphsmith.effects.ChromaticParams
import org.phioster.glyphsmith.effects.DiffractionStarsParams
import org.phioster.glyphsmith.effects.GlowParams
import org.phioster.glyphsmith.effects.JpegGlitchParams
import org.phioster.glyphsmith.effects.PostProcessingParams
import org.phioster.glyphsmith.effects.TintMode
import org.phioster.glyphsmith.effects.TintParams
import org.phioster.glyphsmith.ui.HexColorField
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
            "channel offset", params.offset.toFloat(), 0f..50f,
            { onChange(params.copy(offset = it.toInt())) },
            valueText = "${params.offset}px",
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
