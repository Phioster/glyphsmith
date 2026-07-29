package org.phioster.glyphsmith.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ui.theme.Term
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.DitherCategory

/**
 * `LABEL` over `[ value ▾ ] [<] [>]` — the stepper-plus-dropdown control the original uses
 * for category and set selection. The arrows matter more than they look: stepping through
 * 48 sets one at a time is how you actually find the one that fits an image.
 */
@Composable
fun <T> StepperDropdown(
    label: String,
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: (T) -> String = { it.toString() },
    itemDetail: ((T) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label.uppercase(), color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Term.InkDim, RectangleShape)
                        .background(Term.SurfaceHigh)
                        .clickable(enabled = items.isNotEmpty()) { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            items.getOrNull(safeIndex)?.let(itemLabel) ?: "—",
                            color = Term.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(" ▾", color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Term.Surface).heightIn(max = 360.dp),
                ) {
                    items.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        itemLabel(item),
                                        color = if (index == safeIndex) Term.Ink else Term.InkDim,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    itemDetail?.invoke(item)?.let {
                                        Text(
                                            it,
                                            color = Term.InkFaint,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(index)
                            },
                        )
                    }
                }
            }
            StepButton("<", enabled = items.isNotEmpty()) {
                onSelect((safeIndex - 1 + items.size) % items.size)
            }
            StepButton(">", enabled = items.isNotEmpty()) {
                onSelect((safeIndex + 1) % items.size)
            }
        }
    }
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 34.dp)
            .border(1.dp, if (enabled) Term.InkDim else Term.InkFaint, RectangleShape)
            .background(Term.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (enabled) Term.Ink else Term.InkFaint, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Numeric field with a unit suffix, as in the original's Canvas Width/Height rows.
 *
 * The typed text is kept as-is while editing — clamping on every keystroke would fight the
 * user the moment they clear the field or type a leading digit below the minimum.
 */
@Composable
fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "px",
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Column(modifier.padding(vertical = 4.dp)) {
        Text(label.uppercase(), color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .border(1.dp, Term.InkDim, RectangleShape)
                    .background(Term.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        text = raw.filter(Char::isDigit).take(5)
                        text.toIntOrNull()?.let { if (it in range) onValueChange(it) }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Ink),
                    cursorBrush = SolidColor(Term.Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "  $suffix",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Swatch + `#RRGGBB` field, as in the original's Background Color row. */
@Composable
fun HexColorField(
    label: String,
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(color) { mutableStateOf(hexOf(color)) }

    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label.uppercase(), color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .border(1.dp, Term.InkDim, RectangleShape)
                    .background(Color(color)),
            )
            Box(
                Modifier
                    .weight(1f)
                    .border(1.dp, Term.InkDim, RectangleShape)
                    .background(Term.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        text = raw
                        parseHex(raw)?.let(onColorChange)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Ink),
                    cursorBrush = SolidColor(Term.Ink),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QUICK_COLORS.forEach { quick ->
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 22.dp)
                        .border(1.dp, if (quick == color) Term.Ink else Term.InkFaint, RectangleShape)
                        .background(Color(quick))
                        .clickable {
                            text = hexOf(quick)
                            onColorChange(quick)
                        },
                ) {}
            }
        }
    }
}

private val QUICK_COLORS = listOf(
    0xFF000000.toInt(),
    0xFFFFFFFF.toInt(),
    0xFF33FF66.toInt(),
    0xFFFFAA22.toInt(),
    0xFF46C8F0.toInt(),
    0xFFFF4FA3.toInt(),
)

fun hexOf(color: Int): String = String.format(java.util.Locale.US, "#%06X", color and 0xFFFFFF)

fun parseHex(raw: String): Int? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val value = cleaned.toIntOrNull(16) ?: return null
    return (0xFF shl 24) or value
}

/**
 * The style picker: the same stepper-plus-dropdown shell, but the list inside is sorted.
 *
 * A flat list worked at thirty entries and stops working long before eighty. Three things
 * make the difference, and none of them is cosmetic:
 *
 * **One section open at a time.** An accordion rather than a tree — with everything expanded
 * the sorting buys nothing, because the wall of names is back.
 *
 * **The section holding the current style opens first.** Reopening the picker almost always
 * means "something like this one, but not this one", so that is where the list should land.
 *
 * **Stars, kept apart from selection.** The arrows still step through every style in order,
 * which is how you audition a category; the star is for the handful you return to.
 */
@Composable
fun StylePicker(
    label: String,
    selected: DitherMode,
    favourites: Set<String>,
    onSelect: (DitherMode) -> Unit,
    onToggleFavourite: (DitherMode) -> Unit,
    modifier: Modifier = Modifier,
    itemDetail: (DitherMode) -> String = { "" },
) {
    var expanded by remember { mutableStateOf(false) }
    // Keyed to the selection so that picking a style in another section leaves that section
    // open next time, instead of snapping back to wherever the user started.
    var openCategory by remember(selected) { mutableStateOf<DitherCategory?>(selected.category) }

    val all = DitherMode.entries
    val starred = all.filter { it.name in favourites }
    val grouped = DitherCategory.entries.map { category ->
        category to all.filter { it.category == category }
    }.filter { it.second.isNotEmpty() }

    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label.uppercase(), color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Term.InkDim, RectangleShape)
                        .background(Term.SurfaceHigh)
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selected.label,
                            color = Term.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${selected.category.label.lowercase()} ▾",
                            color = Term.InkDim,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Term.Surface).heightIn(max = 420.dp),
                ) {
                    if (starred.isNotEmpty()) {
                        SectionRow(
                            title = "★ favourites",
                            count = starred.size,
                            open = openCategory == null,
                            onClick = { openCategory = if (openCategory == null) selected.category else null },
                        )
                        if (openCategory == null) {
                            starred.forEach { mode ->
                                StyleRow(mode, selected, favourites, itemDetail, onToggleFavourite) {
                                    expanded = false
                                    onSelect(mode)
                                }
                            }
                        }
                    }
                    grouped.forEach { (category, modes) ->
                        SectionRow(
                            title = category.label,
                            count = modes.size,
                            open = openCategory == category,
                            onClick = { openCategory = if (openCategory == category) null else category },
                        )
                        if (openCategory == category) {
                            modes.forEach { mode ->
                                StyleRow(mode, selected, favourites, itemDetail, onToggleFavourite) {
                                    expanded = false
                                    onSelect(mode)
                                }
                            }
                        }
                    }
                }
            }
            StepButton("<") {
                onSelect(all[(all.indexOf(selected) - 1 + all.size) % all.size])
            }
            StepButton(">") {
                onSelect(all[(all.indexOf(selected) + 1) % all.size])
            }
        }
    }
}

@Composable
private fun SectionRow(title: String, count: Int, open: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (open) "▾" else "▸"} ${title.lowercase()}",
                    color = if (open) Term.Ink else Term.InkDim,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("$count", color = Term.InkFaint, style = MaterialTheme.typography.bodySmall)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun StyleRow(
    mode: DitherMode,
    selected: DitherMode,
    favourites: Set<String>,
    itemDetail: (DitherMode) -> String,
    onToggleFavourite: (DitherMode) -> Unit,
    onClick: () -> Unit,
) {
    val isFavourite = mode.name in favourites
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    mode.label,
                    color = if (mode == selected) Term.Ink else Term.InkDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                itemDetail(mode).takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        color = Term.InkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingIcon = {
            Text(
                if (isFavourite) "★" else "☆",
                color = if (isFavourite) Term.Amber else Term.InkFaint,
                modifier = Modifier
                    .clickable { onToggleFavourite(mode) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        onClick = onClick,
    )
}
