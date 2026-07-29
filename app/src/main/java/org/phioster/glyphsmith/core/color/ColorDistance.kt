package org.phioster.glyphsmith.core.color

import kotlinx.serialization.Serializable
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How far apart two colours are.
 *
 * Kept separate from palette data on purpose: a palette is a list of colours and has no
 * opinion about what "nearest" means, while the metric is the thing that decides whether a
 * mid grey resolves to the blue or the brown entry. Splitting them is what lets the same
 * palette be quantised three different ways.
 *
 * The three differ in where they measure:
 *
 * - [EUCLIDEAN] measures in sRGB as stored. Cheapest, and wrong in a specific way — sRGB is
 *   perceptually non-uniform, so it over-weights differences in the dark end and under-weights
 *   them in green.
 * - [CIELAB] measures ΔE*ab (CIE 76) in L\*a\*b\*, which is roughly perceptually uniform.
 * - [OKLAB] measures in OKLab, which fixes the blue-region and hue-linearity problems L\*a\*b\*
 *   is known for. Usually the best choice for reducing a photograph to a small palette.
 *
 * All three are metrics on the same scale-free footing: only the *ordering* of distances
 * matters to a nearest-colour search, never the absolute value, so the three do not need to
 * agree on units.
 */
@Serializable
enum class ColorDistance {
    EUCLIDEAN,
    CIELAB,
    OKLAB,
    ;

    /**
     * The colour as coordinates in this metric's space, alpha discarded.
     *
     * Returned as a plain [FloatArray] of three so a caller can convert a whole palette once
     * and then compare without re-entering the transforms per pixel — the conversions here are
     * cube roots and powers, far too expensive to repeat inside a pixel loop.
     */
    fun coordsOf(color: Int): FloatArray {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return when (this) {
            EUCLIDEAN -> floatArrayOf(r * 255f, g * 255f, b * 255f)
            CIELAB -> labOf(linear(r), linear(g), linear(b))
            OKLAB -> okLabOf(linear(r), linear(g), linear(b))
        }
    }

    /** Straight-line distance between two colours in this metric's space. */
    fun distance(a: Int, b: Int): Float = distanceBetween(coordsOf(a), coordsOf(b))

    companion object {

        /** Squared distance — what a nearest search should use, since the root changes no ordering. */
        fun squaredBetween(a: FloatArray, b: FloatArray): Float {
            val d0 = a[0] - b[0]
            val d1 = a[1] - b[1]
            val d2 = a[2] - b[2]
            return d0 * d0 + d1 * d1 + d2 * d2
        }

        fun distanceBetween(a: FloatArray, b: FloatArray): Float = sqrt(squaredBetween(a, b))

        /** sRGB transfer function, inverted: gamma-encoded channel to linear light. */
        private fun linear(c: Float): Float =
            if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

        // D65 white point, the one sRGB is defined against.
        private const val XN = 0.95047f
        private const val YN = 1.00000f
        private const val ZN = 1.08883f

        private fun labOf(r: Float, g: Float, b: Float): FloatArray {
            val x = (0.4124564f * r + 0.3575761f * g + 0.1804375f * b) / XN
            val y = (0.2126729f * r + 0.7151522f * g + 0.0721750f * b) / YN
            val z = (0.0193339f * r + 0.1191920f * g + 0.9503041f * b) / ZN
            val fx = pivot(x)
            val fy = pivot(y)
            val fz = pivot(z)
            return floatArrayOf(
                116f * fy - 16f,
                500f * (fx - fy),
                200f * (fy - fz),
            )
        }

        /**
         * The L\*a\*b\* pivot. Below the break the curve is linear, which is what keeps the
         * transform finite-sloped at zero — a bare cube root has infinite slope there and would
         * make near-blacks numerically unstable.
         */
        private fun pivot(t: Float): Float =
            if (t > 0.008856f) cbrt(t.toDouble()).toFloat() else 7.787f * t + 16f / 116f

        private fun okLabOf(r: Float, g: Float, b: Float): FloatArray {
            val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
            val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
            val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b
            val l3 = cbrt(l.toDouble()).toFloat()
            val m3 = cbrt(m.toDouble()).toFloat()
            val s3 = cbrt(s.toDouble()).toFloat()
            return floatArrayOf(
                0.2104542553f * l3 + 0.7936177850f * m3 - 0.0040720468f * s3,
                1.9779984951f * l3 - 2.4285922050f * m3 + 0.4505937099f * s3,
                0.0259040371f * l3 + 0.7827717662f * m3 - 0.8086757660f * s3,
            )
        }
    }
}
