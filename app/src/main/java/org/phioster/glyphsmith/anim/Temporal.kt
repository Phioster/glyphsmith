package org.phioster.glyphsmith.anim

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The nine noise patterns.
 *
 * The reference app's temporal variation advertises nine controllable noise patterns but
 * publishes none of their names or maths. These nine are this app's own set, named after
 * what they do rather than after anything of theirs.
 */
enum class TemporalPattern(val label: String) {
    WHITE_NOISE("White Noise"),
    BLUE_NOISE("Blue Noise"),
    VALUE_NOISE("Value Noise"),
    INTERFERENCE("Interference"),
    SCANLINE_ROLL("Scanline Roll"),
    PLASMA("Plasma"),
    VORTEX("Vortex"),
    RIPPLE("Ripple"),
    BANDS("Bands"),
}

@Serializable
data class TemporalParams(
    val enabled: Boolean = false,
    val pattern: TemporalPattern = TemporalPattern.VALUE_NOISE,
    /** Cells per feature — bigger means a coarser, slower-reading texture. */
    val scale: Int = 8,
    /** Whole cycles per loop. Fractions would leave a seam at the loop point. */
    val speed: Int = 1,
    /** 0..100 — how far the pattern pushes the dither threshold. */
    val amount: Int = 40,
    val seed: Int = 1,
    /**
     * Normalised loop position, written per frame by [Animator.paramsAt] and left at 0 for
     * a still. It lives here rather than as an argument so that a frame's parameters remain
     * a single value the whole pipeline can be handed.
     */
    val time: Float = 0f,
) {
    companion object {
        val SCALE_RANGE = 2..64
        val SPEED_RANGE = 1..8
    }
}

/**
 * Animated noise added to the dither threshold.
 *
 * It goes onto the *threshold* rather than onto the image so that it works with every dither
 * mode rather than only the threshold-based ones — the reference app describes its effects
 * as compatible with any of its algorithms, and this is what that has to mean.
 *
 * Every pattern is periodic in [time] with period 1. A loop that does not close is the one
 * failure mode that shows up on every single playthrough, so it is a property of the
 * functions themselves rather than something the caller has to arrange.
 */
object Temporal {

    private const val TAU = 2.0 * PI

    /** Offset in -1..1 for the cell at ([x], [y]) at [TemporalParams.time]. */
    fun offset(params: TemporalParams, x: Int, y: Int): Float {
        if (!params.enabled || params.amount == 0) return 0f
        val scale = params.scale.coerceAtLeast(1).toFloat()
        val t = params.time * params.speed.coerceAtLeast(1)
        return sample(params.pattern, x / scale, y / scale, t, params.seed) *
            (params.amount / 100f)
    }

    @Suppress("CyclomaticComplexMethod")
    fun sample(
        pattern: TemporalPattern,
        u: Float,
        v: Float,
        time: Float,
        seed: Int,
    ): Float = when (pattern) {
        // Fresh hash per whole step of time; between steps it holds, which is what makes it
        // read as a frame-rate rather than as a smear.
        TemporalPattern.WHITE_NOISE ->
            hash(u.toInt(), v.toInt(), wrap(time), seed) * 2f - 1f

        // Blue noise here means white noise with its low frequencies taken out: the average
        // of the neighbourhood is subtracted, which leaves the fine grain and kills the
        // clumping that makes plain white noise look blotchy at low amounts.
        TemporalPattern.BLUE_NOISE -> {
            val centre = hash(u.toInt(), v.toInt(), wrap(time), seed)
            var around = 0f
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    around += hash(u.toInt() + dx, v.toInt() + dy, wrap(time), seed)
                }
            }
            ((centre - around / 8f) * 2f).coerceIn(-1f, 1f)
        }

        // Interpolated lattice noise. The time axis is a circle rather than a line, so the
        // last frame lands back on the first instead of jumping.
        TemporalPattern.VALUE_NOISE -> valueNoise(u, v, time, seed) * 2f - 1f

        // Two sines at slightly different angles: where they agree the pattern is strong,
        // where they fight it cancels, and the beat drifts as time moves.
        TemporalPattern.INTERFERENCE -> {
            val a = sin(TAU * (u + time)).toFloat()
            val b = sin(TAU * (u * 0.92f + v * 0.38f - time)).toFloat()
            ((a + b) / 2f)
        }

        // A band travelling down the image, the way an out-of-sync CRT rolls.
        TemporalPattern.SCANLINE_ROLL -> {
            val phase = frac(v - time)
            (1f - abs(2f * phase - 1f)) * 2f - 1f
        }

        TemporalPattern.PLASMA -> {
            val a = sin(TAU * (u + time)).toFloat()
            val b = sin(TAU * (v - time)).toFloat()
            val c = sin(TAU * ((u + v) * 0.5f + time)).toFloat()
            ((a + b + c) / 3f)
        }

        // Rotation grows with the radius, so the field winds up as it turns.
        TemporalPattern.VORTEX -> {
            val angle = atan2(v.toDouble(), u.toDouble()).toFloat()
            val radius = hypot(u, v)
            sin(TAU * (angle / TAU.toFloat() * 3f + radius * 0.5f + time)).toFloat()
        }

        // Rings running outward from the centre of the pattern space.
        TemporalPattern.RIPPLE -> sin(TAU * (hypot(u, v) - time)).toFloat()

        // Hard-edged bands rather than a gradient — the blockiest of the nine on purpose.
        TemporalPattern.BANDS -> {
            val step = floor(frac(u * 0.5f + time) * 4f)
            step / 1.5f - 1f
        }
    }

    /** Fractional part in 0..1; `x % 1` goes negative on the left half of the plane. */
    private fun frac(value: Float): Float = value - floor(value)

    /** Whole steps of loop time, wrapped, so a hash-driven pattern still closes its loop. */
    private fun wrap(time: Float): Int = floor(frac(time) * 16f).toInt()

    /**
     * Lattice noise interpolated with a smoothstep, and looped in time by walking a circle
     * through the hash rather than a straight line.
     */
    private fun valueNoise(u: Float, v: Float, time: Float, seed: Int): Float {
        val x0 = floor(u).toInt()
        val y0 = floor(v).toInt()
        val fx = smooth(u - x0)
        val fy = smooth(v - y0)
        // Two hashes a half-period apart, cross-faded — the fade is symmetric, so the
        // sequence arrives back where it started.
        val phase = frac(time)
        val slot = floor(phase * 8f).toInt()
        val blend = smooth(frac(phase * 8f))

        fun lattice(step: Int): Float {
            val t = Math.floorMod(step, 8)
            val a = hash(x0, y0, t, seed)
            val b = hash(x0 + 1, y0, t, seed)
            val c = hash(x0, y0 + 1, t, seed)
            val d = hash(x0 + 1, y0 + 1, t, seed)
            return lerp(lerp(a, b, fx), lerp(c, d, fx), fy)
        }

        return lerp(lattice(slot), lattice(slot + 1), blend)
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Deterministic 0..1 hash — the same cell and frame must always give the same value. */
    private fun hash(x: Int, y: Int, t: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + t * 1274126177 + seed * 2147483647
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
