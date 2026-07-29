package org.phioster.glyphsmith.anim

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.ascii.AsciiParams
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/** A parameter an animation track can drive. */
enum class AnimTarget(val label: String, val min: Int, val max: Int) {
    DEPTH("Depth", 1, AsciiParams.MAX_DEPTH),
    CHARACTER_OFFSET("Character Offset", 0, 64),
    DITHER_STRENGTH("Dither Strength", 0, 100),
    /** A full sweep of a modulation period, so a sawtooth over 0..100 travels seamlessly. */
    MOD_PHASE("Modulation Phase", 0, 100),
    EDGE_THRESHOLD("Edge Threshold", 0, 100),
    GLITCH_SEED("Glitch Seed", 1, 9999),
    CHROMATIC_OFFSET("Chromatic Offset", 0, 50),
    GLOW_DIRECTION("Glow Direction", 0, 359),
    STARS_ANGLE("Stars Angle", 0, 359),
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
) {
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
    fun paramsAt(base: AsciiParams, animation: AnimationParams, frame: Int): AsciiParams {
        if (!animation.enabled) return base
        var params = base
        animation.tracks.filter { it.enabled }.forEach { track ->
            val value = valueAt(track, frame, animation.frames)
            params = apply(params, track.target, value)
        }
        return params
    }

    private fun apply(params: AsciiParams, target: AnimTarget, value: Int): AsciiParams =
        when (target) {
            AnimTarget.DEPTH -> params.copy(depth = value.coerceIn(1, AsciiParams.MAX_DEPTH))
            // The offset wraps anyway, so it is never clamped to the ramp length here.
            AnimTarget.CHARACTER_OFFSET -> params.copy(offset = value)
            AnimTarget.DITHER_STRENGTH -> params.copy(ditherStrength = value.coerceIn(0, 100))
            // Left unclamped: the phase wraps inside the pattern, so a track that runs past
            // 100 simply keeps travelling instead of stalling at the end of its range.
            AnimTarget.MOD_PHASE -> params.copy(modPhase = value)
            AnimTarget.EDGE_THRESHOLD -> params.copy(edgeThreshold = value.coerceIn(0, 100))
            AnimTarget.GLITCH_SEED -> params.copy(
                effects = params.effects.copy(
                    jpegGlitch = params.effects.jpegGlitch.copy(seed = value),
                ),
            )

            AnimTarget.CHROMATIC_OFFSET -> params.copy(
                effects = params.effects.copy(
                    chromatic = params.effects.chromatic.copy(offset = value.coerceIn(0, 50)),
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
