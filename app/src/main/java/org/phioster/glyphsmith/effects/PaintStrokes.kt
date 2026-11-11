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
 * Repaints the picture in brush strokes: rectangular dabs that turn to follow edges and stop
 * at them, laid over each other like thick paint. Each dab is mixed half from the one colour
 * under its centre and half from the tone around it, so over a dither neighbouring dabs lean
 * towards different palette colours and mix by eye.
 */
@Serializable
data class PaintStrokesParams(
    val enabled: Boolean = false,
    /** 1..48 — the brush, in 1/640 of the picture's shorter side, so preview and export agree. */
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

    private const val UNIT_DIVISOR = 640f
    private const val MIN_UNIT = 2f
    private const val MAX_SMOOTH = 4
    private const val SMOOTH_SHARE = 0.3f
    private const val PASS_SHRINK = 0.5f
    private const val SPACING = 0.6f
    private const val DETAIL_AT = 14f
    private const val TILT = 0.5f
    private const val SHADE = 0.12f
    private const val BRISTLE = 0.1f
    private const val BRISTLES = 5f
    private const val PURITY = 128

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
        val unit = max(MIN_UNIT, min(w, h) * params.brushSize.coerceIn(1, 48) / UNIT_DIVISOR)
        // Edges are read from a smoothed copy, where a dither's dots have mixed into the tone
        // they stand for.
        val smooth = PixelOps.blurRgba(source, (unit * SMOOTH_SHARE).toInt().coerceIn(2, MAX_SMOOTH)).data
        val canvas = source.data.copyOf()

        var size = unit
        for (round in 0 until params.passes.coerceIn(1, 4)) {
            paint(Layers(source.data, smooth, canvas, w, h), size, round, params)
            size = max(MIN_UNIT, size * PASS_SHRINK)
        }

        val mix = params.mix.coerceIn(0, 100) * 256 / 100
        for (i in source.data.indices) {
            source.data[i] = (source.data[i] and 0xFF000000.toInt()) or (blend(source.data[i], canvas[i], mix) and 0xFFFFFF)
        }
        return source
    }

    private class Layers(val src: IntArray, val smooth: IntArray, val canvas: IntArray, val w: Int, val h: Int)

    private class Dab(
        val cx: Int, val cy: Int, val ux: Float, val uy: Float, val halfW: Float, val halfH: Float,
        val colour: Int, val key: Int,
    )

    private fun paint(l: Layers, size: Float, round: Int, p: PaintStrokesParams) {
        val w = l.w
        val h = l.h
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

            // The stroke's long axis runs along the edge it sits on; without one it leans a little.
            val gx = luma(l.smooth, w, h, cx + reach, cy) - luma(l.smooth, w, h, cx - reach, cy)
            val gy = luma(l.smooth, w, h, cx, cy + reach) - luma(l.smooth, w, h, cx, cy - reach)
            val strength = hypot(gx, gy)
            // Later passes only go back over the places that hold detail.
            if (round > 0 && strength < DETAIL_AT) continue
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
            val colour = shaded(blend(l.smooth[cy * w + cx], l.src[cy * w + cx], PURITY), shade)
            stroke(l, Dab(cx, cy, ux, uy, bw / 2f, bh / 2f, colour, index + salt), softBand, tolerance)
        }
    }

    private fun stroke(l: Layers, d: Dab, softBand: Float, tolerance: Int) {
        val w = l.w
        val radius = hypot(d.halfW, d.halfH).toInt() + 1
        val here = l.smooth[d.cy * w + d.cx]
        val bristle = max(1f, d.halfW * 2f / BRISTLES)
        for (y in max(0, d.cy - radius)..min(l.h - 1, d.cy + radius)) {
            for (x in max(0, d.cx - radius)..min(w - 1, d.cx + radius)) {
                val dx = (x - d.cx).toFloat()
                val dy = (y - d.cy).toFloat()
                // u runs across the stroke, v along it.
                val u = dx * d.ux + dy * d.uy
                val edge = max(abs(u) / d.halfW, abs(dy * d.ux - dx * d.uy) / d.halfH)
                if (edge >= 1f) continue
                val i = y * w + x
                // The stroke ends where the picture stops looking like the place it started.
                if (distance(l.smooth[i], here) > tolerance) continue
                // Each bristle leaves its own streak down the length of the stroke.
                val streak = 1f + (unitHash(((u + d.halfW) / bristle).toInt(), d.key) - 0.5f) * BRISTLE
                val cover = min(1f, (1f - edge) / softBand)
                l.canvas[i] = blend(l.canvas[i], shaded(d.colour, streak), (cover * 256f).toInt())
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
