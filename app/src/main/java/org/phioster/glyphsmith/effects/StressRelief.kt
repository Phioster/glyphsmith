package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Wears a picture's colour areas like old paint: the borders between them fray and chip, the
 * areas themselves stay hard and pure, a little grain settles on top and flecks of the colour
 * next door land where it has flaked.
 */
@Serializable
data class StressReliefParams(
    val enabled: Boolean = false,
    /** 0..12 — how far a border is pushed about, in 1/640 of the picture's shorter side. */
    val edgeWear: Int = 4,
    /** 0..100 — smooth dents at 0, jagged chips at 100. */
    val roughness: Int = 53,
    /** 0..40 — brightness noise per pixel. */
    val grain: Int = 3,
    /** 0..100 — how many flecks. */
    val speckles: Int = 30,
    /** 1..12 — a fleck's size, in the same unit as the wear. */
    val speckleSize: Int = 2,
    val seed: Int = 1,
    /** 0..100 — how much of the worn picture replaces the one underneath. */
    val mix: Int = 100,
)

object StressRelief {

    private const val UNIT_DIVISOR = 640f
    private const val DENT = 6f
    private const val CHIP = 1.5f
    private const val FLECK_CELL = 4f
    private const val FLECK_REACH = 3f
    private const val FLECK_SHARE = 0.6f

    val pass = EffectPass(
        EffectStack::stressRelief,
        { copy(stressRelief = it) },
        StressReliefParams::enabled,
        randomise = { roll ->
            copy(enabled = true, edgeWear = roll.random.nextInt(2, 9), seed = roll.random.nextInt(1, 999))
        },
    ) { pixels, params, _ -> apply(pixels, params) }

    fun apply(source: Pixels, params: StressReliefParams): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || w < 4 || h < 4) return source
        val unit = max(1f, min(w, h) / UNIT_DIVISOR)
        val src = source.data.copyOf()
        val out = source.data
        val seed = params.seed
        val mix = params.mix.coerceIn(0, 100) * 256 / 100

        // A border moves because each pixel looks a little to the side for its colour. Inside
        // an area that finds the same colour again, so only the borders show it.
        val wear = params.edgeWear.coerceIn(0, 12) * unit
        val rough = params.roughness.coerceIn(0, 100) / 100f
        val dent = DENT * unit
        val chip = CHIP * unit
        val grain = params.grain.coerceIn(0, 40)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var worn = src[y * w + x]
                if (wear > 0f) {
                    val dx = lerp(smooth(x / dent, y / dent, seed), smooth(x / chip, y / chip, seed + 7), rough)
                    val dy = lerp(smooth(x / dent, y / dent, seed + 13), smooth(x / chip, y / chip, seed + 19), rough)
                    val sx = (x + ((dx - 0.5f) * 2f * wear).toInt()).coerceIn(0, w - 1)
                    val sy = (y + ((dy - 0.5f) * 2f * wear).toInt()).coerceIn(0, h - 1)
                    worn = src[sy * w + sx]
                }
                if (grain > 0) worn = lit(worn, ((unitHash(y * w + x, seed + 31) - 0.5f) * 2f * grain).toInt())
                out[y * w + x] = (src[y * w + x] and 0xFF000000.toInt()) or (blend(src[y * w + x], worn, mix) and 0xFFFFFF)
            }
        }
        flecks(src, out, w, h, unit, params, mix)
        return source
    }

    /** One fleck at most per cell of a coarse grid, in the colour found a few sizes away. */
    @Suppress("LongParameterList")
    private fun flecks(src: IntArray, out: IntArray, w: Int, h: Int, unit: Float, p: StressReliefParams, mix: Int) {
        val share = p.speckles.coerceIn(0, 100) / 100f * FLECK_SHARE
        if (share <= 0f) return
        val radius = max(0.5f, p.speckleSize.coerceIn(1, 12) * unit / 2f)
        val cell = max(2f, radius * FLECK_CELL)
        val cols = (w / cell).toInt() + 1
        val rows = (h / cell).toInt() + 1
        val reach = radius * FLECK_REACH + 1f
        for (index in 0 until cols * rows) {
            if (unitHash(index, p.seed + 41) >= share) continue
            val cx = ((index % cols + unitHash(index, p.seed + 43)) * cell).toInt()
            val cy = ((index / cols + unitHash(index, p.seed + 47)) * cell).toInt()
            if (cx >= w || cy >= h) continue
            val fx = (cx + ((unitHash(index, p.seed + 53) - 0.5f) * 2f * reach).toInt()).coerceIn(0, w - 1)
            val fy = (cy + ((unitHash(index, p.seed + 59) - 0.5f) * 2f * reach).toInt()).coerceIn(0, h - 1)
            val colour = src[fy * w + fx]
            // The same colour as the ground it lands on would be a fleck nobody can see.
            if (colour and 0xFFFFFF == src[cy * w + cx] and 0xFFFFFF) continue
            val size = radius * (0.5f + unitHash(index, p.seed + 61))
            val span = size.toInt() + 1
            for (y in max(0, cy - span)..min(h - 1, cy + span)) {
                for (x in max(0, cx - span)..min(w - 1, cx + span)) {
                    val dx = (x - cx).toFloat()
                    val dy = (y - cy).toFloat()
                    if (dx * dx + dy * dy > size * size) continue
                    val i = y * w + x
                    out[i] = (out[i] and 0xFF000000.toInt()) or (blend(out[i], colour, mix) and 0xFFFFFF)
                }
            }
        }
    }

    /** Value noise, 0..1, smooth between the whole numbers. */
    private fun smooth(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val tx = ease(x - x0)
        val ty = ease(y - y0)
        val top = lerp(corner(x0, y0, seed), corner(x0 + 1, y0, seed), tx)
        val bottom = lerp(corner(x0, y0 + 1, seed), corner(x0 + 1, y0 + 1, seed), tx)
        return lerp(top, bottom, ty)
    }

    private fun corner(x: Int, y: Int, seed: Int): Float = unitHash(x * 73856093 xor y * 19349663, seed)

    private fun ease(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun lit(colour: Int, by: Int): Int = PixelOps.argb(
        255,
        (PixelOps.redOf(colour) + by).coerceIn(0, 255),
        (PixelOps.greenOf(colour) + by).coerceIn(0, 255),
        (PixelOps.blueOf(colour) + by).coerceIn(0, 255),
    )

    /** [over] on [base] at [amount]/256, colour only. */
    private fun blend(base: Int, over: Int, amount: Int): Int {
        fun mix(shift: Int): Int {
            val from = (base shr shift) and 0xFF
            return from + ((((over shr shift) and 0xFF) - from) * amount shr 8)
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun unitHash(n: Int, seed: Int): Float {
        var x = n * 374761393 + seed * 668265263
        x = (x xor (x shr 13)) * 1274126177
        return ((x xor (x shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
