package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.ascii.Palettes
import org.phioster.glyphsmith.ui.HexColorField
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalChip
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.hexOf
import org.phioster.glyphsmith.ui.theme.Term
import kotlin.random.Random

/** How many colours the extraction offers to pull out — a short ramp or a rich one. */
private val EXTRACT_COUNTS = listOf(5, 8, 16)

/**
 * Character colour, palette and background — the original's Use Palette / Transparent
 * Background / Background Color block plus its Color Palette section, where the palette's
 * individual stops are editable.
 */
@Composable
fun ColorPanel(
    params: AsciiParams,
    onChange: (AsciiParams) -> Unit,
    onExtractPalette: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = params.renderPalette()
    val categories = remember { Palettes.categories }
    var category by remember(params.paletteId) { mutableStateOf(Palettes.byId(params.paletteId).category) }
    val palettes = remember(category) { Palettes.inCategory(category) }

    Column(modifier.fillMaxWidth()) {
        SectionHeader("character colour")

        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ColorMode.entries.forEach { mode ->
                TerminalChip(
                    label = mode.name,
                    selected = params.colorMode == mode,
                    onClick = { onChange(params.copy(colorMode = mode)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (params.colorMode) {
            ColorMode.SINGLE -> HexColorField(
                label = "ink colour",
                color = params.inkColor,
                onColorChange = { onChange(params.copy(inkColor = it)) },
            )

            ColorMode.SOURCE -> Text(
                "glyphs take the average colour of the pixels they replace",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            ColorMode.PALETTE -> PaletteSection(
                params = params,
                onChange = onChange,
                onExtractPalette = onExtractPalette,
                categories = categories,
                category = category,
                onCategoryChange = { next ->
                    category = next
                    onChange(
                        params.copy(
                            paletteId = Palettes.inCategory(next).first().id,
                            paletteOverride = emptyList(),
                        ),
                    )
                },
                palettes = palettes,
            )
        }

        SectionHeader("background")

        TerminalToggle(
            label = "transparent background",
            checked = params.transparentBackground,
            onCheckedChange = { onChange(params.copy(transparentBackground = it)) },
        )

        if (!params.transparentBackground) {
            HexColorField(
                label = "background colour",
                color = params.backgroundColor,
                onColorChange = { onChange(params.copy(backgroundColor = it)) },
            )
        }

        // A palette-coloured grid on a matching background is the usual reason an export
        // comes back blank; the swatch ramp above the background field makes that visible.
        PaletteRamp(palette.colors, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun PaletteSection(
    params: AsciiParams,
    onChange: (AsciiParams) -> Unit,
    onExtractPalette: (Int) -> Unit,
    categories: List<String>,
    category: String,
    onCategoryChange: (String) -> Unit,
    palettes: List<org.phioster.glyphsmith.ascii.Palette>,
) {
    val selected = palettes.indexOfFirst { it.id == params.paletteId }.coerceAtLeast(0)
    val stops = params.activePalette().colors

    /** Locks are stored per index, so dropping a stop has to drop its lock with it. */
    fun locksFor(size: Int) = List(size) { params.isStopLocked(it) }

    Column(Modifier.fillMaxWidth()) {
        StepperDropdown(
            label = "palette category",
            items = categories,
            selectedIndex = categories.indexOf(category).coerceAtLeast(0),
            onSelect = { onCategoryChange(categories[it]) },
        )
        StepperDropdown(
            label = "palette",
            items = palettes,
            selectedIndex = selected,
            onSelect = {
                onChange(
                    params.copy(
                        paletteId = palettes[it].id,
                        paletteOverride = emptyList(),
                        paletteLocks = emptyList(),
                    ),
                )
            },
            itemLabel = { it.name },
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EXTRACT_COUNTS.forEach { count ->
                TerminalButton(
                    label = "from image ×$count",
                    onClick = { onExtractPalette(count) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "pulls the palette out of the loaded image with the same median-cut quantiser " +
                "the GIF export uses, sorted darkest first",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        TerminalSlider(
            label = "palette depth",
            value = params.paletteDepth.toFloat(),
            range = 0f..AsciiParams.PALETTE_DEPTH_RANGE.last.toFloat(),
            valueText = if (params.paletteDepth == 0) {
                "all (${stops.size})"
            } else {
                "${params.paletteDepth} of ${stops.size}"
            },
            onValueChange = { value ->
                val depth = value.toInt()
                onChange(
                    params.copy(
                        paletteDepth = if (depth in 1 until AsciiParams.PALETTE_DEPTH_RANGE.first) {
                            0
                        } else {
                            depth
                        },
                    ),
                )
            },
        )

        Text(
            "COLORS",
            color = Term.InkDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        stops.forEachIndexed { index, stop ->
            val locked = params.isStopLocked(index)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .border(1.dp, Term.InkDim, RectangleShape)
                        .background(Color(stop)),
                )
                Text(
                    hexOf(stop),
                    color = Term.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(top = 5.dp),
                )
                TerminalButton(
                    label = if (locked) "■" else "□",
                    accent = if (locked) Term.Amber else Term.Ink,
                    onClick = {
                        val locks = locksFor(stops.size).toMutableList()
                        locks[index] = !locked
                        onChange(params.copy(paletteOverride = stops, paletteLocks = locks))
                    },
                )
                TerminalButton(
                    label = "−",
                    enabled = stops.size > 2,
                    onClick = {
                        onChange(
                            params.copy(
                                paletteOverride = stops.filterIndexed { i, _ -> i != index },
                                paletteLocks = locksFor(stops.size)
                                    .filterIndexed { i, _ -> i != index },
                            ),
                        )
                    },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalButton(
                label = "add stop",
                onClick = {
                    onChange(
                        params.copy(
                            paletteOverride = stops + stops.last(),
                            paletteLocks = locksFor(stops.size) + false,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "shuffle",
                enabled = stops.size > 1,
                onClick = {
                    onChange(
                        params.copy(
                            paletteOverride = Palettes.shuffle(
                                stops,
                                locksFor(stops.size),
                                Random.Default,
                            ),
                            paletteLocks = locksFor(stops.size),
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "reset",
                enabled = params.paletteOverride.isNotEmpty(),
                onClick = {
                    onChange(params.copy(paletteOverride = emptyList(), paletteLocks = emptyList()))
                },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "■ pins a stop against the shuffle",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (stops.isNotEmpty()) {
            HexColorField(
                label = "edit last stop",
                color = stops.last(),
                onColorChange = { edited ->
                    onChange(params.copy(paletteOverride = stops.dropLast(1) + edited))
                },
            )
        }
    }
}

@Composable
private fun PaletteRamp(colors: List<Int>, modifier: Modifier = Modifier) {
    if (colors.size < 2) return
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .border(1.dp, Term.InkFaint, RectangleShape)
            .background(Brush.horizontalGradient(colors.map { Color(it) })),
    )
}
