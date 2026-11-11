package org.phioster.glyphsmith.ui.panels

import androidx.compose.runtime.Composable
import org.phioster.glyphsmith.effects.StressReliefParams
import org.phioster.glyphsmith.ui.TerminalSlider

/** Wear: how far the borders fray, how jagged, and the grain and flecks on top. */
@Composable
internal fun StressReliefSection(params: StressReliefParams, move: MoveHandler, onChange: (StressReliefParams) -> Unit) {
    EffectSection("stress relief", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "edge wear", params.edgeWear.toFloat(), 0f..12f,
            { onChange(params.copy(edgeWear = it.toInt())) },
            steps = 11,
            valueText = "${params.edgeWear}/12",
        )
        TerminalSlider(
            "roughness", params.roughness.toFloat(), 0f..100f,
            { onChange(params.copy(roughness = it.toInt())) },
            valueText = "${params.roughness}/100",
        )
        TerminalSlider(
            "grain", params.grain.toFloat(), 0f..40f,
            { onChange(params.copy(grain = it.toInt())) },
            valueText = "${params.grain}/40",
        )
        TerminalSlider(
            "speckles", params.speckles.toFloat(), 0f..100f,
            { onChange(params.copy(speckles = it.toInt())) },
            valueText = "${params.speckles}/100",
        )
        TerminalSlider(
            "speckle size", params.speckleSize.toFloat(), 1f..12f,
            { onChange(params.copy(speckleSize = it.toInt())) },
            steps = 10,
            valueText = "${params.speckleSize}/12",
        )
        TerminalSlider(
            "seed", params.seed.toFloat(), 1f..999f,
            { onChange(params.copy(seed = it.toInt())) },
            valueText = "${params.seed}/999",
        )
        TerminalSlider(
            "mix", params.mix.toFloat(), 0f..100f,
            { onChange(params.copy(mix = it.toInt())) },
            valueText = "${params.mix}/100",
        )
    }
}
