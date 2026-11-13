package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.data.PlaybackQuality
import org.phioster.glyphsmith.data.PreviewQuality
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term
import org.phioster.glyphsmith.ui.theme.TermThemes

/**
 * What belongs to the app rather than to the picture: its theme and how the preview behaves.
 * It used to sit under the preset list, a long scroll away from anyone looking for it.
 */
@Composable
@Suppress("LongParameterList")
fun AppPanel(
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
    Column(modifier) {
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
