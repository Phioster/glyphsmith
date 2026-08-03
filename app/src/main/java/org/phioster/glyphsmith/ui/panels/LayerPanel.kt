package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ascii.RenderSettings
import org.phioster.glyphsmith.ascii.CharacterSets
import org.phioster.glyphsmith.ascii.Layer
import org.phioster.glyphsmith.ascii.LayerBlend
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

/**
 * The layer stack: extra renderings of the same picture, blended over the first.
 *
 * A layer is *captured* from the settings in force rather than edited here. Giving each
 * layer its own editor would mean a second copy of every panel in the app; taking a snapshot
 * of a look you have already dialled in is both simpler and how the work actually goes —
 * build a look, capture it, change the settings, capture again.
 */
@Composable
fun LayerPanel(
    params: RenderSettings,
    onChange: (RenderSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers = params.layers

    fun update(index: Int, block: (Layer) -> Layer) {
        onChange(
            params.copy(
                layers = layers.mapIndexed { i, layer -> if (i == index) block(layer) else layer },
            ),
        )
    }

    Column(modifier.fillMaxWidth()) {
        SectionHeader("layers")

        TerminalButton(
            label = "capture current settings as a layer",
            onClick = {
                onChange(
                    params.copy(
                        layers = layers + Layer(
                            name = "layer ${layers.size + 1}",
                            // The captured look must not carry the stack with it, or the
                            // layer would try to render everything underneath itself again.
                            params = params.copy(layers = emptyList()),
                        ),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (layers.isEmpty()) {
            Text(
                "one image, two treatments: a coarse ramp underneath and a fine one on top, " +
                    "offset and screened together. Dial in a look, capture it, change the " +
                    "settings, and the captured one stays where it was.",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@Column
        }

        Text(
            "each layer is a full render, so the preview costs one pass per layer",
            color = Term.Amber,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )

        layers.forEachIndexed { index, layer ->
            LayerBlock(
                layer = layer,
                index = index,
                last = index == layers.lastIndex,
                onUpdate = { block -> update(index) { block(it) } },
                onRemove = {
                    onChange(params.copy(layers = layers.filterIndexed { i, _ -> i != index }))
                },
                onMove = { delta ->
                    val target = index + delta
                    if (target in layers.indices) {
                        val moved = layers.toMutableList()
                        moved.add(target, moved.removeAt(index))
                        onChange(params.copy(layers = moved))
                    }
                },
                onReplace = {
                    update(index) { it.copy(params = params.copy(layers = emptyList())) }
                },
            )
        }

        TerminalButton(
            label = "remove all layers",
            accent = Term.Amber,
            onClick = { onChange(params.copy(layers = emptyList())) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

@Composable
private fun LayerBlock(
    layer: Layer,
    index: Int,
    last: Boolean,
    onUpdate: ((Layer) -> Layer) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onReplace: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .border(1.dp, if (layer.enabled) Term.InkDim else Term.InkFaint, RectangleShape)
            .background(if (layer.enabled) Term.Surface else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TerminalToggle(
                label = layer.name,
                checked = layer.enabled,
                onCheckedChange = { on -> onUpdate { it.copy(enabled = on) } },
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "▲",
                enabled = index > 0,
                onClick = { onMove(-1) },
                modifier = Modifier.padding(start = 6.dp),
            )
            TerminalButton(
                label = "▼",
                enabled = !last,
                onClick = { onMove(1) },
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (!layer.enabled) return@Column

        Text(
            "${CharacterSets.byId(layer.params.charSetId).name} · " +
                "cell ${layer.params.cellSize} · ${layer.params.ditherMode.label}",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        StepperDropdown(
            label = "blend",
            items = LayerBlend.entries.toList(),
            selectedIndex = LayerBlend.entries.indexOf(layer.blend),
            onSelect = { i -> onUpdate { it.copy(blend = LayerBlend.entries[i]) } },
            itemLabel = { it.label },
        )
        TerminalSlider(
            "opacity", layer.opacity.toFloat(), 0f..100f,
            { v -> onUpdate { it.copy(opacity = v.toInt()) } },
            valueText = "${layer.opacity}/100",
        )
        TerminalSlider(
            "offset x", layer.offsetX.toFloat(), -100f..100f,
            { v -> onUpdate { it.copy(offsetX = v.toInt()) } },
            valueText = "${layer.offsetX}%",
        )
        TerminalSlider(
            "offset y", layer.offsetY.toFloat(), -100f..100f,
            { v -> onUpdate { it.copy(offsetY = v.toInt()) } },
            valueText = "${layer.offsetY}%",
        )
        TerminalSlider(
            "scale", layer.scale.toFloat(), 10f..400f,
            { v -> onUpdate { it.copy(scale = v.toInt()) } },
            valueText = "${layer.scale}%",
        )
        TerminalSlider(
            "rotation", layer.rotation.toFloat(), 0f..359f,
            { v -> onUpdate { it.copy(rotation = v.toInt()) } },
            valueText = "${layer.rotation}°",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton(
                label = if (layer.flipHorizontal) "flip h ✓" else "flip h",
                onClick = { onUpdate { it.copy(flipHorizontal = !it.flipHorizontal) } },
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = if (layer.flipVertical) "flip v ✓" else "flip v",
                onClick = { onUpdate { it.copy(flipVertical = !it.flipVertical) } },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalButton(
                label = "replace with current",
                onClick = onReplace,
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "remove",
                accent = Term.Amber,
                onClick = onRemove,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
