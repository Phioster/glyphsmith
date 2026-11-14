package org.phioster.glyphsmith.ui.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.render.FeatherPattern
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.render.ShapeBand
import org.phioster.glyphsmith.render.ShapeBands
import org.phioster.glyphsmith.render.ShapeKind
import org.phioster.glyphsmith.render.ShapeLayout
import org.phioster.glyphsmith.data.ShapeSetFile
import org.phioster.glyphsmith.render.ShapeSet
import org.phioster.glyphsmith.render.ShapeSets
import org.phioster.glyphsmith.render.ShapeShifterParams
import org.phioster.glyphsmith.render.Shapes
import org.phioster.glyphsmith.ui.HexColorField
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.NameRow
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalChip
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

/**
 * Everything the shape shifter mode adds: the band editor, the feather, and the grid.
 *
 * The strip is the tonal range dark to bright; tap a band to edit its shape, size and width.
 */
@Composable
internal fun ShapeShifterSection(
    params: RenderSettings,
    onChange: (RenderSettings) -> Unit,
    onExport: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    kept: List<ShapeSetFile>,
    onKeep: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    val shifter = params.shapeShifter
    val bands = shifter.bands.ifEmpty { ShapeSets.default.bands }
    val update: (ShapeShifterParams) -> Unit = { onChange(params.copy(shapeShifter = it)) }
    var open by remember { mutableIntStateOf(0) }
    val selected = open.coerceIn(0, bands.lastIndex)

    SectionHeader("shape shifter")

    // Edited bands match no built-in set, so they get an entry of their own rather than
    // letting the picker claim the first set is selected.
    // The user's own sets follow the built-in ones, so those keep their places in the list.
    val own = kept.mapNotNull { file -> ShapeSetFile.bandsOf(file)?.let { ShapeSet(file.name, it) } }
    val shelf = ShapeSets.all + own
    val known = shelf.indexOfFirst { it.bands == bands }
    val sets = if (known < 0) listOf(ShapeSet("custom", bands)) + shelf else shelf
    StepperDropdown(
        label = "shape set",
        items = sets,
        selectedIndex = known.coerceAtLeast(0),
        onSelect = { update(shifter.copy(bands = sets[it].bands)) },
        itemLabel = { it.name },
    )

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TerminalButton("save set", onExport, Modifier.weight(1f))
        TerminalButton(
            "load set", { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f),
        )
    }
    // Kept sets stay in the list above; save and load are for files to pass on.
    val keptName = own.firstOrNull { it.bands == bands }?.name
    var naming by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TerminalButton(if (naming) "keep in app ▾" else "keep in app ▸", { naming = !naming }, Modifier.weight(1f), known < 0)
        TerminalButton("forget", { keptName?.let(onForget) }, Modifier.weight(1f), keptName != null)
    }
    if (naming && known < 0) {
        NameRow("name of the set", "keep") {
            onKeep(it)
            naming = false
        }
    }

    BandStrip(bands, selected) { open = it }
    BandEditor(bands, selected, shifter.perBandColor) { update(shifter.copy(bands = it)) }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TerminalButton(
            "split", { update(shifter.copy(bands = ShapeBands.split(bands, selected))) },
            Modifier.weight(1f), bands.size < ShapeBands.MAX_BANDS,
        )
        TerminalButton(
            "remove", { update(shifter.copy(bands = ShapeBands.remove(bands, selected))) },
            Modifier.weight(1f), bands.size > 1,
        )
        TerminalButton("even", { update(shifter.copy(bands = ShapeBands.distribute(bands))) }, Modifier.weight(1f))
        TerminalButton("flip", { update(shifter.copy(bands = ShapeBands.reverse(bands))) }, Modifier.weight(1f))
    }

    TerminalToggle(
        label = "colour per band",
        checked = shifter.perBandColor,
        onCheckedChange = { update(shifter.copy(perBandColor = it)) },
    )

    TerminalSlider(
        "feather", shifter.feather.toFloat(), 0f..100f,
        { update(shifter.copy(feather = it.toInt())) },
        valueText = "${shifter.feather}/100",
    )
    ChoiceRow("feather pattern", FeatherPattern.entries, shifter.featherPattern, { it.label }) {
        update(shifter.copy(featherPattern = it))
    }

    SectionHeader("shape grid")

    TerminalSlider(
        label = "cell size",
        value = params.cellSize.toFloat(),
        range = RenderSettings.CELL_SIZE_RANGE.first.toFloat()..RenderSettings.CELL_SIZE_RANGE.last.toFloat(),
        steps = RenderSettings.CELL_SIZE_RANGE.count() - 2,
        valueText = "${params.cellSize}px",
        onValueChange = { onChange(params.copy(cellSize = it.toInt())) },
    )
    TerminalSlider(
        "shape size", shifter.shapeSize.toFloat(), 10f..200f,
        { update(shifter.copy(shapeSize = it.toInt())) },
        valueText = "${shifter.shapeSize}%",
    )
    TerminalSlider(
        "tone scaling", shifter.toneScaling.toFloat(), 0f..100f,
        { update(shifter.copy(toneScaling = it.toInt())) },
        valueText = "${shifter.toneScaling}/100",
    )
    ChoiceRow("layout", ShapeLayout.entries, shifter.layout, { it.label }) {
        update(shifter.copy(layout = it))
    }
    TerminalSlider(
        "rotation", shifter.rotation.toFloat(), 0f..359f,
        { update(shifter.copy(rotation = it.toInt())) },
        valueText = "${shifter.rotation}°",
    )
    TerminalSlider(
        "rotation jitter", shifter.rotationJitter.toFloat(), 0f..100f,
        { update(shifter.copy(rotationJitter = it.toInt())) },
        valueText = "${shifter.rotationJitter}/100",
    )
    TerminalToggle(
        label = "crisp edges",
        checked = shifter.crispEdges,
        onCheckedChange = { update(shifter.copy(crispEdges = it)) },
    )
    TerminalToggle(
        label = "invert tones",
        checked = params.invert,
        onCheckedChange = { onChange(params.copy(invert = it)) },
    )
    Text(
        "without colour per band, colour comes from the COLOUR tab: source takes each cell's " +
            "own colour, palette picks by tone, single draws every shape in the ink colour.",
        color = Term.InkFaint,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** The tonal range as one bar, each band as wide as its share and wearing its shape. */
@Composable
private fun BandStrip(bands: List<ShapeBand>, selected: Int, onSelect: (Int) -> Unit) {
    val bounds = ShapeBands.bounds(bands)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(44.dp)) {
        bands.forEachIndexed { index, band ->
            val from = if (index == 0) 0f else bounds[index - 1]
            val grey = (from + bounds[index]) / 2f
            Box(
                Modifier
                    // A floor on the weight, so a very narrow band can still be tapped.
                    .weight(band.width.coerceAtLeast(MIN_STRIP_WEIGHT).toFloat())
                    .fillMaxHeight()
                    .background(Color(grey, grey, grey))
                    .border(
                        if (index == selected) 2.dp else 1.dp,
                        if (index == selected) Term.Amber else Term.InkFaint,
                        RectangleShape,
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                ShapeIcon(band.shape, if (grey > 0.5f) Color.Black else Color.White, Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun BandEditor(
    bands: List<ShapeBand>,
    index: Int,
    ownColour: Boolean,
    onChange: (List<ShapeBand>) -> Unit,
) {
    val band = bands[index]
    val set: (ShapeBand) -> Unit = { next -> onChange(bands.mapIndexed { i, old -> if (i == index) next else old }) }

    Text(
        "band ${index + 1} of ${bands.size} · tones ${ShapeBands.rangeLabel(bands, index)}",
        color = Term.InkDim,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 2.dp),
    )
    StepperDropdown(
        label = "shape",
        items = ShapeKind.entries.toList(),
        selectedIndex = band.shape.ordinal,
        onSelect = { set(band.copy(shape = ShapeKind.entries[it])) },
        itemLabel = { it.label },
    )
    TerminalSlider(
        "band size", band.size.toFloat(), 0f..200f,
        { set(band.copy(size = it.toInt())) },
        valueText = "${band.size}%",
    )
    TerminalSlider(
        "band width", band.width.toFloat(), 1f..100f,
        { set(band.copy(width = it.toInt())) },
        valueText = "${band.width}",
    )
    if (ownColour && band.shape != ShapeKind.EMPTY) {
        HexColorField(
            label = "band colour",
            color = band.color,
            onColorChange = { set(band.copy(color = it)) },
        )
    }
}

@Composable
internal fun <T> ChoiceRow(label: String, items: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Text(
        label.uppercase(),
        color = Term.InkDim,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            TerminalChip(
                label = name(item),
                selected = item == selected,
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A shape drawn from the same outlines the renderer fills. */
@Composable
private fun ShapeIcon(kind: ShapeKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val path = Path()
        path.fillType = PathFillType.EvenOdd
        Shapes.contours(kind).forEach { c ->
            path.moveTo(center.x + c[0] * radius, center.y + c[1] * radius)
            for (i in 1 until c.size / 2) {
                path.lineTo(center.x + c[2 * i] * radius, center.y + c[2 * i + 1] * radius)
            }
            path.close()
        }
        drawPath(path, color)
    }
}

private const val MIN_STRIP_WEIGHT = 8
