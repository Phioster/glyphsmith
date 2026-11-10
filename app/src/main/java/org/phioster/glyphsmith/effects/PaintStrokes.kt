package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Repaints the picture in brush strokes: rectangular dabs in the picture's own colours that
 * turn to follow edges and stop at them, laid over each other like thick paint.
 */
@Serializable
data class PaintStrokesParams(
    val enabled: Boolean = false,
    /** 1..48 — the brush, in 1/1000 of the picture's shorter side, so preview and export agree. */
    val brushSize: Int = 14,
    /** 0..100 — how hard a stroke's border is. */
    val sharpness: Int = 70,
    /** 1..4 — each further pass repaints with a smaller brush where the picture is still off. */
    val passes: Int = 1,
    /** 0..100 — how much of the painting replaces the picture underneath. */
    val mix: Int = 100,
    /** 10..200 — a stroke's width and height in % of the brush. */
    val brushWidth: Int = 85,
    val brushHeight: Int = 147,
    /** 0..100 — how readily strokes turn along an edge and refuse to cross it. */
    val edgeSensitivity: Int = 59,
    val seed: Int = 1,
)

object PaintStrokes {

    private const val UNIT_DIVISOR = 1000f
    private const val MAX_SMOOTH = 4
    private const val SMOOTH_SHARE = 0.4f
    private const val PASS_SHRINK = 0.5f
    private const val SPACING = 0.55f
    private const val REFINE_AT = 26
    private const val TILT = 0.5f
    private const val SHADE = 0.16f

    val pass = EffectPass(
        EffectStack::paintStrokes,
        { copy(paintStrokes = it) },
        PaintStrokesParams::enabled,
        randomise = { roll ->
            copy(enabled = true, brushSize = roll.random.nextInt(8, 26), seed = roll.random.nextInt(1, 999))
        },
    ) { pixels, params, _ -> apply(pixels, params) }

    fun apply(source: Pixels, params: PaintStrokesParams): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || w < 4 || h < 4) return source
        val unit = max(1.5f, min(w, h) * params.brushSize.coerceIn(1, 48) / UNIT_DIVISOR)
        // Strokes read their colour and their edges from a lightly smoothed copy, so a dither's
        // single dots mix into the tone they stand for instead of each claiming a stroke.
        val smooth = PixelOps.blurRgba(source, (unit * SMOOTH_SHARE).toInt().coerceIn(2, MAX_SMOOTH)).data
        val canvas = smooth.copyOf()

        var size = unit
        for (round in 0 until params.passes.coerceIn(1, 4)) {
            paint(smooth, canvas, w, h, size, round, params)
            size = max(1.5f, size * PASS_SHRINK)
        }

        val mix = params.mix.coerceIn(0, 100) * 256 / 100
        for (i in source.data.indices) {
            source.data[i] = (source.data[i] and 0xFF000000.toInt()) or (blend(source.data[i], canvas[i], mix) and 0xFFFFFF)
        }
        return source
    }

    @Suppress("LongParameterList")
    private fun paint(src: IntArray, canvas: IntArray, w: Int, h: Int, size: Float, round: Int, p: PaintStrokesParams) {
        val bw = max(1f, size * p.brushWidth.coerceIn(10, 200) / 100f)
        val bh = max(1f, size * p.brushHeight.coerceIn(10, 200) / 100f)
        val step = max(1f, min(bw, bh) * SPACING)
        val cols = (w / step).toInt() + 1
        val rows = (h / step).toInt() + 1
        val salt = p.seed * 31 + round
        // A shuffled order, so strokes overlap each other in every direction, not in rows.
        val order = LongArray(cols * rows) { (bits(it, salt).toLong() shl 32) or it.toLong() }
        order.sort()

        val sensitivity = p.edgeSensitivity.coerceIn(0, 100)
        val turnAt = (100 - sensitivity) * 0.9f + 4f
        val tolerance = 30 + (100 - sensitivity) * 22 / 10
        val softBand = 0.04f + (100 - p.sharpness.coerceIn(0, 100)) / 100f * 0.6f
        val reach = (max(1f, size / 2f)).toInt()

        for (entry in order) {
            val index = (entry and 0xFFFFFFFFL).toInt()
            val cx = ((index % cols + unitHash(index, salt + 11)) * step).toInt()
            val cy = ((index / cols + unitHash(index, salt + 23)) * step).toInt()
            if (cx >= w || cy >= h) continue
            val colour = src[cy * w + cx]
            if (round > 0 && distance(canvas[cy * w + cx], colour) < REFINE_AT) continue

            // The stroke's long axis runs along the edge it sits on; without one it leans a little.
            val gx = luma(src, w, h, cx + reach, cy) - luma(src, w, h, cx - reach, cy)
            val gy = luma(src, w, h, cx, cy + reach) - luma(src, w, h, cx, cy - reach)
            val strength = hypot(gx, gy)
            val ux: Float
            val uy: Float
            if (strength >= turnAt) {
                ux = gx / strength
                uy = gy / strength
            } else {
                val lean = (unitHash(index, salt + 37) - 0.5f) * TILT
                ux = cos(lean)
                uy = -sin(lean)
            }
            val shade = 1f + (unitHash(index, salt + 53) - 0.5f) * SHADE
            stroke(src, canvas, w, h, cx, cy, ux, uy, bw / 2f, bh / 2f, softBand, tolerance, shaded(colour, shade))
        }
    }

    @Suppress("LongParameterList")
    private fun stroke(
        src: IntArray, canvas: IntArray, w: Int, h: Int, cx: Int, cy: Int, ux: Float, uy: Float,
        halfW: Float, halfH: Float, softBand: Float, tolerance: Int, colour: Int,
    ) {
        val radius = hypot(halfW, halfH).toInt() + 1
        for (y in max(0, cy - radius)..min(h - 1, cy + radius)) {
            for (x in max(0, cx - radius)..min(w - 1, cx + radius)) {
                val dx = (x - cx).toFloat()
                val dy = (y - cy).toFloat()
                // u runs across the stroke, v along it.
                val edge = max(abs(dx * ux + dy * uy) / halfW, abs(dy * ux - dx * uy) / halfH)
                if (edge >= 1f) continue
                val i = y * w + x
                if (distance(src[i], colour) > tolerance) continue
                val cover = min(1f, (1f - edge) / softBand)
                canvas[i] = blend(canvas[i], colour, (cover * 256f).toInt())
            }
        }
    }

    private fun luma(src: IntArray, w: Int, h: Int, x: Int, y: Int): Float {
        val p = src[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        return 0.2126f * PixelOps.redOf(p) + 0.7152f * PixelOps.greenOf(p) + 0.0722f * PixelOps.blueOf(p)
    }

    /** The largest difference in any one channel, 0..255. */
    private fun distance(a: Int, b: Int): Int = max(
        abs(PixelOps.redOf(a) - PixelOps.redOf(b)),
        max(abs(PixelOps.greenOf(a) - PixelOps.greenOf(b)), abs(PixelOps.blueOf(a) - PixelOps.blueOf(b))),
    )

    /** [over] on [base] at [amount]/256, colour only. */
    private fun blend(base: Int, over: Int, amount: Int): Int {
        fun mix(shift: Int): Int {
            val from = (base shr shift) and 0xFF
            return from + ((((over shr shift) and 0xFF) - from) * amount shr 8)
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun shaded(colour: Int, factor: Float): Int = PixelOps.argb(
        255,
        (PixelOps.redOf(colour) * factor).toInt().coerceIn(0, 255),
        (PixelOps.greenOf(colour) * factor).toInt().coerceIn(0, 255),
        (PixelOps.blueOf(colour) * factor).toInt().coerceIn(0, 255),
    )

    private fun bits(n: Int, seed: Int): Int {
        var x = n * 374761393 + seed * 668265263
        x = (x xor (x shr 13)) * 1274126177
        return (x xor (x shr 16)) and 0x7FFFFFFF
    }

    private fun unitHash(n: Int, seed: Int): Float = bits(n, seed) / 0x7FFFFFFF.toFloat()
}
