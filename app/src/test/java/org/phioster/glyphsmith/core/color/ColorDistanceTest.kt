package org.phioster.glyphsmith.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorDistanceTest {

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private val black = rgb(0, 0, 0)
    private val white = rgb(255, 255, 255)
    private val red = rgb(255, 0, 0)
    private val blue = rgb(0, 0, 255)

    @Test
    fun `a colour is zero distance from itself in every metric`() {
        for (metric in ColorDistance.entries) {
            assertEquals(metric.name, 0f, metric.distance(red, red), 1e-4f)
        }
    }

    @Test
    fun `distance is symmetric in every metric`() {
        for (metric in ColorDistance.entries) {
            assertEquals(
                metric.name,
                metric.distance(red, blue),
                metric.distance(blue, red),
                1e-4f,
            )
        }
    }

    @Test
    fun `black to white is the longest step on the grey axis`() {
        for (metric in ColorDistance.entries) {
            val full = metric.distance(black, white)
            val half = metric.distance(black, rgb(128, 128, 128))
            assertTrue("$metric: half a ramp must be shorter than all of it", half < full)
        }
    }

    /** L* runs 0..100 and both chroma axes are zero for a neutral, which pins the whole scale. */
    @Test
    fun `cielab puts white at lightness one hundred and no chroma`() {
        val lab = ColorDistance.CIELAB.coordsOf(white)

        assertEquals(100f, lab[0], 0.1f)
        assertEquals(0f, lab[1], 0.1f)
        assertEquals(0f, lab[2], 0.1f)
    }

    /** OKLab is normalised so that white sits at L = 1 rather than 100. */
    @Test
    fun `oklab puts white at lightness one and no chroma`() {
        val ok = ColorDistance.OKLAB.coordsOf(white)

        assertEquals(1f, ok[0], 0.01f)
        assertEquals(0f, ok[1], 0.01f)
        assertEquals(0f, ok[2], 0.01f)
    }

    @Test
    fun `both perceptual metrics put black at the origin`() {
        for (metric in listOf(ColorDistance.CIELAB, ColorDistance.OKLAB)) {
            val coords = metric.coordsOf(black)
            assertEquals(metric.name, 0f, coords[0], 0.01f)
            assertEquals(metric.name, 0f, coords[1], 0.01f)
            assertEquals(metric.name, 0f, coords[2], 0.01f)
        }
    }

    /**
     * The point of offering three: they are not the same metric with different units.
     *
     * Stated as a ratio rather than as an ordering, because an ordering only differs for
     * particular colour pairs and picking one by hand is guesswork — whereas if two metrics
     * disagreed by a constant factor they would always rank every pair identically, and two of
     * the three would be decoration. Comparing how each one scales two different pairs proves
     * they cannot be reduced to one another.
     */
    @Test
    fun `the metrics are not proportional to one another`() {
        val greyStep = black to rgb(60, 60, 60)
        val blueStep = black to rgb(0, 0, 120)

        fun ratioIn(metric: ColorDistance): Float =
            metric.distance(blueStep.first, blueStep.second) /
                metric.distance(greyStep.first, greyStep.second)

        val srgb = ratioIn(ColorDistance.EUCLIDEAN)
        val perceptual = ratioIn(ColorDistance.OKLAB)

        assertTrue(
            "sRGB and OKLab weigh a blue step against a grey step differently " +
                "(sRGB $srgb, OKLab $perceptual)",
            kotlin.math.abs(srgb - perceptual) > 0.1f,
        )
        assertNotEquals(srgb, perceptual)
    }

    @Test
    fun `the quantiser returns a palette entry and nothing else`() {
        val palette = intArrayOf(black, white, red)
        val quantizer = PaletteQuantizer(palette, ColorDistance.OKLAB)

        for (colour in listOf(rgb(10, 10, 10), rgb(200, 30, 30), rgb(240, 240, 240))) {
            assertTrue(quantizer.nearest(colour) in palette.toList())
        }
    }

    @Test
    fun `an exact palette colour maps to itself`() {
        val palette = intArrayOf(black, white, red, blue)
        for (metric in ColorDistance.entries) {
            val quantizer = PaletteQuantizer(palette, metric)
            for (entry in palette) {
                assertEquals(metric.name, entry, quantizer.nearest(entry))
            }
        }
    }

    /** The cache must not change an answer — it exists to repeat one, not to approximate it. */
    @Test
    fun `repeated lookups are stable`() {
        val quantizer = PaletteQuantizer(intArrayOf(black, white, red), ColorDistance.CIELAB)
        val colour = rgb(120, 40, 40)
        val first = quantizer.nearest(colour)

        repeat(5) { assertEquals(first, quantizer.nearest(colour)) }
    }

    @Test
    fun `an empty palette returns the colour untouched`() {
        assertEquals(red, PaletteQuantizer(intArrayOf()).nearest(red))
    }
}
