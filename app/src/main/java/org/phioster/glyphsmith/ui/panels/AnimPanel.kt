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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.UiState
import org.phioster.glyphsmith.anim.AnimCurve
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.anim.AnimTrack
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.StepperDropdown
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalSlider
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term
import java.util.Locale

/**
 * Animation of a still image: parameters move, the picture doesn't.
 *
 * Each track drives one parameter between two values on a curve. Curves are evaluated over
 * normalised loop time, so whole-numbered cycle counts always come back to where they
 * started and the loop is seamless.
 */
@Composable
fun AnimPanel(
    state: UiState,
    onChange: (AsciiParams) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onExportGif: () -> Unit,
    onExportMp4: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = state.params
    val animation = params.animation
    fun update(block: AnimationParams.() -> AnimationParams) =
        onChange(params.copy(animation = animation.block()))

    Column(modifier.fillMaxWidth()) {
        SectionHeader("animation")

        TerminalToggle(
            label = "animate",
            checked = animation.enabled,
            onCheckedChange = { on -> update { copy(enabled = on) } },
        )

        if (!animation.enabled) {
            Text(
                "drives depth, offset, seeds and effect angles over time — no video needed, " +
                    "the still image stays still",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@Column
        }

        TerminalSlider(
            label = "frames",
            value = animation.frames.toFloat(),
            range = AnimationParams.FRAME_RANGE.first.toFloat()..AnimationParams.FRAME_RANGE.last.toFloat(),
            valueText = "${animation.frames}",
            onValueChange = { v -> update { copy(frames = v.toInt()) } },
        )
        TerminalSlider(
            label = "fps",
            value = animation.fps.toFloat(),
            range = AnimationParams.FPS_RANGE.first.toFloat()..AnimationParams.FPS_RANGE.last.toFloat(),
            valueText = "${animation.fps}",
            onValueChange = { v -> update { copy(fps = v.toInt()) } },
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("LOOP", color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
            Text(
                String.format(Locale.US, "%.1fs · %d tracks", animation.durationSeconds, animation.activeCount),
                color = Term.Ink,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionHeader("tracks")

        AnimTarget.entries.forEach { target ->
            TrackSection(animation.track(target)) { track -> update { withTrack(track) } }
        }

        SectionHeader("playback")

        val ready = state.hasImage && !state.working
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton(
                label = if (state.animPlaying) "playing" else "play loop",
                onClick = onPlay,
                modifier = Modifier.weight(1f),
                enabled = ready && animation.activeCount > 0,
            )
            TerminalButton(
                label = "stop",
                onClick = onStop,
                modifier = Modifier.weight(1f),
                enabled = state.animPlaying,
            )
        }
        if (animation.activeCount == 0) {
            Text(
                "switch on at least one track — nothing moves otherwise",
                color = Term.Amber,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionHeader("export")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton("export gif", onExportGif, Modifier.weight(1f), ready)
            TerminalButton("export mp4", onExportMp4, Modifier.weight(1f), ready)
        }
        Text(
            "frame size is capped to keep the whole set in memory, so a long animation " +
                "exports smaller than a short one",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TrackSection(track: AnimTrack, onChange: (AnimTrack) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, if (track.enabled) Term.InkDim else Term.InkFaint, RectangleShape)
            .background(if (track.enabled) Term.Surface else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        TerminalToggle(
            label = track.target.label,
            checked = track.enabled,
            onCheckedChange = { onChange(track.copy(enabled = it)) },
        )
        if (!track.enabled) return@Column

        StepperDropdown(
            label = "curve",
            items = AnimCurve.entries.toList(),
            selectedIndex = AnimCurve.entries.indexOf(track.curve),
            onSelect = { onChange(track.copy(curve = AnimCurve.entries[it])) },
            itemLabel = { it.label },
            itemDetail = {
                when (it) {
                    AnimCurve.SINE -> "smooth there and back"
                    AnimCurve.TRIANGLE -> "linear there and back"
                    AnimCurve.SAWTOOTH -> "ramp, then snap back"
                    AnimCurve.PULSE -> "hard switch between the two ends"
                    AnimCurve.RANDOM -> "a new value every frame, same every render"
                }
            },
        )
        TerminalSlider(
            label = "from",
            value = track.from.toFloat(),
            range = track.target.min.toFloat()..track.target.max.toFloat(),
            valueText = "${track.from}",
            onValueChange = { onChange(track.copy(from = it.toInt())) },
        )
        TerminalSlider(
            label = "to",
            value = track.to.toFloat(),
            range = track.target.min.toFloat()..track.target.max.toFloat(),
            valueText = "${track.to}",
            onValueChange = { onChange(track.copy(to = it.toInt())) },
        )
        TerminalSlider(
            label = "cycles per loop",
            value = track.cycles.toFloat(),
            range = 1f..8f,
            steps = 6,
            valueText = "${track.cycles}×",
            onValueChange = { onChange(track.copy(cycles = it.toInt())) },
        )
        TerminalSlider(
            label = "phase",
            value = track.phase.toFloat(),
            range = 0f..100f,
            valueText = "${track.phase}%",
            onValueChange = { onChange(track.copy(phase = it.toInt())) },
        )
    }
}
