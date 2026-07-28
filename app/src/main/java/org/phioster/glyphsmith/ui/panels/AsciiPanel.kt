package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.CharacterSet
import org.phioster.glyphsmith.ascii.CharacterSets
import org.phioster.glyphsmith.ascii.FontStyle
import org.phioster.glyphsmith.ascii.GlyphFont
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term

private const val CATEGORY_ALL = "All"

/**
 * Script Slayer's ASCII Settings, control for control: depth, character category and set,
 * injected characters, character offset, font style — plus cell size, which decides the
 * grid resolution and has no equivalent slider in the original's ASCII panel.
 */
@Composable
fun AsciiPanel(
    params: AsciiParams,
    onChange: (AsciiParams) -> Unit,
    fontLabel: String,
    missingGlyphs: String,
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

    Column(modifier.fillMaxWidth()) {
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
                onChange(params.copy(charSetId = first.id))
            },
        )

        StepperDropdown(
            label = "character set",
            items = sets,
            selectedIndex = setIndex,
            onSelect = { onChange(params.copy(charSetId = sets[it].id)) },
            itemLabel = { it.name },
            itemDetail = { it.glyphs.take(28) },
        )

        GlyphPreview(sets.getOrNull(setIndex) ?: CharacterSets.default, params, fontLabel, missingGlyphs)

        InjectField(
            value = params.injection,
            onChange = { onChange(params.copy(injection = it)) },
        )

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
