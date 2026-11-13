package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.FilterQuality
import org.phioster.glyphsmith.ui.TerminalChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.glyph.CharacterSets
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PresetStore
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.theme.Term

@Composable
fun PresetPanel(
    presets: List<Preset>,
    onApply: (Preset) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: () -> Unit,
    onImport: (Uri) -> Unit,
    thumbs: Map<String, android.graphics.Bitmap>,
    onToggleFavourite: (String) -> Unit,
    onRandomise: () -> Unit,
    onResetPresets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var chosenFilter by remember { mutableStateOf<PresetFilter>(PresetFilter.All) }
    var query by remember { mutableStateOf("") }
    // Exports are plain JSON, but pickers and file managers label them inconsistently, so
    // text/* and */* are accepted too rather than hiding the file the user just exported.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalButton(
                label = "surprise me",
                onClick = onRandomise,
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "reset presets",
                accent = Term.Amber,
                onClick = onResetPresets,
                modifier = Modifier.weight(1f),
            )
        }

        // Folded away until asked for. Open, its name field sat right above the search field
        // and looked just like it, and a search was forever being typed into the wrong one.
        var saving by remember { mutableStateOf(false) }
        TerminalButton(
            label = if (saving) "save current look ▾" else "save current look ▸",
            onClick = { saving = !saving },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        )
        if (saving) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .border(1.dp, Term.Amber, RectangleShape)
                        .background(Term.SurfaceHigh)
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                ) {
                    if (name.isEmpty()) {
                        Text("name of the new preset", color = Term.InkFaint, style = MaterialTheme.typography.bodyMedium)
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Ink),
                        cursorBrush = SolidColor(Term.Ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TerminalButton(
                    label = "save",
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(name)
                        name = ""
                        saving = false
                    },
                )
            }
        }

        // The row is derived from the library on every draw, so a shelf appears when it holds
        // something and goes when it does not — and `resolve` makes sure the list can never stay
        // narrowed by a chip that is no longer there.
        PresetSearchField(query) { query = it }

        val filter = PresetFilters.resolve(presets, chosenFilter)
        FilterRow(presets, filter) { chosenFilter = it }

        val visible = PresetFilters.search(PresetFilters.apply(presets, filter), query)
        if (visible.isEmpty() && query.isNotBlank()) {
            Text(
                "nothing matches \"$query\" here — try fewer words, or the all chip",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        // Favourites first, then by the shipped category order, then by name. Sorting rather
        // than a separate section keeps one list to scroll instead of two.
        val ordered = visible.sortedWith(
            compareByDescending<Preset> { it.favourite }
                .thenBy { PresetStore.categories.indexOf(it.category).let { i -> if (i < 0) 99 else i } }
                .thenBy { it.name },
        )
        // Runs of one shelf, each under its header, two presets to a row.
        val runs = mutableListOf<Pair<Shelf, MutableList<Preset>>>()
        ordered.forEach { preset ->
            val shelf = Shelf.of(preset)
            if (runs.isEmpty() || runs.last().first != shelf) runs.add(shelf to mutableListOf())
            runs.last().second.add(preset)
        }
        runs.forEach { (shelf, members) ->
            SectionHeader(shelf.label)
            members.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pair.forEach { preset ->
                        PresetCard(
                            preset, thumbs[preset.name], Modifier.weight(1f),
                            onApply, onToggleFavourite, onDelete,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        SectionHeader("transfer")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalButton(
                label = "export all",
                onClick = onExport,
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "import",
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "exports land in Download/Glyphsmith; importing merges by name",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * What a run of presets sits under.
 *
 * A heading is not a category. Favourites are gathered from every shelf and have no category of
 * their own, and modelling that as the string "FAVOURITES" put it in the same namespace as the
 * stored category tokens — so a preset somebody had filed under that word would have silently
 * merged into the favourites run, and un-starring it would have moved it somewhere else.
 *
 * Typed instead, the two cannot collide, and the heading text is decided in one place rather
 * than by asking `preset.favourite` twice on the way past.
 */
private sealed interface Shelf {

    /** Starred presets, which sort above everything and come from every category. */
    data object Favourites : Shelf

    /** A stored category token — see [PresetStore.categories]. */
    data class Category(val id: String) : Shelf

    val label: String
        get() = when (this) {
            Favourites -> "Favourites"
            is Category -> PresetStore.label(id)
        }

    companion object {
        fun of(preset: Preset): Shelf =
            if (preset.favourite) Favourites else Category(preset.category)
    }
}

/**
 * The shelf chips.
 *
 * A row rather than a search field: eleven shelves fit on a phone if they scroll sideways, and a
 * text field would push the keyboard over the very list it is filtering. The count is on the chip
 * because it answers "is it worth tapping" before the tap.
 */
@Composable
private fun FilterRow(
    presets: List<Preset>,
    selected: PresetFilter,
    onSelect: (PresetFilter) -> Unit,
) {
    val chips = PresetFilters.available(presets)
    if (chips.size <= 1) return

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip ->
            TerminalChip(
                label = "${PresetFilters.label(chip)} ${PresetFilters.count(presets, chip)}",
                selected = chip == selected,
                // Tapping the chosen shelf again clears it, so the way out is where the way in
                // was — no separate "clear" control to find.
                onClick = { onSelect(if (chip == selected) PresetFilter.All else chip) },
            )
        }
    }
}

/** One line to type into; it narrows the list below as you type, and × clears it. */
@Composable
private fun PresetSearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .border(1.dp, Term.InkDim, RectangleShape)
            .background(Term.SurfaceHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 9.dp)) {
            if (query.isEmpty()) {
                Text(
                    "search presets — name, effect, style…",
                    color = Term.InkFaint,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Ink),
                cursorBrush = SolidColor(Term.Ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Text(
                "×",
                color = Term.Ink,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onChange("") }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** One preset as a tile: its picture large, its name and what it is made of underneath. */
@Composable
private fun PresetCard(
    preset: Preset,
    thumb: android.graphics.Bitmap?,
    modifier: Modifier,
    onApply: (Preset) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(
        modifier
            .border(1.dp, Term.InkFaint, RectangleShape)
            .background(Term.SurfaceHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT)
                .background(Term.Surface)
                .clickable { onApply(preset) },
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Unsmoothed, so a dither's pixels stay pixels at this size.
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT),
                )
            }
        }
        Text(
            if (preset.params.animation.enabled) "${preset.name} ▶" else preset.name,
            color = Term.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { onApply(preset) }.padding(start = 8.dp, end = 8.dp, top = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildString {
                    append("cell ${preset.params.cellSize}")
                    val fx = preset.params.effects.activeCount
                    if (fx > 0) append(" · $fx fx")
                    if (preset.params.animation.enabled) append(" · ${preset.params.animation.frames}f")
                },
                color = Term.InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = if (preset.favourite) "★" else "☆",
                accent = if (preset.favourite) Term.Amber else Term.InkDim,
                onClick = { onToggleFavourite(preset.name) },
            )
            TerminalButton(
                label = "del",
                accent = Term.Amber,
                onClick = { onDelete(preset.name) },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private const val CARD_ASPECT = 1.2f
