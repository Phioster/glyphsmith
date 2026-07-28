package org.phioster.glyphsmith.effects

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/** A loose pixel buffer — the currency every effect in this package trades in. */
class Pixels(val data: IntArray, val width: Int, val height: Int) {

    fun copy(): Pixels = Pixels(data.copyOf(), width, height)

    fun toBitmap(): Bitmap = Bitmap.createBitmap(data, width, height, Bitmap.Config.ARGB_8888)

    companion object {
        fun of(bitmap: Bitmap): Pixels {
            val data = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return Pixels(data, bitmap.width, bitmap.height)
        }
    }
}

/**
 * Shared pixel maths. The rotate-blur-rotate trick and the inverse-power kernel live here
 * because both the glow and the diffraction stars need them; keeping one copy means a fix
 * to the kernel fixes both.
 */
object PixelOps {

    fun alphaOf(pixel: Int): Int = (pixel ushr 24) and 0xFF
    fun redOf(pixel: Int): Int = (pixel shr 16) and 0xFF
    fun greenOf(pixel: Int): Int = (pixel shr 8) and 0xFF
    fun blueOf(pixel: Int): Int = pixel and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or
            (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or
            b.coerceIn(0, 255)

    fun luminance(pixel: Int): Float =
        (0.2126f * redOf(pixel) + 0.7152f * greenOf(pixel) + 0.0722f * blueOf(pixel)) / 255f

    fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Everything below the soft knee goes to black; the rest keeps its colour. */
    fun brightPass(source: Pixels, threshold: Int, smoothing: Int): Pixels {
        val out = IntArray(source.data.size)
        val knee = threshold / 100f
        val soft = (smoothing / 100f).coerceAtLeast(0.001f)
        val low = knee - soft / 2f
        val high = knee + soft / 2f
        for (i in source.data.indices) {
            val pixel = source.data[i]
            val weight = smoothStep(low, high, luminance(pixel))
            out[i] = argb(
                255,
                (redOf(pixel) * weight).toInt(),
                (greenOf(pixel) * weight).toInt(),
                (blueOf(pixel) * weight).toInt(),
            )
        }
        return Pixels(out, source.width, source.height)
    }

    /** 1-D inverse-power kernel: `w(d) = 1 / ((d·scale)ⁿ + ε)`, optionally energy-normalised. */
    fun inversePowerKernel(
        radius: Int,
        falloff: Float,
        epsilon: Float,
        distanceScale: Float,
        normalise: Boolean,
    ): FloatArray {
        val n = falloff.coerceAtLeast(0.01f)
        val eps = epsilon.coerceAtLeast(0.001f)
        val scale = distanceScale.coerceAtLeast(0.01f)
        val weights = FloatArray(radius * 2 + 1)
        var sum = 0f
        for (i in weights.indices) {
            val d = abs(i - radius) * scale
            val w = 1f / (d.toDouble().pow(n.toDouble()).toFloat() + eps)
            weights[i] = w
            sum += w
        }
        val norm = if (normalise) sum else weights[radius] * weights.size
        if (norm > 0f) for (i in weights.indices) weights[i] /= norm
        return weights
    }

    fun convolve(source: Pixels, weights: FloatArray, horizontal: Boolean): Pixels {
        val out = IntArray(source.data.size)
        val radius = weights.size / 2
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in weights.indices) {
                    val offset = k - radius
                    val sx = if (horizontal) (x + offset).coerceIn(0, source.width - 1) else x
                    val sy = if (horizontal) y else (y + offset).coerceIn(0, source.height - 1)
                    val pixel = source.data[sy * source.width + sx]
                    val w = weights[k]
                    r += redOf(pixel) * w
                    g += greenOf(pixel) * w
                    b += blueOf(pixel) * w
                }
                out[y * source.width + x] = argb(255, r.toInt(), g.toInt(), b.toInt())
            }
        }
        return Pixels(out, source.width, source.height)
    }

    /**
     * Blur along an arbitrary axis by rotating the buffer, blurring separably, and rotating
     * back. An exact rotated 2-D convolution is O(r²) per pixel — seconds per frame at glow
     * radii — and at these radii the difference doesn't survive the downscale anyway.
     */
    fun directionalBlur(
        source: Pixels,
        degrees: Float,
        alongRadius: Int,
        acrossRadius: Int,
        kernel: (Int) -> FloatArray,
    ): Pixels {
        val radians = Math.toRadians(degrees.toDouble())
        val rotated = rotate(source, -radians)
        val blurredX = convolve(rotated, kernel(max(1, alongRadius)), true)
        val blurredY = convolve(blurredX, kernel(max(1, acrossRadius)), false)
        return centerCrop(rotate(blurredY, radians), source.width, source.height)
    }

    fun rotate(source: Pixels, radians: Double): Pixels {
        if (radians == 0.0) return source
        val bitmap = source.toBitmap()
        val matrix = Matrix().apply { postRotate(Math.toDegrees(radians).toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        val result = Pixels.of(rotated)
        rotated.recycle()
        return result
    }

    fun centerCrop(source: Pixels, width: Int, height: Int): Pixels {
        if (source.width == width && source.height == height) return source
        val offsetX = (source.width - width) / 2
        val offsetY = (source.height - height) / 2
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val sy = (y + offsetY).coerceIn(0, source.height - 1)
            for (x in 0 until width) {
                val sx = (x + offsetX).coerceIn(0, source.width - 1)
                out[y * width + x] = source.data[sy * source.width + sx]
            }
        }
        return Pixels(out, width, height)
    }

    fun scale(source: Pixels, width: Int, height: Int): Pixels {
        if (source.width == width && source.height == height) return source
        val bitmap = source.toBitmap()
        val scaled = Bitmap.createScaledBitmap(bitmap, max(1, width), max(1, height), true)
        bitmap.recycle()
        val result = Pixels.of(scaled)
        scaled.recycle()
        return result
    }

    /**
     * Adds [light] onto [base] and lifts alpha with it, so a glow still shows over a
     * transparent background instead of being clipped away by the base's zero alpha.
     */
    fun addLight(base: Pixels, light: Pixels, intensity: Float) {
        for (i in base.data.indices) {
            val b = base.data[i]
            val l = light.data[i]
            val r = redOf(l) * intensity
            val g = greenOf(l) * intensity
            val bl = blueOf(l) * intensity
            val alpha = max(alphaOf(b), ((r + g + bl) / 3f).toInt().coerceIn(0, 255))
            base.data[i] = argb(
                alpha,
                (redOf(b) + r).toInt(),
                (greenOf(b) + g).toInt(),
                (blueOf(b) + bl).toInt(),
            )
        }
    }
}
