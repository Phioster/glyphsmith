package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.CharacterSet
import org.phioster.glyphsmith.ascii.CharacterSets
import org.phioster.glyphsmith.ascii.FontStyle
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.ascii.GlyphFont
import org.phioster.glyphsmith.core.color.ColorDistance
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

private const val CATEGORY_ALL = "All"

/**
 * Glyph rendering as an optional plugin, with the master toggle at the top.
 *
 * On, this is the reference app's ASCII settings control for control: depth, character set and
 * category, injected characters, character offset, font style — plus cell size, which decides
 * the grid resolution and has no equivalent slider in the original's ASCII panel.
 *
 * Off, the app is a pixel-dither tool and everything about characters is gone; what is left is
 * [PixelModeControls]. The two are one panel rather than two tabs because they are the same
 * decision seen from either side, and a tab the user has to find would hide that.
 */
@Composable
fun AsciiPanel(
    params: AsciiParams,
    onChange: (AsciiParams) -> Unit,
    fontLabel: String,
    missingGlyphs: String,
    rampCoverage: List<Float>,
    onAutoOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = remember { listOf(CATEGORY_ALL) + CharacterSets.categories }
    // Deliberately *not* keyed on the selected set: deriving the category from the set would
    // throw you out of "All" the moment you picked anything, and `>` would then only step
    // within that one category instead of all 48 sets.
    var category by remember { mutableStateOf(CATEGORY_ALL) }
    val filtered = remember(category) {
        if (category == CATEGORY_ALL) CharacterSets.all else CharacterSets.inCategory(category)
    }
    // A preset can select a set from outside the current filter; fall back to the full list
    // so the dropdown still shows what is actually in use.
    val sets = if (filtered.any { it.id == params.charSetId }) filtered else CharacterSets.all
    val setIndex = sets.indexOfFirst { it.id == params.charSetId }.coerceAtLeast(0)

    val glyphMode = params.renderMode.isGlyph

    Column(modifier.fillMaxWidth()) {
        SectionHeader("glyph / ascii rendering")

        // The master toggle. Off, the app is a pixel-dither tool: the same sampler and the same
        // 78 algorithms, with levels becoming colours instead of characters. Everything below
        // that is about characters therefore has nothing to say and is not drawn.
        TerminalToggle(
            label = "glyph rendering",
            checked = glyphMode,
            onCheckedChange = {
                onChange(
                    params.copy(
                        renderMode = if (it) RenderMode.GlyphMatrix else RenderMode.PurePixel,
                    ),
                )
            },
        )
        Text(
            text = if (glyphMode) {
                "levels become characters from the ramp"
            } else {
                "pixel dither — levels become colours, no character mapping"
            },
            color = Term.InkDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        if (!glyphMode) {
            PixelModeControls(params, onChange)
            return@Column
        }

        SectionHeader("ascii settings")

        TerminalSlider(
            label = "depth",
            value = params.depth.toFloat(),
            range = 1f..AsciiParams.MAX_DEPTH.toFloat(),
            steps = AsciiParams.MAX_DEPTH - 2,
            valueText = "${params.depth}/${AsciiParams.MAX_DEPTH}",
            onValueChange = { onChange(params.copy(depth = it.toInt())) },
        )

        StepperDropdown(
            label = "character category",
            items = categories,
            selectedIndex = categories.indexOf(category).coerceAtLeast(0),
            onSelect = { index ->
                category = categories[index]
                val first = if (categories[index] == CATEGORY_ALL) {
                    CharacterSets.all.first()
                } else {
                    CharacterSets.inCategory(categories[index]).first()
                }
                // A ramp override belongs to the set it was built from; carrying it into a
                // different set would make picking that set look like it did nothing.
                onChange(params.copy(charSetId = first.id, rampOverride = ""))
            },
        )

        StepperDropdown(
            label = "character set",
            items = sets,
            selectedIndex = setIndex,
            onSelect = { onChange(params.copy(charSetId = sets[it].id, rampOverride = "")) },
            itemLabel = { it.name },
            itemDetail = { it.glyphs.take(28) },
        )

        GlyphPreview(sets.getOrNull(setIndex) ?: CharacterSets.default, params, fontLabel, missingGlyphs)

        InjectField(
            value = params.injection,
            onChange = { onChange(params.copy(injection = it)) },
        )

        RampEditor(params, rampCoverage, onAutoOrder, onChange)

        TerminalSlider(
            label = "character offset",
            value = params.offset.toFloat(),
            range = 0f..params.offsetMax().toFloat(),
            steps = (params.offsetMax() - 1).coerceAtLeast(0),
            valueText = "${params.offset}/${params.offsetMax()}",
            onValueChange = { onChange(params.copy(offset = it.toInt())) },
        )

        StepperDropdown(
            label = "font style",
            items = FontStyle.entries.toList(),
            selectedIndex = FontStyle.entries.indexOf(params.fontStyle),
            onSelect = { onChange(params.copy(fontStyle = FontStyle.entries[it])) },
            itemLabel = { it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) },
        )

        StepperDropdown(
            label = "typeface",
            items = GlyphFont.entries.toList(),
            selectedIndex = GlyphFont.entries.indexOf(params.glyphFont),
            onSelect = { onChange(params.copy(glyphFont = GlyphFont.entries[it])) },
            itemLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            itemDetail = {
                when (it) {
                    GlyphFont.AUTO -> "first bundled face that covers the whole ramp"
                    GlyphFont.DEJAVU -> "DejaVu Sans Mono — four real styles, 36 of 48 sets"
                    GlyphFont.UNIFONT -> "GNU Unifont — pixel outlines, covers every built-in set"
                    GlyphFont.SYSTEM -> "device monospace — falls back for exotic glyphs"
                }
            },
        )

        SectionHeader("grid")

        TerminalSlider(
            label = "cell size",
            value = params.cellSize.toFloat(),
            range = AsciiParams.CELL_SIZE_RANGE.first.toFloat()..AsciiParams.CELL_SIZE_RANGE.last.toFloat(),
            steps = AsciiParams.CELL_SIZE_RANGE.count() - 2,
            valueText = "${params.cellSize}/${AsciiParams.CELL_SIZE_RANGE.last}",
            onValueChange = { onChange(params.copy(cellSize = it.toInt())) },
        )

        TerminalToggle(
            label = "invert ramp",
            checked = params.invert,
            onCheckedChange = { onChange(params.copy(invert = it)) },
        )

        TerminalButton(
            label = "reset to default",
            onClick = {
                onChange(
                    AsciiParams(
                        // Colour and output settings live in their own panels; resetting the
                        // ASCII section shouldn't silently undo them.
                        colorMode = params.colorMode,
                        inkColor = params.inkColor,
                        paletteId = params.paletteId,
                        paletteOverride = params.paletteOverride,
                        transparentBackground = params.transparentBackground,
                        backgroundColor = params.backgroundColor,
                        fontSizePx = params.fontSizePx,
                        effects = params.effects,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

/** Live glyph preview — the ramp exactly as the engine will use it, injection included. */
@Composable
private fun GlyphPreview(
    set: CharacterSet,
    params: AsciiParams,
    fontLabel: String,
    missingGlyphs: String,
) {
    val ramp = params.effectiveRamp()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("RAMP", color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
            Text(
                "  ${set.category} · ${ramp.length} glyphs",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            ramp,
            color = Term.Ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .border(1.dp, Term.InkFaint, RectangleShape)
                .background(Term.SurfaceHigh)
                .padding(6.dp),
        )
        if (fontLabel.isNotEmpty()) {
            Text(
                if (missingGlyphs.isEmpty()) {
                    "font: $fontLabel · all glyphs covered"
                } else {
                    "font: $fontLabel · ${missingGlyphs.length} glyph(s) missing: $missingGlyphs"
                },
                color = if (missingGlyphs.isEmpty()) Term.InkFaint else Term.Amber,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun InjectField(value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("INJECT CHARACTERS", color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
            Text(
                "  ${value.length}/${AsciiParams.MAX_INJECTION}",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(AsciiParams.MAX_INJECTION)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Term.Ink),
            cursorBrush = SolidColor(Term.Ink),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
                .border(1.dp, Term.InkDim, RectangleShape)
                .background(Term.SurfaceHigh)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

/**
 * Reordering the ramp by hand, and the measured coverage that says where it is wrong.
 *
 * The ordering is not decoration: the engine maps a cell's luminance straight onto a ramp
 * index, so a glyph out of place shows up as a flat spot or a jump in the tonal gradient.
 * The percentages come from actually rasterising each glyph in the face that will draw it,
 * which is the only way to know for a set someone typed into Inject Characters.
 *
 * The chips are a horizontal strip rather than a list of rows: a 70-glyph set would be 70
 * rows of buttons, which is neither usable on a phone nor cheap to compose.
 */
@Composable
private fun RampEditor(
    params: AsciiParams,
    coverage: List<Float>,
    onAutoOrder: () -> Unit,
    onChange: (AsciiParams) -> Unit,
) {
    val glyphs = params.baseGlyphs()
    var selected by remember(glyphs) { mutableStateOf(-1) }

    fun move(delta: Int) {
        val from = selected
        val to = from + delta
        if (from !in glyphs.indices || to !in glyphs.indices) return
        val chars = glyphs.toMutableList()
        val moved = chars.removeAt(from)
        chars.add(to, moved)
        selected = to
        onChange(params.copy(rampOverride = chars.joinToString("")))
    }

    SectionHeader("ramp order")

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        glyphs.forEachIndexed { index, glyph ->
            val chosen = index == selected
            Column(
                Modifier
                    .padding(end = 4.dp)
                    .border(1.dp, if (chosen) Term.Ink else Term.InkFaint, RectangleShape)
                    .background(if (chosen) Term.SurfaceHigh else Term.Surface)
                    .clickable { selected = if (chosen) -1 else index }
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (glyph == ' ') "␠" else glyph.toString(),
                    color = if (chosen) Term.Ink else Term.InkDim,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    coverage.getOrNull(index)?.let { "${(it * 100).toInt()}" } ?: "·",
                    color = Term.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    if (selected >= 0) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton(
                label = "◀ move",
                enabled = selected > 0,
                onClick = { move(-1) },
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "move ▶",
                enabled = selected < glyphs.lastIndex,
                onClick = { move(1) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TerminalButton(
            label = "auto-order",
            onClick = onAutoOrder,
            modifier = Modifier.weight(1f),
        )
        TerminalButton(
            label = "reset ramp",
            enabled = params.rampOverride.isNotEmpty(),
            onClick = { onChange(params.copy(rampOverride = "")) },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        "the number under each glyph is its measured ink coverage. auto-order sorts by it — " +
            "including anything you injected, which otherwise sits at the dense end unmeasured.",
        color = Term.InkFaint,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * What the panel still has to offer once glyph rendering is switched off.
 *
 * Cell size and invert survive from the glyph panel because they mean something in both modes —
 * cell size becomes the pixel-block size, invert still flips the tone. Everything else here is
 * specific to reducing an image to colours, which is a question the glyph mode never asked.
 */
@Composable
private fun PixelModeControls(params: AsciiParams, onChange: (AsciiParams) -> Unit) {
    SectionHeader("pixel grid")

    TerminalSlider(
        label = "block size",
        value = params.cellSize.toFloat(),
        range = AsciiParams.CELL_SIZE_RANGE.first.toFloat()..AsciiParams.CELL_SIZE_RANGE.last.toFloat(),
        steps = AsciiParams.CELL_SIZE_RANGE.count() - 2,
        valueText = "${params.cellSize}px",
        onValueChange = { onChange(params.copy(cellSize = it.toInt())) },
    )
    Text(
        text = "1 dithers at full resolution; larger values are visible pixel blocks",
        color = Term.InkDim,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 6.dp),
    )

    // In single-colour mode there is no palette to take a level count from, so depth is what
    // decides how many steps sit between the background and the ink.
    if (params.colorMode == ColorMode.SINGLE) {
        TerminalSlider(
            label = "levels",
            value = params.depth.toFloat(),
            range = 2f..AsciiParams.MAX_DEPTH.toFloat(),
            steps = AsciiParams.MAX_DEPTH - 3,
            valueText = "${params.depth}",
            onValueChange = { onChange(params.copy(depth = it.toInt())) },
        )
        Text(
            text = "2 is classic 1-bit dithering between background and ink",
            color = Term.InkDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }

    TerminalToggle(
        label = "invert",
        checked = params.invert,
        onCheckedChange = { onChange(params.copy(invert = it)) },
    )

    // Only the source-colour mode actually asks "which palette entry is this colour closest
    // to", so the metric is only offered where it changes anything.
    if (params.colorMode == ColorMode.SOURCE) {
        SectionHeader("colour matching")

        StepperDropdown(
            label = "distance metric",
            items = ColorDistance.entries.toList(),
            selectedIndex = ColorDistance.entries.indexOf(params.colorDistance),
            onSelect = { onChange(params.copy(colorDistance = ColorDistance.entries[it])) },
            itemLabel = { it.name.lowercase() },
            itemDetail = {
                when (it) {
                    ColorDistance.EUCLIDEAN -> "plain sRGB — fastest, over-weights the dark end"
                    ColorDistance.CIELAB -> "ΔE*ab — roughly perceptual"
                    ColorDistance.OKLAB -> "perceptual, fixes L*a*b*'s blues — best for photos"
                }
            },
        )
    }

    TerminalButton(
        label = "reset to default",
        onClick = {
            onChange(
                AsciiParams(
                    renderMode = params.renderMode,
                    colorMode = params.colorMode,
                    inkColor = params.inkColor,
                    paletteId = params.paletteId,
                    backgroundColor = params.backgroundColor,
                    effects = params.effects,
                ),
            )
        },
    )
}
