package org.phioster.glyphsmith.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ui.theme.Term
import java.util.Locale

/** `── LABEL ─────────────` — the section rule that gives the UI its terminal feel. */
@Composable
fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("──", color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
        Text(" ${label.uppercase()} ", color = Term.Ink, style = MaterialTheme.typography.labelSmall)
        Box(Modifier.weight(1f).height(1.dp).background(Term.InkFaint))
    }
}

@Composable
fun TerminalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = Term.Ink,
) {
    val tint = if (enabled) accent else Term.InkFaint
    Box(
        modifier = modifier
            .border(1.dp, tint, RectangleShape)
            .background(if (enabled) Term.SurfaceHigh else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "[$label]",
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun TerminalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(1.dp, if (selected) Term.Ink else Term.InkFaint, RectangleShape)
            .background(if (selected) Term.InkFaint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = if (selected) Term.Ink else Term.InkDim,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Label + live value on one line, slider underneath. [steps] is the number of *interior*
 * stops, so an int range of n values needs n-2.
 */
@Composable
fun TerminalSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueText: String = String.format(Locale.US, "%.2f", value),
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label.uppercase(), color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
            Text(valueText, color = Term.Ink, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Term.Ink,
                activeTrackColor = Term.InkDim,
                inactiveTrackColor = Term.InkFaint,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun TerminalToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Term.Background,
                checkedTrackColor = Term.Ink,
                uncheckedThumbColor = Term.InkDim,
                uncheckedTrackColor = Term.Surface,
                uncheckedBorderColor = Term.InkFaint,
            ),
        )
    }
}
