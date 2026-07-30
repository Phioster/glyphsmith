package org.phioster.glyphsmith.core.color

import kotlinx.serialization.Serializable
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
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

    /**
     * The opaque colour a set of coordinates stands for — the exact inverse of [coordsOf].
     *
     * Needed by anything that wants to *modify* a colour in a perceptual space rather than merely
     * compare two: quantising lightness, for instance, has to come back out again. Comparison
     * alone never needed a way back, which is why this arrived later than [coordsOf].
     *
     * Coordinates outside the sRGB gamut — easy to produce, since L\*a\*b\* and OKLab are both far
     * larger than sRGB — are clamped per channel. Clamping distorts such a colour, but the
     * alternative is a channel wrapping from bright to black, which reads as a hole in the image.
     */
    fun rgbOf(coords: FloatArray): Int = when (this) {
        EUCLIDEAN -> pack(coords[0], coords[1], coords[2])
        CIELAB -> fromLab(coords)
        OKLAB -> fromOkLab(coords)
    }

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

        /** sRGB transfer function, forward: linear light back to a gamma-encoded channel. */
        private fun encoded(c: Float): Float =
            if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f

        /**
         * [pivot] undone. The branch is on the *pivoted* value rather than on the original, since
         * that is all we have coming back; `6/29` is the pivoted form of the `0.008856` break, so
         * the two functions change branch at exactly the same colour.
         */
        private fun unpivot(f: Float): Float =
            if (f > 6f / 29f) f * f * f else (f - 16f / 116f) / 7.787f

        private fun fromLab(lab: FloatArray): Int {
            val fy = (lab[0] + 16f) / 116f
            val fx = fy + lab[1] / 500f
            val fz = fy - lab[2] / 200f
            val x = unpivot(fx) * XN
            val y = unpivot(fy) * YN
            val z = unpivot(fz) * ZN
            return fromLinear(
                3.2404542f * x - 1.5371385f * y - 0.4985314f * z,
                -0.9692660f * x + 1.8760108f * y + 0.0415560f * z,
                0.0556434f * x - 0.2040259f * y + 1.0572252f * z,
            )
        }

        private fun fromOkLab(lab: FloatArray): Int {
            val l3 = lab[0] + 0.3963377774f * lab[1] + 0.2158037573f * lab[2]
            val m3 = lab[0] - 0.1055613458f * lab[1] - 0.0638541728f * lab[2]
            val s3 = lab[0] - 0.0894841775f * lab[1] - 1.2914855480f * lab[2]
            val l = l3 * l3 * l3
            val m = m3 * m3 * m3
            val s = s3 * s3 * s3
            return fromLinear(
                4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
                -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
                -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
            )
        }

        /** Linear light to a packed opaque colour, gamma-encoded and clamped into gamut. */
        private fun fromLinear(r: Float, g: Float, b: Float): Int = pack(
            encoded(r.coerceIn(0f, 1f)) * 255f,
            encoded(g.coerceIn(0f, 1f)) * 255f,
            encoded(b.coerceIn(0f, 1f)) * 255f,
        )

        private fun pack(r: Float, g: Float, b: Float): Int =
            (0xFF shl 24) or
                (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or
                b.roundToInt().coerceIn(0, 255)
    }
}
