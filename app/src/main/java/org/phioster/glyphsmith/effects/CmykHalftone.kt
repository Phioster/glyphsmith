package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A four-colour halftone screen, the way process printing does it.
 *
 * Added to Dither Boy in 6.0. The three controls it is documented as having — screen angle,
 * black ink, and mid-tone gain — are the three here, because they are the three that matter:
 * the angles decide whether the screens moiré, the black ink decides how much of the neutral
 * tone is carried by K instead of by all three chromatic inks at once, and the gain is where
 * the press's dot spread gets compensated.
 *
 * The per-ink angle offsets are the classic set (yellow 0°, cyan 15°, black 45°, magenta
 * 75°). Thirty degrees between the strong inks is what keeps their rosette from collapsing
 * into a visible pattern, and yellow sits on the shared axis because it is the one nobody
 * sees when it goes wrong.
 */
@Serializable
data class CmykHalftoneParams(
    val enabled: Boolean = false,
    /** Screen ruling as pixels per dot — smaller is a finer screen. */
    val frequency: Int = 6,
    /** Rotates all four screens together, keeping their relative offsets. */
    val angle: Int = 0,
    /**
     * 0..100 — how much of the grey component is pulled out of CMY and given to K. At 0 the
     * neutrals are printed by all three chromatic inks, which is muddy and the reason grey
     * component replacement exists.
     */
    val blackInk: Int = 80,
    /** 0..200, where 100 is linear — lifts or crushes the mid-tones before screening. */
    val midtoneGain: Int = 100,
    /** 0..100 — how hard the dot edge is. Low is a soft, photographic dot. */
    val sharpness: Int = 70,
)

object CmykHalftone {

    /** Yellow, cyan, black, magenta — the classic separation angles, in degrees. */
    private val INK_ANGLES = floatArrayOf(0f, 15f, 45f, 75f)

    private const val CYAN = 0
    private const val MAGENTA = 1
    private const val YELLOW = 2
    private const val BLACK = 3

    fun apply(source: Pixels, params: CmykHalftoneParams): Pixels {
        if (!params.enabled) return source

        val period = params.frequency.coerceAtLeast(2).toFloat()
        val gain = gammaFor(params.midtoneGain)
        val gcr = (params.blackInk / 100f).coerceIn(0f, 1f)
        val edge = edgeWidth(params.sharpness)

        // Per-ink rotation, precomputed: four sin/cos pairs beat two trig calls per pixel
        // per ink, which at a few million pixels is the whole cost of the effect.
        val cosines = FloatArray(4)
        val sines = FloatArray(4)
        for (ink in 0 until 4) {
            val radians = (INK_ANGLES[angleIndex(ink)] + params.angle) * PI / 180.0
            cosines[ink] = cos(radians).toFloat()
            sines[ink] = sin(radians).toFloat()
        }

        val coverage = FloatArray(4)

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val index = y * source.width + x
                val pixel = source.data[index]
                separate(pixel, gcr, gain, coverage)

                var r = 1f
                var g = 1f
                var b = 1f
                for (ink in 0 until 4) {
                    val amount = coverage[ink]
                    if (amount <= 0f) continue
                    val dot = dotCoverage(x, y, cosines[ink], sines[ink], period, amount, edge)
                    if (dot <= 0f) continue
                    // Subtractive: each ink removes light from the channels it absorbs.
                    when (ink) {
                        CYAN -> r *= 1f - dot
                        MAGENTA -> g *= 1f - dot
                        YELLOW -> b *= 1f - dot
                        else -> {
                            r *= 1f - dot
                            g *= 1f - dot
                            b *= 1f - dot
                        }
                    }
                }

                source.data[index] = PixelOps.argb(
                    PixelOps.alphaOf(pixel),
                    (r * 255f).toInt().coerceIn(0, 255),
                    (g * 255f).toInt().coerceIn(0, 255),
                    (b * 255f).toInt().coerceIn(0, 255),
                )
            }
        }
        return source
    }

    /** Maps ink index (C, M, Y, K) onto its entry in [INK_ANGLES], which is ordered by angle. */
    private fun angleIndex(ink: Int): Int = when (ink) {
        CYAN -> 1
        MAGENTA -> 3
        YELLOW -> 0
        else -> 2
    }

    /**
     * RGB to CMYK with grey component replacement.
     *
     * Without the replacement step a neutral grey is printed as equal parts cyan, magenta
     * and yellow, which on paper turns muddy brown and on screen just looks dirty. Pulling
     * the common minimum out into black is what keeps neutrals neutral.
     */
    private fun separate(pixel: Int, gcr: Float, gain: Float, out: FloatArray) {
        val c = 1f - PixelOps.redOf(pixel) / 255f
        val m = 1f - PixelOps.greenOf(pixel) / 255f
        val y = 1f - PixelOps.blueOf(pixel) / 255f
        val k = minOf(c, m, y) * gcr

        out[CYAN] = tone(c - k, gain)
        out[MAGENTA] = tone(m - k, gain)
        out[YELLOW] = tone(y - k, gain)
        out[BLACK] = tone(k, gain)
    }

    private fun tone(value: Float, gain: Float): Float =
        value.coerceIn(0f, 1f).pow(gain)

    /**
     * Mid-tone gain as an exponent, and 100 is linear.
     *
     * Inverted on purpose: coverage is raised to this power, so a *smaller* exponent means
     * *more* ink. Turning the gain up therefore darkens the mid-tones, which is the
     * direction the name means on a press — dot gain is ink spreading, not ink shrinking.
     */
    private fun gammaFor(midtoneGain: Int): Float =
        100f / midtoneGain.coerceIn(1, 200).toFloat()

    /** Sharpness as the width of the dot's soft edge, in units of the dot radius. */
    private fun edgeWidth(sharpness: Int): Float =
        (1f - sharpness.coerceIn(0, 100) / 100f) * 0.5f + 0.02f

    /**
     * How much of this pixel the ink's dot covers, 0..1.
     *
     * The dot is a disc centred in each screen cell whose radius follows the requested
     * coverage. Measuring against the *area* — hence the square root — is what makes 50%
     * coverage actually print as half the paper rather than as a dot half the width, which
     * would only be a quarter of the area.
     */
    @Suppress("LongParameterList")
    private fun dotCoverage(
        x: Int,
        y: Int,
        cosine: Float,
        sine: Float,
        period: Float,
        amount: Float,
        edge: Float,
    ): Float {
        val u = (x * cosine + y * sine) / period
        val v = (-x * sine + y * cosine) / period
        val dx = u - floor(u) - 0.5f
        val dy = v - floor(v) - 0.5f
        // Normalised so a radius of 1 fills the cell corner to corner.
        val distance = hypot(dx, dy) / 0.70710678f
        val radius = sqrt(amount.coerceIn(0f, 1f))
        // Inside the disc is *covered*, so the ramp has to be inverted. Handing smoothStep
        // its edges the other way round would not do it: it treats edge1 <= edge0 as a hard
        // step, and a hard step the wrong way at that.
        return 1f - PixelOps.smoothStep(radius - edge, radius + edge, distance)
    }
}
