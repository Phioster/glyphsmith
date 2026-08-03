package org.phioster.glyphsmith.anim

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.ascii.RenderSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import org.phioster.glyphsmith.core.dither.Dither

/** A parameter an animation track can drive. */
/**
 * [cyclic] marks the targets whose range joins up at the ends: 359° is the same direction as
 * 0°, and a full sweep of a modulation phase lands exactly back on the pattern it started
 * from. Those are the only ones a non-closing curve may drive without leaving a visible jump
 * at the loop seam — everywhere else a sawtooth snaps back in plain sight.
 */
enum class AnimTarget(
    val label: String,
    val min: Int,
    val max: Int,
    val cyclic: Boolean = false,
) {
    DEPTH("Depth", 1, RenderSettings.MAX_DEPTH),
    CHARACTER_OFFSET("Character Offset", 0, 64),
    DITHER_STRENGTH("Dither Strength", 0, 100),
    /** A full sweep of a modulation period, so a sawtooth over 0..100 travels seamlessly. */
    MOD_PHASE("Modulation Phase", 0, 100, cyclic = true),
    /**
     * The pattern's second axis — a vortex's twist, a topography's warp, a wave's frequency
     * range. Worth animating precisely because its meaning changes with the style: one track
     * makes a spiral wind up, a contour map breathe, or a moiré drift through its beat.
     */
    PATTERN_DENSITY("Pattern Density", 0, 100),
    EDGE_THRESHOLD("Edge Threshold", 0, 100),
    GLITCH_SEED("Glitch Seed", 1, 9999),
    CHROMATIC_OFFSET("Chromatic Offset", 0, 50),
    GLOW_DIRECTION("Glow Direction", 0, 359, cyclic = true),
    STARS_ANGLE("Stars Angle", 0, 359, cyclic = true),

    /**
     * The travelling phase of the modulation-lines pass, one full cycle over 0..100.
     *
     * That pass also moves on its own from the render clock, which is enough for a plain loop.
     * A track exists so the motion can be *shaped* — eased, held, reversed, or run at a
     * different rate from the rest of the animation — like every other animated parameter.
     */
    MODULATION_PHASE("Modulation Phase (lines)", 0, 100, cyclic = true),
}

/**
 * How a track's value travels between its two ends over one cycle.
 *
 * [seamless] says whether the curve arrives back where it started. The ramps do not: they
 * run from one end to the other and snap back, which is what a one-shot move wants and what
 * a looping texture does not. The panel says which is which rather than leaving it to be
 * discovered in an exported GIF.
 */
enum class AnimCurve(val label: String, val seamless: Boolean) {
    SINE("Sine", true),
    TRIANGLE("Triangle", true),
    SAWTOOTH("Sawtooth", false),
    PULSE("Pulse", true),
    RANDOM("Random", true),
    EASE_IN("Ease In", false),
    EASE_OUT("Ease Out", false),
    EASE_IN_OUT("Ease In-Out", false),
}

/**
 * One parameter moving from [from] to [to] across a *slice* of the loop.
 *
 * The difference from [AnimTrack] is the whole reason both exist. A track is periodic and
 * spans the entire loop, which is right for a pattern that should travel forever. A segment
 * occupies a time range and then stops, which is the only way to say "fade in, hold, fade
 * out" — three segments on one property, the middle one going from a value to itself.
 *
 * Outside every segment for a property, the base value applies. Holding is therefore stated
 * rather than implied, which is more typing and far easier to reason about than a rule
 * saying which neighbour a gap inherits from.
 */
@Serializable
data class AnimSegment(
    val target: AnimTarget,
    val from: Int = 0,
    val to: Int = 100,
    /** 0..100 % of the loop. [start] is inclusive, [end] exclusive at the far edge. */
    val start: Int = 0,
    val end: Int = 100,
    val curve: AnimCurve = AnimCurve.EASE_IN_OUT,
) {
    val span: IntRange get() = minOf(start, end)..maxOf(start, end)

    /** True when both cover the same property and their ranges genuinely intersect. */
    fun collidesWith(other: AnimSegment): Boolean {
        if (target != other.target) return false
        val a = span
        val b = other.span
        // Touching end to end is allowed — that is how one segment runs into the next.
        return a.first < b.last && b.first < a.last
    }
}

@Serializable
data class AnimTrack(
    val target: AnimTarget,
    val enabled: Boolean = false,
    val curve: AnimCurve = AnimCurve.SINE,
    val from: Int = 0,
    val to: Int = 100,
    /** Cycles per loop. Whole numbers keep the loop seamless. */
    val cycles: Int = 1,
    /** 0..100 % of a cycle, shifts this track against the others. */
    val phase: Int = 0,
)

/**
 * Animation of a *still* image: instead of decoding video, the parameters themselves move.
 *
 * Every curve is evaluated over normalised loop time, so frame 0 and frame [frames] land on
 * the same value and the result tiles seamlessly — which is the whole point of a loop.
 */
@Serializable
data class AnimationParams(
    val enabled: Boolean = false,
    val frames: Int = 24,
    val fps: Int = 12,
    val tracks: List<AnimTrack> = AnimTarget.entries.map { AnimTrack(target = it, from = it.min, to = it.max) },
    /** Empty by default, so an animation built before these existed behaves identically. */
    val segments: List<AnimSegment> = emptyList(),
) {
    /**
     * Whether [segment] can be added without overlapping an existing one on the same
     * property. Refused rather than merged, the way the original refuses it — two rules for
     * one property at one instant has no sensible answer.
     */
    fun canPlace(segment: AnimSegment, ignoring: Int = -1): Boolean =
        segments.filterIndexed { i, _ -> i != ignoring }.none { it.collidesWith(segment) }

    /** The segment covering [position] (0..1) for [target], or null where none does. */
    fun segmentAt(target: AnimTarget, position: Float): AnimSegment? {
        val percent = (position * 100f)
        return segments.firstOrNull {
            it.target == target && percent >= it.span.first && percent <= it.span.last
        }
    }

    val segmentCount: Int get() = segments.size
    fun track(target: AnimTarget): AnimTrack =
        tracks.firstOrNull { it.target == target } ?: AnimTrack(target, from = target.min, to = target.max)

    fun withTrack(track: AnimTrack): AnimationParams {
        val updated = tracks.filterNot { it.target == track.target } + track
        return copy(tracks = updated.sortedBy { it.target.ordinal })
    }

    val activeCount: Int get() = tracks.count { it.enabled }

    val durationSeconds: Float get() = frames.toFloat() / fps.coerceAtLeast(1)

    companion object {
        val FRAME_RANGE = 2..120
        val FPS_RANGE = 1..30
    }
}

object Animator {

    /** Slack at a segment's ends, in percent. Smaller than any position a frame can land on. */
    private const val EDGE = 1e-3f

    /** Curve value in 0..1 for normalised loop position [u], including cycles and phase. */
    fun sample(curve: AnimCurve, u: Float, frame: Int, salt: Int): Float {
        val phased = frac(u)
        return when (curve) {
            // 1 - cos starts and ends at 0 with no discontinuity, unlike a raw sine.
            AnimCurve.SINE -> ((1f - cos(2f * PI.toFloat() * phased)) / 2f)
            AnimCurve.TRIANGLE -> 1f - abs(2f * phased - 1f)
            AnimCurve.SAWTOOTH -> phased
            AnimCurve.PULSE -> if (phased < 0.5f) 0f else 1f
            AnimCurve.RANDOM -> hash(frame, salt)
            // Eased ramps: same journey as SAWTOOTH, different pacing along it.
            AnimCurve.EASE_IN -> phased * phased
            AnimCurve.EASE_OUT -> 1f - (1f - phased) * (1f - phased)
            AnimCurve.EASE_IN_OUT -> phased * phased * (3f - 2f * phased)
        }
    }

    private fun frac(value: Float): Float = value - kotlin.math.floor(value)

    /** Deterministic per-frame noise — re-rendering the same frame must give the same value. */
    private fun hash(frame: Int, salt: Int): Float {
        var h = frame * 374761393 + salt * 668265263
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }

    fun valueAt(track: AnimTrack, frame: Int, frames: Int): Int {
        val loop = if (frames <= 0) 0f else frame.toFloat() / frames
        val u = loop * track.cycles.coerceAtLeast(1) + track.phase / 100f
        val curve = sample(track.curve, u, frame, track.target.ordinal + 1)
        return (track.from + (track.to - track.from) * curve).roundToInt()
            .coerceIn(minOf(track.from, track.to), maxOf(track.from, track.to))
    }

    /**
     * The parameters for one frame: the base settings with every enabled track applied.
     *
     * The temporal clock is deliberately *not* set here. Temporal noise and a video are both
     * animations in their own right, and this function's contract is that a disabled
     * animation changes nothing — so the caller sets the clock, which it has to do anyway
     * for a video whose parameter tracks are switched off.
     */
    fun paramsAt(base: RenderSettings, animation: AnimationParams, frame: Int): RenderSettings {
        if (!animation.enabled) return base
        var params = base
        animation.tracks.filter { it.enabled }.forEach { track ->
            val value = valueAt(track, frame, animation.frames)
            params = apply(params, track.target, value)
        }

        // Segments run after the tracks and win where they cover, because a segment is an
        // explicit statement about one stretch of time and a track is a standing rule.
        val position = if (animation.frames <= 0) 0f else frame.toFloat() / animation.frames
        animation.segments.forEach { segment ->
            val value = valueIn(segment, position) ?: return@forEach
            params = apply(params, segment.target, value)
        }
        return params
    }

    /**
     * A curve evaluated across a segment, on 0..1, **without wrapping**.
     *
     * [sample] cannot be reused here. It takes the fractional part of its input, which is
     * exactly right for a track that repeats forever and exactly wrong for a segment: the
     * end of a segment is 1.0, and `frac(1.0)` is 0, so every segment would snap back to its
     * starting value on its final frame.
     *
     * TRIANGLE keeps its there-and-back shape on purpose — inside a segment that reads as a
     * bump, which is a useful thing to be able to say in one piece instead of two.
     */
    fun shape(curve: AnimCurve, t: Float, salt: Int): Float {
        val u = t.coerceIn(0f, 1f)
        return when (curve) {
            AnimCurve.SINE -> ((1f - cos(PI.toFloat() * u)) / 2f)
            AnimCurve.TRIANGLE -> 1f - abs(2f * u - 1f)
            AnimCurve.SAWTOOTH -> u
            AnimCurve.PULSE -> if (u < 0.5f) 0f else 1f
            AnimCurve.RANDOM -> hash((u * 64f).toInt(), salt)
            AnimCurve.EASE_IN -> u * u
            AnimCurve.EASE_OUT -> 1f - (1f - u) * (1f - u)
            AnimCurve.EASE_IN_OUT -> u * u * (3f - 2f * u)
        }
    }

    /** The segment's value at [position] in 0..1, or null when it does not reach that far. */
    fun valueIn(segment: AnimSegment, position: Float): Int? {
        val span = segment.span
        val percent = position * 100f
        // The bounds are whole percentages and the position is a float, so the ends need
        // slack: 0.6f * 100f is 60.000004, and comparing that strictly against 60 drops a
        // segment's final frame.
        if (percent < span.first - EDGE || percent > span.last + EDGE) return null
        val width = (span.last - span.first).toFloat()
        // A zero-width segment is a single instant; it reports its end value rather than
        // dividing by nothing.
        val local = if (width <= 0f) 1f else ((percent - span.first) / width).coerceIn(0f, 1f)
        val shaped = shape(segment.curve, local, segment.target.ordinal + 1)
        return (segment.from + (segment.to - segment.from) * shaped).roundToInt()
            .coerceIn(minOf(segment.from, segment.to), maxOf(segment.from, segment.to))
    }

    private fun apply(params: RenderSettings, target: AnimTarget, value: Int): RenderSettings =
        when (target) {
            AnimTarget.DEPTH -> params.copy(depth = value.coerceIn(1, RenderSettings.MAX_DEPTH))
            // The offset wraps anyway, so it is never clamped to the ramp length here.
            AnimTarget.CHARACTER_OFFSET -> params.copy(offset = value)
            AnimTarget.DITHER_STRENGTH -> params.copy(ditherStrength = value.coerceIn(0, 100))
            // Left unclamped: the phase wraps inside the pattern, so a track that runs past
            // 100 simply keeps travelling instead of stalling at the end of its range.
            AnimTarget.MOD_PHASE -> params.copy(modPhase = value)
            AnimTarget.MODULATION_PHASE -> params.copy(
                effects = params.effects.copy(
                    modulationLines = params.effects.modulationLines.copy(phase = value),
                ),
            )
            AnimTarget.PATTERN_DENSITY -> params.copy(patternDensity = value.coerceIn(0, 100))
            AnimTarget.EDGE_THRESHOLD -> params.copy(edgeThreshold = value.coerceIn(0, 100))
            AnimTarget.GLITCH_SEED -> params.copy(
                effects = params.effects.copy(
                    jpegGlitch = params.effects.jpegGlitch.copy(seed = value),
                ),
            )

            AnimTarget.CHROMATIC_OFFSET -> params.copy(
                effects = params.effects.copy(
                    chromatic = params.effects.chromatic.copy(maxDisplace = value.coerceIn(0, 50)),
                ),
            )

            AnimTarget.GLOW_DIRECTION -> params.copy(
                effects = params.effects.copy(
                    glow = params.effects.glow.copy(direction = value),
                ),
            )

            AnimTarget.STARS_ANGLE -> params.copy(
                effects = params.effects.copy(
                    stars = params.effects.stars.copy(angle = value),
                ),
            )
        }
}
