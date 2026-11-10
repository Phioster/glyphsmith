package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Soft spheres of light added to the picture *before* the dither.
 *
 * Because they land on the cell grid rather than on the finished render, the algorithm dithers
 * them like any other light in the picture: they come out as clusters of the pattern itself, not
 * as smooth blobs pasted on top. That is the whole difference from a glow, and why this lives
 * in `render` beside the tone curve rather than in the effect chain.
 *
 * Positions are seeded and kept in 0..1 of the picture, so an orb sits in the same place at any
 * cell size and on any export resolution.
 */
@Serializable
data class LightOrbsParams(
    val enabled: Boolean = false,
    /** How many orbs, 1..200. */
    val count: Int = 40,
    /** 1..100 — the radius, in tenths of a cell plus half a cell. */
    val size: Int = 30,
    /** 0..100 — how much light an orb adds at its centre. */
    val intensity: Int = 60,
    /** 0..100 — how much each orb's size and brightness vary. */
    val random: Int = 30,
    /** -100..100 — how far all orbs have drifted along [direction], in % of the picture. */
    val drift: Int = 0,
    /** 0..359 degrees. */
    val direction: Int = 270,
    val seed: Int = 1,
)

object LightOrbs {

    /** Adds the orbs' light to the luminance grid, and to the colours when there are any. */
    @Suppress("LongParameterList")
    fun apply(params: LightOrbsParams, cols: Int, rows: Int, luma: FloatArray, colors: IntArray?) {
        if (!params.enabled || params.intensity <= 0 || params.count <= 0) return
        val light = field(params, cols, rows)
        for (i in luma.indices) {
            val add = light[i]
            if (add <= 0f) continue
            luma[i] = min(1f, luma[i] + add)
            colors?.let { it[i] = brighten(it[i], add) }
        }
    }

    /** The added light per cell, 0..1. Pure, so it can be tested on its own. */
    fun field(params: LightOrbsParams, cols: Int, rows: Int): FloatArray {
        val out = FloatArray(cols * rows)
        val strength = params.intensity.coerceIn(0, 100) / 100f
        val jitter = params.random.coerceIn(0, 100) / 100f
        val radians = params.direction * PI / 180.0
        // The way is snapped to steps of half a picture per axis, so a full drift sweep (-100..100,
        // two units) always lands back where it began and the drift loops without a seam.
        val c = cos(radians)
        val s = sin(radians)
        val m = max(abs(c), abs(s))
        val shiftX = (round(c / m * 2.0) / 2.0 * params.drift / 100.0).toFloat()
        val shiftY = (round(s / m * 2.0) / 2.0 * params.drift / 100.0).toFloat()

        for (n in 0 until params.count.coerceIn(1, 200)) {
            val u = frac(hash(n, 0, params.seed) + shiftX)
            val v = frac(hash(n, 1, params.seed) + shiftY)
            val sizeVar = 1f + (hash(n, 2, params.seed) - 0.5f) * jitter
            val glowVar = 1f - hash(n, 3, params.seed) * jitter * 0.7f
            val radius = (0.5f + params.size.coerceIn(1, 100) / 10f) * sizeVar
            stamp(out, cols, rows, u * cols, v * rows, radius, strength * glowVar)
        }
        return out
    }

    @Suppress("LongParameterList")
    private fun stamp(out: FloatArray, cols: Int, rows: Int, cx: Float, cy: Float, radius: Float, peak: Float) {
        val reach = radius * 2.5f
        val x0 = max(0, floor(cx - reach).toInt())
        val x1 = min(cols - 1, floor(cx + reach).toInt())
        val y0 = max(0, floor(cy - reach).toInt())
        val y1 = min(rows - 1, floor(cy + reach).toInt())
        val twoSigma2 = 2f * radius * radius
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x + 0.5f - cx
                val dy = y + 0.5f - cy
                val i = y * cols + x
                // Light adds, but never past white; overlapping orbs merge into one bright patch.
                out[i] = min(1f, out[i] + peak * exp(-(dx * dx + dy * dy) / twoSigma2))
            }
        }
    }

    private fun brighten(argb: Int, add: Float): Int {
        val a = argb ushr 24
        val r = channel((argb shr 16) and 0xFF, add)
        val g = channel((argb shr 8) and 0xFF, add)
        val b = channel(argb and 0xFF, add)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun channel(value: Int, add: Float): Int = min(255, value + (add * 255f).toInt())

    private fun frac(value: Float): Float = value - floor(value)

    private fun hash(n: Int, salt: Int, seed: Int): Float {
        var h = n * 374761393 + salt * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
