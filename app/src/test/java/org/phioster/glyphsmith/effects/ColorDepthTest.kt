package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.color.ColorDistance
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext

class ColorDepthTest {

    private val side = 300
    private val ctx = RenderContext(maxSide = 1024)

    /** A full grey ramp, so a level count is directly countable in the output. */
    private fun greyRamp(): Pixels {
        val data = IntArray(side * side) { i ->
            val v = (i % side) * 255 / (side - 1)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Pixels(data, side, side)
    }

    private fun distinctGreys(pixels: Pixels): Int =
        pixels.data.map { (it shr 16) and 0xFF }.toSet().size

    @Test
    fun `rgb levels are exactly what was asked for`() {
        for (levels in listOf(2, 3, 5, 9)) {
            val out = ColorDepth.apply(
                greyRamp(),
                ColorDepthParams(enabled = true, colorLevels = levels),
                ctx,
            )
            assertEquals("$levels levels", levels, distinctGreys(out))
        }
    }

    @Test
    fun `two levels give the extremes and nothing between`() {
        val out = ColorDepth.apply(greyRamp(), ColorDepthParams(enabled = true, colorLevels = 2), ctx)
        assertEquals(setOf(0, 255), out.data.map { (it shr 16) and 0xFF }.toSet())
    }

    @Test
    fun `rgb levels are evenly spaced`() {
        val out = ColorDepth.apply(greyRamp(), ColorDepthParams(enabled = true, colorLevels = 5), ctx)
        val steps = out.data.map { (it shr 16) and 0xFF }.toSortedSet().toList()
        val gaps = steps.zipWithNext { a, b -> b - a }
        // 255 does not divide by four, so the gaps differ by at most one.
        assertTrue("uneven spacing: $steps", gaps.max() - gaps.min() <= 1)
    }

    @Test
    fun `a full byte of levels is a no-op`() {
        val input = greyRamp()
        val out = ColorDepth.apply(input, ColorDepthParams(enabled = true, colorLevels = 256), ctx)
        assertSame(input.data, out.data)
    }

    /**
     * The point of offering the perceptual spaces: for the same level count they do not put the
     * steps in the same places. If they did, two of the three options would be decoration.
     */
    @Test
    fun `the perceptual spaces choose different levels than rgb`() {
        val plain = ColorDepth.apply(
            greyRamp(), ColorDepthParams(enabled = true, colorLevels = 5), ctx,
        ).data.map { (it shr 16) and 0xFF }.toSortedSet()

        for (space in listOf(ColorDistance.CIELAB, ColorDistance.OKLAB)) {
            val perceptual = ColorDepth.apply(
                greyRamp(),
                ColorDepthParams(enabled = true, colorLevels = 5, colorSpace = space),
                ctx,
            ).data.map { (it shr 16) and 0xFF }.toSortedSet()

            assertTrue("$space landed on the same levels as rgb", perceptual != plain)
        }
    }

    /**
     * Dithering trades banding for grain, so it must produce *more* distinct values than hard
     * rounding at the same level count — that is the entire mechanism.
     */
    @Test
    fun `dithering breaks the bands up`() {
        val hard = ColorDepth.apply(
            greyRamp(), ColorDepthParams(enabled = true, colorLevels = 3), ctx,
        )
        val dithered = ColorDepth.apply(
            greyRamp(), ColorDepthParams(enabled = true, colorLevels = 3, dithered = true), ctx,
        )
        assertEquals(3, distinctGreys(hard))
        assertTrue(
            "dithering did not change the distribution",
            !dithered.data.contentEquals(hard.data),
        )
    }

    @Test
    fun `a solid colour stays solid in every space`() {
        for (space in ColorDistance.entries) {
            val flat = Pixels(IntArray(64 * 64) { 0xFF3366CC.toInt() }, 64, 64)
            val out = ColorDepth.apply(
                flat,
                ColorDepthParams(enabled = true, colorLevels = 6, colorSpace = space),
                ctx,
            )
            assertEquals("$space split a flat field", 1, out.data.toSet().size)
        }
    }
}
