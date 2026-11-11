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
import org.phioster.glyphsmith.render.FlowAsciiParams
import org.phioster.glyphsmith.render.FlowStyle
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.TerminalChip
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

/** The controls of flow ASCII: what the rows are made of, how large and how close. */
@Composable
internal fun FlowAsciiSection(params: RenderSettings, onChange: (RenderSettings) -> Unit) {
    val flow = params.flowAscii
    fun set(next: FlowAsciiParams) = onChange(params.copy(flowAscii = next))

    SectionHeader("flow ascii")
    Text(
        "rows follow the forms of the picture instead of a grid. colour comes from the COLOUR " +
            "tab; brightness and contrast decide how dense the rows are filled",
        color = Term.InkFaint,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowStyle.entries.forEach { style ->
            TerminalChip(
                label = style.label,
                selected = flow.style == style,
                onClick = { set(flow.copy(style = style)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (flow.style == FlowStyle.GLYPHS) {
        TerminalSlider(
            label = "character size",
            value = flow.size.toFloat(),
            range = 3f..20f,
            onValueChange = { set(flow.copy(size = it.toInt())) },
            steps = 16,
            valueText = flow.size.toString(),
        )
        TerminalToggle("characters by tilt", flow.byTilt, { set(flow.copy(byTilt = it)) })
        Text(
            if (flow.byTilt) {
                "round and dense where a surface faces you, open curves where it turns aside, thin strokes at its edge"
            } else {
                "one run of characters, chosen by brightness alone"
            },
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        TerminalSlider(
            label = "hair length",
            value = flow.hairLength.toFloat(),
            range = 25f..300f,
            onValueChange = { set(flow.copy(hairLength = it.toInt())) },
            valueText = "${flow.hairLength}%",
        )
    }
    TerminalSlider(
        label = "row spacing",
        value = flow.spacing.toFloat(),
        range = 50f..200f,
        onValueChange = { set(flow.copy(spacing = it.toInt())) },
        valueText = "${flow.spacing}%",
    )
    TerminalSlider(
        label = "black below",
        value = flow.blackBelow.toFloat(),
        range = 0f..40f,
        onValueChange = { set(flow.copy(blackBelow = it.toInt())) },
        steps = 39,
        valueText = "${flow.blackBelow}%",
    )
    TerminalSlider(
        label = "lift darks",
        value = flow.liftDarks.toFloat(),
        range = 0f..100f,
        onValueChange = { set(flow.copy(liftDarks = it.toInt())) },
        valueText = "${flow.liftDarks}%",
    )
    TerminalToggle("outline strong edges", flow.outline, { set(flow.copy(outline = it)) })
}
