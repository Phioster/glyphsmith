package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Bright runs travelling through the picture along one direction, lighting up what is already
 * drawn there — on glyph art the characters flare as a run passes and stay put, on a dither
 * the dots do. Nothing new is painted: only lit pixels get brighter, so the background stays
 * dark and the pattern underneath decides where the light shows.
 */
@Serializable
data class NeoGlowParams(
    val enabled: Boolean = false,
    /** 0..100 — where the runs are on their way; one full sweep loops seamlessly. */
    val offset: Int = 0,
    /** 1..100 — how long a run's tail is, in % of the picture along the direction. */
    val length: Int = 30,
    /** 0..100 — how far apart in time neighbouring lanes start. */
    val stagger: Int = 70,
    /** 0..400 — extra light at a run's head, where 100 doubles a pixel. */
    val intensity: Int = 200,
    /** 0..100 — how much of the picture outside the runs stays visible. */
    val rest: Int = 60,
    /** 1..64 — width of a lane in pixels; match it to the cell size for whole characters. */
    val lane: Int = 8,
    /** 0..359 — the way the runs travel; 90 is downwards. */
    val direction: Int = 90,
    /** The light's colour, or 0 to brighten each pixel in its own colour. */
    val color: Int = 0,
    val seed: Int = 1,
)

object NeoGlow {

    val pass = EffectPass(
        EffectStack::neoGlow,
        { copy(neoGlow = it) },
        NeoGlowParams::enabled,
        randomise = { roll ->
            copy(
                enabled = true,
                length = roll.random.nextInt(15, 60),
                stagger = roll.random.nextInt(30, 100),
                seed = roll.random.nextInt(1, 999),
            )
        },
    ) { pixels, params, _ -> apply(pixels, params) }

    fun apply(source: Pixels, params: NeoGlowParams): Pixels {
        if (!params.enabled) return source
        val radians = params.direction * PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        // Lanes run across the direction; the extent along it is what a run travels through.
        val along = abs(source.width * c) + abs(source.height * s)
        val lane = params.lane.coerceIn(1, 64).toFloat()
        val tail = (params.length.coerceIn(1, 100) / 100f) * along
        val boost = params.intensity.coerceIn(0, 400) / 100f
        val rest = params.rest.coerceIn(0, 100) / 100f
        val phase = Math.floorMod(params.offset, 100) / 100f      // 100 is the start again, exactly
        val cx = source.width / 2f
        val cy = source.height / 2f

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val dx = x - cx
                val dy = y - cy
                val t = dx * c + dy * s + along / 2f            // 0..along, the way the runs go
                val laneIndex = floor((-dx * s + dy * c) / lane).toInt()
                val glow = runAt(t, laneIndex, along, tail, phase, params)
                val index = y * source.width + x
                source.data[index] = light(source.data[index], rest + (1f - rest) * glow, boost * glow, params.color)
            }
        }
        return source
    }

    /** 0..1: 1 at the head of this lane's run, falling to 0 at the end of its tail. */
    @Suppress("LongParameterList")
    internal fun runAt(t: Float, lane: Int, along: Float, tail: Float, phase: Float, params: NeoGlowParams): Float {
        val start = hash(lane, params.seed) * params.stagger.coerceIn(0, 100) / 100f
        val loop = along + tail
        val head = frac(phase + start) * loop                 // travels 0..loop once per sweep
        val behind = head - t
        if (behind < 0f || behind > tail) return 0f
        return 1f - behind / tail
    }

    private fun light(pixel: Int, keep: Float, add: Float, color: Int): Int {
        val r = PixelOps.redOf(pixel)
        val g = PixelOps.greenOf(pixel)
        val b = PixelOps.blueOf(pixel)
        // Light only lands where something is drawn: a pixel's own brightness gates it.
        val lit = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        val gate = add * lit
        val tr = if (color == 0) r else PixelOps.redOf(color)
        val tg = if (color == 0) g else PixelOps.greenOf(color)
        val tb = if (color == 0) b else PixelOps.blueOf(color)
        return PixelOps.argb(
            PixelOps.alphaOf(pixel),
            (r * keep + tr * gate).toInt(),
            (g * keep + tg * gate).toInt(),
            (b * keep + tb * gate).toInt(),
        )
    }

    private fun frac(value: Float): Float = value - floor(value)

    private fun hash(n: Int, seed: Int): Float {
        var h = n * 374761393 + seed * 668265263
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
