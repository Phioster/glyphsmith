package org.phioster.glyphsmith.effects

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Controls for the directional bloom, named and ranged after Script Slayer's Epsilon Glow
 * panel so the numbers mean the same thing in both places.
 */
@Serializable
data class GlowParams(
    val enabled: Boolean = false,
    /** 0..100 — luminance above which a pixel contributes to the glow. */
    val threshold: Int = 14,
    /** 0..100 — width of the soft edge around [threshold]. */
    val thresholdSmoothing: Int = 25,
    /** 0..200 — glow reach, in pixels of the rendered image. */
    val radius: Int = 200,
    /** Normalises the kernel so changing [radius] doesn't change apparent brightness. */
    val radiusCompensation: Boolean = true,
    /** 0..1000, where 500 is 1×. */
    val intensity: Int = 500,
    /** 0..400, where 100 is circular; higher stretches along [direction]. */
    val aspectRatio: Int = 100,
    /** 0..359 degrees. */
    val direction: Int = 0,
    /** 0..50, where 10 is n = 1.0 — the exponent in the 1/(dⁿ+ε) falloff. */
    val falloff: Int = 10,
    /** 0..100 — the ε that keeps the falloff finite at distance 0. */
    val epsilon: Int = 50,
    /** 0..500, where 150 is 1× — scales distance before the falloff is evaluated. */
    val distanceScale: Int = 150,
)

/**
 * Threshold → blur → add, with an anisotropic inverse-power kernel.
 *
 * The blur runs on a downscaled buffer and along rotated axes (rotate, blur separably,
 * rotate back). That's an approximation of a true rotated 2-D convolution, but a real one
 * at radius 200 is O(r²) per pixel — seconds per frame on a phone — and at glow radii the
 * difference is invisible.
 */
object EpsilonGlow {

    /** Longest edge of the buffer the blur actually runs on. */
    private const val WORK_MAX_SIDE = 360

    fun apply(source: Bitmap, params: GlowParams): Bitmap {
        if (!params.enabled || params.intensity <= 0 || params.radius <= 0) return source

        val scale = min(1f, WORK_MAX_SIDE.toFloat() / max(source.width, source.height))
        val workW = max(1, (source.width * scale).roundToInt())
        val workH = max(1, (source.height * scale).roundToInt())

        val small = Bitmap.createScaledBitmap(source, workW, workH, true)
        val bright = extractBright(small, params)
        if (small != source) small.recycle()

        val blurred = blurDirectional(bright, workW, workH, params, scale)
        val glow = Bitmap.createBitmap(blurred, workW, workH, Bitmap.Config.ARGB_8888)
        val upscaled = Bitmap.createScaledBitmap(glow, source.width, source.height, true)
        glow.recycle()

        val result = composite(source, upscaled, params.intensity / 500f)
        upscaled.recycle()
        return result
    }

    /** Soft-thresholded copy of the image: everything below the knee goes to black. */
    private fun extractBright(bitmap: Bitmap, params: GlowParams): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val knee = params.threshold / 100f
        val soft = (params.thresholdSmoothing / 100f).coerceAtLeast(0.001f)
        val low = knee - soft / 2f
        val high = knee + soft / 2f
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            val weight = smoothStep(low, high, luma)
            pixels[i] = (0xFF shl 24) or
                ((r * weight).toInt().coerceIn(0, 255) shl 16) or
                ((g * weight).toInt().coerceIn(0, 255) shl 8) or
                (b * weight).toInt().coerceIn(0, 255)
        }
        return pixels
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun blurDirectional(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: GlowParams,
        scale: Float,
    ): IntArray {
        val radius = max(1, (params.radius * scale).roundToInt())
        val alongRadius = max(1, (radius * (params.aspectRatio / 100f)).roundToInt())

        val angle = Math.toRadians(params.direction.toDouble())
        // Rotate the buffer so the anisotropic axes line up with x/y, blur separably, rotate back.
        val rotated = rotate(pixels, width, height, -angle)
        val blurredX = convolve(rotated.pixels, rotated.width, rotated.height, kernel(alongRadius, params), true)
        val blurredY = convolve(blurredX, rotated.width, rotated.height, kernel(radius, params), false)
        val back = rotate(blurredY, rotated.width, rotated.height, angle)
        return centerCrop(back, width, height)
    }

    /** 1-D inverse-power kernel: w(d) = 1 / ((d·s)ⁿ + ε). */
    private fun kernel(radius: Int, params: GlowParams): FloatArray {
        val n = (params.falloff / 10f).coerceAtLeast(0.01f)
        val epsilon = (params.epsilon / 100f).coerceAtLeast(0.001f)
        val distanceScale = (params.distanceScale / 150f).coerceAtLeast(0.01f)
        val weights = FloatArray(radius * 2 + 1)
        var sum = 0f
        for (i in weights.indices) {
            val d = abs(i - radius) * distanceScale
            val w = 1f / (d.toDouble().pow(n.toDouble()).toFloat() + epsilon)
            weights[i] = w
            sum += w
        }
        // Radius compensation = constant kernel energy, so radius changes reach, not brightness.
        val norm = if (params.radiusCompensation) sum else weights[radius] * (radius * 2 + 1)
        if (norm > 0f) for (i in weights.indices) weights[i] /= norm
        return weights
    }

    private fun convolve(
        pixels: IntArray,
        width: Int,
        height: Int,
        weights: FloatArray,
        horizontal: Boolean,
    ): IntArray {
        val out = IntArray(pixels.size)
        val radius = weights.size / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in weights.indices) {
                    val offset = k - radius
                    val sx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    val pixel = pixels[sy * width + sx]
                    val w = weights[k]
                    r += ((pixel shr 16) and 0xFF) * w
                    g += ((pixel shr 8) and 0xFF) * w
                    b += (pixel and 0xFF) * w
                }
                out[y * width + x] = (0xFF shl 24) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
            }
        }
        return out
    }

    private class Rotated(val pixels: IntArray, val width: Int, val height: Int)

    private fun rotate(pixels: IntArray, width: Int, height: Int, radians: Double): Rotated {
        if (radians == 0.0) return Rotated(pixels, width, height)
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply { postRotate(Math.toDegrees(radians).toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        bitmap.recycle()
        val out = IntArray(rotated.width * rotated.height)
        rotated.getPixels(out, 0, rotated.width, 0, 0, rotated.width, rotated.height)
        val result = Rotated(out, rotated.width, rotated.height)
        rotated.recycle()
        return result
    }

    private fun centerCrop(source: Rotated, width: Int, height: Int): IntArray {
        if (source.width == width && source.height == height) return source.pixels
        val offsetX = (source.width - width) / 2
        val offsetY = (source.height - height) / 2
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val sy = (y + offsetY).coerceIn(0, source.height - 1)
            for (x in 0 until width) {
                val sx = (x + offsetX).coerceIn(0, source.width - 1)
                out[y * width + x] = source.pixels[sy * source.width + sx]
            }
        }
        return out
    }

    /** Additive: glow lifts the base image, and lifts alpha too so it survives a transparent background. */
    private fun composite(base: Bitmap, glow: Bitmap, intensity: Float): Bitmap {
        val width = base.width
        val height = base.height
        val basePixels = IntArray(width * height)
        val glowPixels = IntArray(width * height)
        base.getPixels(basePixels, 0, width, 0, 0, width, height)
        glow.getPixels(glowPixels, 0, width, 0, 0, width, height)

        for (i in basePixels.indices) {
            val b = basePixels[i]
            val g = glowPixels[i]
            val gr = (((g shr 16) and 0xFF) * intensity)
            val gg = (((g shr 8) and 0xFF) * intensity)
            val gb = ((g and 0xFF) * intensity)
            val alpha = (b ushr 24) and 0xFF
            val addedAlpha = max(alpha, ((gr + gg + gb) / 3f).toInt().coerceIn(0, 255))
            basePixels[i] = (addedAlpha shl 24) or
                ((((b shr 16) and 0xFF) + gr).toInt().coerceIn(0, 255) shl 16) or
                ((((b shr 8) and 0xFF) + gg).toInt().coerceIn(0, 255) shl 8) or
                (((b and 0xFF) + gb).toInt().coerceIn(0, 255))
        }
        return Bitmap.createBitmap(basePixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
