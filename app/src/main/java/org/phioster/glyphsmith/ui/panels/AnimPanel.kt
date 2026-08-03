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
import org.phioster.glyphsmith.UiState
import org.phioster.glyphsmith.anim.AnimCurve
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.anim.AnimSegment
import org.phioster.glyphsmith.anim.AnimTrack
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.anim.TemporalParams
import org.phioster.glyphsmith.anim.TemporalPattern
import org.phioster.glyphsmith.render.RenderSettings
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
    onChange: (RenderSettings) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onExportGif: () -> Unit,
    onExportMp4: () -> Unit,
    onPreviewPosition: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = state.params
    val animation = params.animation
    fun update(block: AnimationParams.() -> AnimationParams) =
        onChange(params.copy(animation = animation.block()))

    Column(modifier.fillMaxWidth()) {
        if (state.isVideo) {
            SectionHeader("source")

            TerminalSlider(
                label = "preview frame",
                value = state.previewPosition,
                range = 0f..1f,
                valueText = String.format(Locale.US, "%.0f%%", state.previewPosition * 100),
                onValueChange = onPreviewPosition,
            )
            Text(
                "a video is its own animation: the frames below are sampled evenly across " +
                    "the whole clip, and any tracks you switch on ride on top of them",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }

        SectionHeader("animation")

        TerminalToggle(
            label = "animate parameters",
            checked = animation.enabled,
            onCheckedChange = { on -> update { copy(enabled = on) } },
        )

        // A video moves on its own, so the frame controls and the export stay reachable even
        // with parameter animation switched off — otherwise a clip could not be exported at
        // all without also arming a track it does not need.
        if (!animation.enabled && !state.isVideo) {
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

        if (animation.enabled) {
            SectionHeader("tracks")

            AnimTarget.entries.forEach { target ->
                TrackSection(animation.track(target)) { track -> update { withTrack(track) } }
            }
        }

        SegmentSection(animation) { next -> update { next } }

        SectionHeader("temporal variation")

        TemporalSection(params.temporal) { onChange(params.copy(temporal = it)) }

        SectionHeader("playback")

        val ready = state.hasImage && !state.working
        // Temporal noise is an animation in its own right, so it alone is enough to play.
        val moves = state.isVideo ||
            (animation.enabled && (animation.activeCount > 0 || animation.segments.isNotEmpty())) ||
            params.temporal.enabled
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalButton(
                label = if (state.animPlaying) "playing" else "play loop",
                onClick = onPlay,
                modifier = Modifier.weight(1f),
                enabled = ready && moves,
            )
            TerminalButton(
                label = "stop",
                onClick = onStop,
                modifier = Modifier.weight(1f),
                enabled = state.animPlaying,
            )
        }
        if (!moves) {
            Text(
                "load a video, switch on a track, or turn on temporal variation — nothing " +
                    "moves otherwise",
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

/**
 * The segment list: one property moving across a slice of the loop.
 *
 * A track spans the whole loop and repeats; a segment occupies a range and stops. Three
 * segments on one property — rise, hold, fall — is the only way to say "fade in, stay, fade
 * out", and it is what the original's timeline is for.
 *
 * Numbers rather than a draggable timeline. On a phone a pair of percentage fields is more
 * precise than a gesture, and precision is the entire point of butting two segments together.
 */
@Composable
private fun SegmentSection(
    animation: AnimationParams,
    onChange: (AnimationParams) -> Unit,
) {
    SectionHeader("segments")

    if (animation.segments.isEmpty()) {
        Text(
            "a track moves a parameter across the whole loop, over and over. a segment moves " +
                "it across a slice and then stops — three of them make a fade in, a hold and " +
                "a fade out.",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }

    animation.segments.forEachIndexed { index, segment ->
        fun edit(next: AnimSegment) {
            // Refused rather than merged: two rules for one property at one instant have no
            // sensible answer, so the change is simply dropped.
            if (!animation.canPlace(next, ignoring = index)) return
            onChange(
                animation.copy(
                    segments = animation.segments.mapIndexed { i, s -> if (i == index) next else s },
                ),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .border(1.dp, Term.InkDim, RectangleShape)
                .background(Term.Surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${segment.target.label}",
                    color = Term.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TerminalButton(
                    label = "remove",
                    accent = Term.Amber,
                    onClick = {
                        onChange(
                            animation.copy(
                                segments = animation.segments.filterIndexed { i, _ -> i != index },
                            ),
                        )
                    },
                )
            }

            StepperDropdown(
                label = "property",
                items = AnimTarget.entries.toList(),
                selectedIndex = AnimTarget.entries.indexOf(segment.target),
                onSelect = { edit(segment.copy(target = AnimTarget.entries[it])) },
                itemLabel = { it.label },
            )
            TerminalSlider(
                "from", segment.from.toFloat(),
                segment.target.min.toFloat()..segment.target.max.toFloat(),
                { edit(segment.copy(from = it.toInt())) },
                valueText = "${segment.from}",
            )
            TerminalSlider(
                "to", segment.to.toFloat(),
                segment.target.min.toFloat()..segment.target.max.toFloat(),
                { edit(segment.copy(to = it.toInt())) },
                valueText = "${segment.to}",
            )
            TerminalSlider(
                "starts at", segment.start.toFloat(), 0f..100f,
                { edit(segment.copy(start = it.toInt())) },
                valueText = "${segment.start}%",
            )
            TerminalSlider(
                "ends at", segment.end.toFloat(), 0f..100f,
                { edit(segment.copy(end = it.toInt())) },
                valueText = "${segment.end}%",
            )
            StepperDropdown(
                label = "curve",
                items = AnimCurve.entries.toList(),
                selectedIndex = AnimCurve.entries.indexOf(segment.curve),
                onSelect = { edit(segment.copy(curve = AnimCurve.entries[it])) },
                itemLabel = { it.label },
            )
        }
    }

    val candidate = AnimSegment(
        target = AnimTarget.entries.firstOrNull { target ->
            animation.segments.none { it.target == target }
        } ?: AnimTarget.entries.first(),
    )
    TerminalButton(
        label = "+ segment",
        enabled = animation.canPlace(candidate),
        onClick = { onChange(animation.copy(segments = animation.segments + candidate)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (animation.segments.isNotEmpty()) {
        Text(
            "segments may touch end to end but not overlap on the same property — a change " +
                "that would overlap is simply refused. outside every segment the base value " +
                "applies, so a hold has to be stated: from 100 to 100.",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Animated noise on the dither threshold — the thing that makes the *texture* move rather
 * than a parameter. It goes on the threshold, so it works with every dither mode, including
 * none at all.
 */
@Composable
private fun TemporalSection(params: TemporalParams, onChange: (TemporalParams) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (params.enabled) Term.InkDim else Term.InkFaint, RectangleShape)
            .background(if (params.enabled) Term.Surface else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        TerminalToggle(
            label = "temporal variation",
            checked = params.enabled,
            onCheckedChange = { onChange(params.copy(enabled = it)) },
        )
        if (!params.enabled) {
            Text(
                "nine animated noise patterns that shift the dithering itself, the way an " +
                    "old screen never quite holds still",
                color = Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@Column
        }

        StepperDropdown(
            label = "pattern",
            items = TemporalPattern.entries.toList(),
            selectedIndex = TemporalPattern.entries.indexOf(params.pattern),
            onSelect = { onChange(params.copy(pattern = TemporalPattern.entries[it])) },
            itemLabel = { it.label },
        )
        TerminalSlider(
            label = "scale",
            value = params.scale.toFloat(),
            range = TemporalParams.SCALE_RANGE.first.toFloat()..
                TemporalParams.SCALE_RANGE.last.toFloat(),
            valueText = "${params.scale} cells",
            onValueChange = { onChange(params.copy(scale = it.toInt())) },
        )
        TerminalSlider(
            label = "speed",
            value = params.speed.toFloat(),
            range = TemporalParams.SPEED_RANGE.first.toFloat()..
                TemporalParams.SPEED_RANGE.last.toFloat(),
            steps = TemporalParams.SPEED_RANGE.count() - 2,
            valueText = "${params.speed}× per loop",
            onValueChange = { onChange(params.copy(speed = it.toInt())) },
        )
        TerminalSlider(
            label = "amount",
            value = params.amount.toFloat(),
            range = 0f..100f,
            valueText = "${params.amount}/100",
            onValueChange = { onChange(params.copy(amount = it.toInt())) },
        )
        Text(
            "every pattern is periodic over the loop, so the last frame lands back on the " +
                "first — whole speeds only, for the same reason",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
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
                    AnimCurve.EASE_IN -> "one-way, starts slow"
                    AnimCurve.EASE_OUT -> "one-way, ends slow"
                    AnimCurve.EASE_IN_OUT -> "one-way, slow at both ends"
                }
            },
        )
        if (!track.curve.seamless) {
            Text(
                "one-way: this curve ends somewhere else than it began, so the loop will " +
                    "jump at the seam. Fine for a single move, not for a looping texture.",
                color = Term.Amber,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
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
