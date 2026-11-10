package org.phioster.glyphsmith.ui.panels

import androidx.compose.runtime.Composable
import org.phioster.glyphsmith.effects.NeoGlowParams
import org.phioster.glyphsmith.ui.TerminalSlider

/** The travelling runs of light; offset is the one to animate. */
@Composable
internal fun NeoGlowSection(params: NeoGlowParams, move: MoveHandler, onChange: (NeoGlowParams) -> Unit) {
    EffectSection("neo glow", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "offset", params.offset.toFloat(), 0f..100f,
            { onChange(params.copy(offset = it.toInt())) },
            valueText = "${params.offset}/100",
        )
        TerminalSlider(
            "length", params.length.toFloat(), 1f..100f,
            { onChange(params.copy(length = it.toInt())) },
            valueText = "${params.length}%",
        )
        TerminalSlider(
            "stagger", params.stagger.toFloat(), 0f..100f,
            { onChange(params.copy(stagger = it.toInt())) },
            valueText = "${params.stagger}/100",
        )
        TerminalSlider(
            "intensity", params.intensity.toFloat(), 0f..400f,
            { onChange(params.copy(intensity = it.toInt())) },
            valueText = "${params.intensity}/400",
        )
        TerminalSlider(
            "rest visible", params.rest.toFloat(), 0f..100f,
            { onChange(params.copy(rest = it.toInt())) },
            valueText = "${params.rest}%",
        )
        TerminalSlider(
            "lane width", params.lane.toFloat(), 1f..64f,
            { onChange(params.copy(lane = it.toInt())) },
            valueText = "${params.lane}px",
        )
        TerminalSlider(
            "direction", params.direction.toFloat(), 0f..359f,
            { onChange(params.copy(direction = it.toInt())) },
            valueText = "${params.direction}°",
        )
    }
}
