package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.render.LightOrbsParams
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

/** The pre-dither light orbs: their switch, and their knobs once they are on. */
@Composable
internal fun LightOrbsSection(params: LightOrbsParams, onChange: (LightOrbsParams) -> Unit) {
    SectionHeader("light orbs")
    TerminalToggle("light orbs", params.enabled, { onChange(params.copy(enabled = it)) })
    if (!params.enabled) {
        Text(
            "soft spheres of light added before the dither, so they come out drawn in the " +
                "pattern itself. animate their drift in ANIM to let them float",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    TerminalSlider(
        label = "count",
        value = params.count.toFloat(),
        range = 1f..200f,
        valueText = "${params.count}",
        onValueChange = { onChange(params.copy(count = it.toInt())) },
    )
    TerminalSlider(
        label = "size",
        value = params.size.toFloat(),
        range = 1f..100f,
        valueText = "${params.size}/100",
        onValueChange = { onChange(params.copy(size = it.toInt())) },
    )
    TerminalSlider(
        label = "intensity",
        value = params.intensity.toFloat(),
        range = 0f..100f,
        valueText = "${params.intensity}/100",
        onValueChange = { onChange(params.copy(intensity = it.toInt())) },
    )
    TerminalSlider(
        label = "random",
        value = params.random.toFloat(),
        range = 0f..100f,
        valueText = "${params.random}/100",
        onValueChange = { onChange(params.copy(random = it.toInt())) },
    )
    TerminalSlider(
        label = "drift",
        value = params.drift.toFloat(),
        range = -100f..100f,
        valueText = "${params.drift}/100",
        onValueChange = { onChange(params.copy(drift = it.toInt())) },
    )
    TerminalSlider(
        label = "direction",
        value = params.direction.toFloat(),
        range = 0f..359f,
        valueText = "${params.direction}°",
        onValueChange = { onChange(params.copy(direction = it.toInt())) },
    )
}
