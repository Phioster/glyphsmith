package org.phioster.glyphsmith.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ColorDistance.rgbOf] has to be the exact inverse of [ColorDistance.coordsOf].
 *
 * It matters more than an inverse usually does. Colour-depth reduction converts into a perceptual
 * space, quantises, and converts back, so any error in the pair shows up as a *colour cast over the
 * whole image* rather than as a wrong pixel somewhere — and a cast is easy to mistake for a
 * deliberate look, which is how it would survive review.
 */
class LabRoundTripTest {

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun channels(color: Int): Triple<Int, Int, Int> = Triple(
        (color shr 16) and 0xFF,
        (color shr 8) and 0xFF,
        color and 0xFF,
    )

    /**
     * One step of tolerance, not zero: the transforms run in Float and a cube root followed by a
     * cube cannot be exact. More than one step would mean the maths is wrong, not merely rounded.
     */
    private val tolerance = 1

    @Test
    fun `every metric round trips the colour cube within one step`() {
        for (metric in ColorDistance.entries) {
            var worst = 0
            var r = 0
            while (r < 256) {
                var g = 0
                while (g < 256) {
                    var b = 0
                    while (b < 256) {
                        val original = rgb(r, g, b)
                        val (rr, gg, bb) = channels(metric.rgbOf(metric.coordsOf(original)))
                        worst = maxOf(worst, maxOf(kotlin.math.abs(rr - r), kotlin.math.abs(gg - g), kotlin.math.abs(bb - b)))
                        b += 17
                    }
                    g += 17
                }
                r += 17
            }
            assertTrue("$metric drifted by $worst channel steps", worst <= tolerance)
        }
    }

    @Test
    fun `the extremes survive exactly`() {
        for (metric in ColorDistance.entries) {
            for (colour in listOf(rgb(0, 0, 0), rgb(255, 255, 255))) {
                assertEquals(
                    "$metric lost an extreme",
                    colour,
                    metric.rgbOf(metric.coordsOf(colour)),
                )
            }
        }
    }

    @Test
    fun `the result is always opaque`() {
        for (metric in ColorDistance.entries) {
            val alpha = (metric.rgbOf(metric.coordsOf(rgb(30, 200, 90))) ushr 24) and 0xFF
            assertEquals("$metric must return an opaque colour", 255, alpha)
        }
    }

    /**
     * L\*a\*b\* and OKLab are both much larger than sRGB, so quantising in them lands outside the
     * gamut routinely. Clamping is the contract; wrapping would put a black pixel in a bright area.
     */
    @Test
    fun `coordinates far outside the gamut clamp instead of wrapping`() {
        val absurd = floatArrayOf(400f, 400f, -400f)
        for (metric in listOf(ColorDistance.CIELAB, ColorDistance.OKLAB)) {
            val (r, g, b) = channels(metric.rgbOf(absurd))
            assertTrue("$metric: red out of range ($r)", r in 0..255)
            assertTrue("$metric: green out of range ($g)", g in 0..255)
            assertTrue("$metric: blue out of range ($b)", b in 0..255)
        }
    }

    @Test
    fun `euclidean is a plain identity`() {
        val colour = rgb(12, 240, 77)
        assertEquals(colour, ColorDistance.EUCLIDEAN.rgbOf(ColorDistance.EUCLIDEAN.coordsOf(colour)))
    }
}
