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
import org.phioster.glyphsmith.render.OscilloscopeParams
import org.phioster.glyphsmith.render.Phosphor
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.render.ScopeMotion
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.TerminalChip
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

/** The controls of the oscilloscope: the tube's colour, and how the traces lie and rise. */
@Composable
internal fun OscilloscopeSection(params: RenderSettings, onChange: (RenderSettings) -> Unit) {
    val scope = params.oscilloscope
    fun set(next: OscilloscopeParams) = onChange(params.copy(oscilloscope = next))

    SectionHeader("oscilloscope")
    Text(
        "each trace rises where the picture is bright. the colour is the tube's, not the COLOUR " +
            "tab's; brightness and contrast decide how high the picture stands",
        color = Term.InkFaint,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Phosphor.entries.forEach { phosphor ->
            TerminalChip(
                label = phosphor.label,
                selected = scope.phosphor == phosphor,
                onClick = { set(scope.copy(phosphor = phosphor)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    TerminalSlider(
        label = "trace spacing",
        value = scope.spacing.toFloat(),
        range = 4f..24f,
        onValueChange = { set(scope.copy(spacing = it.toInt())) },
        steps = 19,
        valueText = scope.spacing.toString(),
    )
    TerminalSlider(
        label = "height",
        value = scope.height.toFloat(),
        range = 0f..80f,
        onValueChange = { set(scope.copy(height = it.toInt())) },
        valueText = scope.height.toString(),
    )
    TerminalSlider(
        label = "glow",
        value = scope.glow.toFloat(),
        range = 0f..200f,
        onValueChange = { set(scope.copy(glow = it.toInt())) },
        valueText = "${scope.glow}%",
    )
    Text(
        "motion — plays in the ANIM tab, and a saved picture shows the start of the loop",
        color = Term.InkFaint,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ScopeMotion.entries.forEach { motion ->
            TerminalChip(
                label = motion.label,
                selected = scope.motion == motion,
                onClick = { set(scope.copy(motion = motion)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    TerminalToggle("near traces hide far ones", scope.hidden, { set(scope.copy(hidden = it)) })
    TerminalToggle("measuring grid", scope.graticule, { set(scope.copy(graticule = it)) })
}
