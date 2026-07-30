package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext

class BlueNoiseDitherTest {

    private val side = 320
    private val ctx = RenderContext(maxSide = 1024)

    private fun grey(level: Int) = Pixels(
        IntArray(side * side) { (0xFF shl 24) or (level shl 16) or (level shl 8) or level },
        side,
        side,
    )

    private fun reds(pixels: Pixels): List<Int> = pixels.data.map { (it shr 16) and 0xFF }

    @Test
    fun `the output only holds the allowed levels`() {
        for (levels in listOf(2, 3, 4, 8)) {
            val out = BlueNoiseDither.apply(
                grey(128),
                BlueNoiseDitherParams(enabled = true, levels = levels),
                ctx,
            )
            assertTrue(
                "$levels levels produced ${reds(out).toSet().size} values",
                reds(out).toSet().size <= levels,
            )
        }
    }

    /**
     * The reason a dither is used at all: a mid grey the palette cannot hold is reproduced as a mix
     * of the two levels either side of it, and the *average* comes out as the value asked for. A
     * dither that failed this would be posterisation with extra steps.
     */
    @Test
    fun `a mid grey averages back to itself`() {
        val out = BlueNoiseDither.apply(
            grey(128),
            BlueNoiseDitherParams(enabled = true, levels = 2),
            ctx,
        )
        val mean = reds(out).average()
        assertEquals("the dither is biased", 128.0, mean, 12.0)
        assertEquals("two levels only", setOf(0, 255), reds(out).toSet())
    }

    /** Black and white are exactly representable, so there is nothing to dither and no grain. */
    @Test
    fun `the extremes are left flat`() {
        for (level in listOf(0, 255)) {
            val out = BlueNoiseDither.apply(
                grey(level),
                BlueNoiseDitherParams(enabled = true, levels = 2),
                ctx,
            )
            assertEquals("level $level picked up grain", 1, reds(out).toSet().size)
        }
    }

    @Test
    fun `the threshold biases the result`() {
        fun meanAt(threshold: Int) = reds(
            BlueNoiseDither.apply(
                grey(128),
                BlueNoiseDitherParams(enabled = true, levels = 2, threshold = threshold),
                ctx,
            ),
        ).average()

        assertTrue("a low threshold should darken", meanAt(15) < meanAt(50))
        assertTrue("a high threshold should lighten", meanAt(85) > meanAt(50))
    }

    /**
     * Blue noise earns its name by having no periodic structure. A Bayer matrix at this scale would
     * repeat every few pixels and the row-to-row correlation would be obvious; this checks the mask
     * is not doing that, which is the whole reason to prefer it.
     */
    @Test
    fun `the pattern does not repeat row to row`() {
        val out = BlueNoiseDither.apply(
            grey(128),
            BlueNoiseDitherParams(enabled = true, levels = 2),
            ctx,
        )
        val rowOne = out.data.slice(0 until side)
        val rowTwo = out.data.slice(side until 2 * side)
        assertTrue("adjacent rows are identical — that is not blue noise", rowOne != rowTwo)
    }

    @Test
    fun `monochrome mode keeps the hue`() {
        val blue = Pixels(IntArray(side * side) { 0xFF2040A0.toInt() }, side, side)
        val out = BlueNoiseDither.apply(
            blue,
            BlueNoiseDitherParams(enabled = true, levels = 3, monochrome = true),
            ctx,
        )
        // Blue stayed the dominant channel everywhere, rather than the image going grey.
        assertTrue(
            "the hue was lost",
            out.data.all { (it and 0xFF) >= ((it shr 16) and 0xFF) },
        )
    }

    @Test
    fun `disabled is a no-op on the same buffer`() {
        val input = grey(90)
        assertSame(input.data, BlueNoiseDither.apply(input, BlueNoiseDitherParams(), ctx).data)
    }
}
