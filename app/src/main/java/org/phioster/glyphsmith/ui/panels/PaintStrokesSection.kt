package org.phioster.glyphsmith.ui.panels

import androidx.compose.runtime.Composable
import org.phioster.glyphsmith.effects.PaintStrokesParams
import org.phioster.glyphsmith.ui.TerminalSlider

/** The brush: how large, how hard, how many passes, and how it treats edges. */
@Composable
internal fun PaintStrokesSection(params: PaintStrokesParams, move: MoveHandler, onChange: (PaintStrokesParams) -> Unit) {
    EffectSection("paint strokes", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        TerminalSlider(
            "brush size", params.brushSize.toFloat(), 1f..48f,
            { onChange(params.copy(brushSize = it.toInt())) },
            valueText = "${params.brushSize}/48",
        )
        TerminalSlider(
            "sharpness", params.sharpness.toFloat(), 0f..100f,
            { onChange(params.copy(sharpness = it.toInt())) },
            valueText = "${params.sharpness}/100",
        )
        TerminalSlider(
            "passes", params.passes.toFloat(), 1f..4f,
            { onChange(params.copy(passes = it.toInt())) },
            steps = 2,
            valueText = "${params.passes}/4",
        )
        TerminalSlider(
            "mix", params.mix.toFloat(), 0f..100f,
            { onChange(params.copy(mix = it.toInt())) },
            valueText = "${params.mix}/100",
        )
        TerminalSlider(
            "brush width", params.brushWidth.toFloat(), 10f..200f,
            { onChange(params.copy(brushWidth = it.toInt())) },
            valueText = "${params.brushWidth}/200",
        )
        TerminalSlider(
            "brush height", params.brushHeight.toFloat(), 10f..200f,
            { onChange(params.copy(brushHeight = it.toInt())) },
            valueText = "${params.brushHeight}/200",
        )
        TerminalSlider(
            "edge sensitivity", params.edgeSensitivity.toFloat(), 0f..100f,
            { onChange(params.copy(edgeSensitivity = it.toInt())) },
            valueText = "${params.edgeSensitivity}/100",
        )
    }
}
