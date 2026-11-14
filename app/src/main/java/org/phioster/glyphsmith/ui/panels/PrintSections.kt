package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.effects.BlueprintParams
import org.phioster.glyphsmith.effects.CrumpledPrintParams
import org.phioster.glyphsmith.effects.CyanotypeParams
import org.phioster.glyphsmith.effects.HoneycombScreenParams
import org.phioster.glyphsmith.effects.PetrolSheenParams
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.theme.Term

@Composable
private fun About(text: String) = Text(
    text,
    color = Term.InkFaint,
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier.padding(bottom = 4.dp),
)

@Composable
private fun Knob(label: String, value: Int, range: ClosedFloatingPointRange<Float> = 0f..100f, set: (Int) -> Unit) =
    TerminalSlider(label, value.toFloat(), range, { set(it.toInt()) }, valueText = "$value")

@Composable
internal fun CyanotypeSection(params: CyanotypeParams, move: MoveHandler, onChange: (CyanotypeParams) -> Unit) {
    EffectSection("cyanotype", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        About("a sun print: the picture in prussian blue on paper coated by hand, with the brushed edge left bare")
        Knob("blue", params.strength) { onChange(params.copy(strength = it)) }
        Knob("bare edge", params.border) { onChange(params.copy(border = it)) }
        Knob("paper tooth", params.grain) { onChange(params.copy(grain = it)) }
        Knob("seed", params.seed, 1f..999f) { onChange(params.copy(seed = it)) }
    }
}

@Composable
internal fun BlueprintSection(params: BlueprintParams, move: MoveHandler, onChange: (BlueprintParams) -> Unit) {
    EffectSection("blueprint", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        About("a technical drawing: the picture's edges as pale lines on blue, over a drawing grid")
        Knob("lines", params.lines) { onChange(params.copy(lines = it)) }
        Knob("grid", params.grid) { onChange(params.copy(grid = it)) }
        Knob("picture between the lines", params.fill) { onChange(params.copy(fill = it)) }
    }
}

@Composable
internal fun HoneycombSection(params: HoneycombScreenParams, move: MoveHandler, onChange: (HoneycombScreenParams) -> Unit) {
    EffectSection("honeycomb screen", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        About("one cell per comb, as large as the picture is bright there and in its colour")
        Knob("comb size", params.cell, 4f..60f) { onChange(params.copy(cell = it)) }
        Knob("cell growth", params.size) { onChange(params.copy(size = it)) }
        Knob("colour", params.colour) { onChange(params.copy(colour = it)) }
    }
}

@Composable
internal fun PetrolSheenSection(params: PetrolSheenParams, move: MoveHandler, onChange: (PetrolSheenParams) -> Unit) {
    EffectSection("petrol sheen", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        About("oil on water: rainbow bands over the dark parts of the picture. The colours run through when the loop plays.")
        Knob("amount", params.amount) { onChange(params.copy(amount = it)) }
        Knob("pool size", params.scale) { onChange(params.copy(scale = it)) }
        Knob("keeps to the dark", params.darks) { onChange(params.copy(darks = it)) }
        Knob("hue", params.hue, 0f..359f) { onChange(params.copy(hue = it)) }
        Knob("runs per loop", params.flow, -4f..4f) { onChange(params.copy(flow = it)) }
        Knob("seed", params.seed, 1f..999f) { onChange(params.copy(seed = it)) }
    }
}

@Composable
internal fun CrumpledPrintSection(params: CrumpledPrintParams, move: MoveHandler, onChange: (CrumpledPrintParams) -> Unit) {
    EffectSection("crumpled print", params.enabled, move, { onChange(params.copy(enabled = it)) }) {
        About("the picture on paper that was balled up and smoothed out again, lit from the top left")
        Knob("folds", params.folds) { onChange(params.copy(folds = it)) }
        Knob("depth", params.depth) { onChange(params.copy(depth = it)) }
        Knob("paper showing", params.paper) { onChange(params.copy(paper = it)) }
        Knob("seed", params.seed, 1f..999f) { onChange(params.copy(seed = it)) }
    }
}
