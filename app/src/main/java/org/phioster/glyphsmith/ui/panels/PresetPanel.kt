package org.phioster.glyphsmith.ui.panels

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import org.phioster.glyphsmith.ascii.CharacterSets
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PresetStore
import org.phioster.glyphsmith.data.PlaybackQuality
import org.phioster.glyphsmith.data.PreviewQuality
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term
import org.phioster.glyphsmith.ui.theme.TermThemes

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
    themeId: String,
    onThemeChange: (String) -> Unit,
    previewQuality: PreviewQuality,
    onPreviewQualityChange: (PreviewQuality) -> Unit,
    playbackQuality: PlaybackQuality,
    onPlaybackQualityChange: (PlaybackQuality) -> Unit,
    looped: Boolean,
    onLoopedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
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

        // Favourites first, then by the shipped category order, then by name. Sorting rather
        // than a separate section keeps one list to scroll instead of two.
        val ordered = presets.sortedWith(
            compareByDescending<Preset> { it.favourite }
                .thenBy { PresetStore.categories.indexOf(it.category).let { i -> if (i < 0) 99 else i } }
                .thenBy { it.name },
        )
        var lastHeader: String? = null

        ordered.forEach { preset ->
            val header = if (preset.favourite) "FAVOURITES" else preset.category
            if (header != lastHeader) {
                SectionHeader(header.lowercase())
                lastHeader = header
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .border(1.dp, Term.InkFaint, RectangleShape)
                    .background(Term.SurfaceHigh),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumb = thumbs[preset.name]
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .border(1.dp, Term.InkFaint, RectangleShape)
                            .clickable { onApply(preset) },
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onApply(preset) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.name, color = Term.Ink, style = MaterialTheme.typography.bodyMedium)
                        if (preset.params.animation.enabled) {
                            Text(
                                "  ▶",
                                color = Term.Amber,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        buildString {
                            append(CharacterSets.byId(preset.params.charSetId).name)
                            append(" · cell ${preset.params.cellSize}")
                            val fx = preset.params.effects.activeCount
                            if (fx > 0) append(" · $fx fx")
                            if (preset.params.animation.enabled) {
                                append(" · ${preset.params.animation.frames}f")
                            }
                        },
                        color = Term.InkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TerminalButton(
                    label = if (preset.favourite) "★" else "☆",
                    accent = if (preset.favourite) Term.Amber else Term.InkDim,
                    onClick = { onToggleFavourite(preset.name) },
                )
                TerminalButton(
                    label = "del",
                    accent = Term.Amber,
                    onClick = { onDelete(preset.name) },
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
            }
        }

        SectionHeader("save current")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Ink),
                cursorBrush = SolidColor(Term.Ink),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Term.InkDim, RectangleShape)
                    .background(Term.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 9.dp),
            )
            TerminalButton(
                label = "save",
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(name)
                    name = ""
                },
            )
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

        SectionHeader("appearance")

        StepperDropdown(
            label = "theme",
            items = TermThemes.all,
            selectedIndex = TermThemes.all.indexOfFirst { it.id == themeId }.coerceAtLeast(0),
            onSelect = { onThemeChange(TermThemes.all[it].id) },
            itemLabel = { it.name },
            itemDetail = { if (it.light) "light" else "dark" },
        )
        StepperDropdown(
            label = "preview quality",
            items = PreviewQuality.entries.toList(),
            selectedIndex = PreviewQuality.entries.indexOf(previewQuality),
            onSelect = { onPreviewQualityChange(PreviewQuality.entries[it]) },
            itemLabel = { it.label },
            itemDetail = { "${it.maxSide}px — ${if (it == PreviewQuality.LIVE) "quicker" else "sharper"}" },
        )
        StepperDropdown(
            label = "playback",
            items = PlaybackQuality.entries.toList(),
            selectedIndex = PlaybackQuality.entries.indexOf(playbackQuality),
            onSelect = { onPlaybackQualityChange(PlaybackQuality.entries[it]) },
            itemLabel = { it.label },
            itemDetail = {
                when (it) {
                    PlaybackQuality.QUICK -> "every other frame, smaller — approximate"
                    PlaybackQuality.RENDERED -> "every frame at full preview size"
                }
            },
        )
        TerminalToggle(
            label = "loop playback",
            checked = looped,
            onCheckedChange = onLoopedChange,
        )
        Text(
            "live halves the preview resolution; the export is unaffected. without looping, " +
                "playback stops on the last frame instead of snapping back — which is what " +
                "you want while judging where an animation ends up.",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "the theme belongs to the app, not to a preset — loading someone else's preset " +
                "should not repaint your interface",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
