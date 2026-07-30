package org.phioster.glyphsmith.effects

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext

class SpotColorPrintTest {

    private val side = 300
    private val ctx = RenderContext(maxSide = 1024)

    /** A hard-edged colour block: registration error is only visible against an edge. */
    private fun blocks(): Pixels {
        val data = IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            when {
                x < side / 2 && y < side / 2 -> 0xFFFF0000.toInt()
                x >= side / 2 && y < side / 2 -> 0xFF00FF00.toInt()
                x < side / 2 -> 0xFF0000FF.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
        }
        return Pixels(data, side, side)
    }

    @Test
    fun `misregistration changes the image`() {
        val registered = SpotColorPrint.apply(
            blocks(),
            SpotColorPrintParams(enabled = true, misalignment = 0, inkBleed = 0),
            ctx,
        ).data
        val offset = SpotColorPrint.apply(
            blocks(),
            SpotColorPrintParams(enabled = true, misalignment = 90, inkBleed = 0),
            ctx,
        ).data

        assertTrue("the plates did not shift", !registered.contentEquals(offset))
    }

    /**
     * Reproducible despite running its rows in parallel *and* using randomness.
     *
     * This is the test that would catch a sequential `Random(seed)` sneaking in: the plate offsets
     * and the paper grain are positional hashes, so they cannot depend on which band ran first.
     */
    @Test
    fun `the same seed prints the same sheet`() {
        val params = SpotColorPrintParams(enabled = true, misalignment = 50, seed = 7)
        assertArrayEquals(
            SpotColorPrint.apply(blocks(), params, ctx).data,
            SpotColorPrint.apply(blocks(), params, ctx).data,
        )
    }

    @Test
    fun `a different seed prints a different sheet`() {
        val first = SpotColorPrint.apply(
            blocks(), SpotColorPrintParams(enabled = true, misalignment = 60, seed = 1), ctx,
        ).data
        val second = SpotColorPrint.apply(
            blocks(), SpotColorPrintParams(enabled = true, misalignment = 60, seed = 2), ctx,
        ).data
        assertTrue("the seed did nothing", !first.contentEquals(second))
    }

    /** Ink is subtractive: laying more of it down cannot make the sheet brighter. */
    @Test
    fun `more ink is never lighter`() {
        fun meanLuma(opacity: Int): Double = SpotColorPrint.apply(
            blocks(),
            SpotColorPrintParams(enabled = true, inkOpacity = opacity, misalignment = 0),
            ctx,
        ).data.map { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) }.average()

        assertTrue("heavier ink brightened the sheet", meanLuma(100) <= meanLuma(30))
    }

    /** Paper is never brighter than white, and ink never takes a channel below zero. */
    @Test
    fun `every channel stays in range`() {
        val out = SpotColorPrint.apply(
            blocks(),
            SpotColorPrintParams(
                enabled = true, misalignment = 100, inkOpacity = 100, inkBleed = 100,
                paperTextureBlend = 100, inkCount = 4, paperTone = 100,
            ),
            ctx,
        )
        assertTrue(
            "a channel left 0..255",
            out.data.all { pixel ->
                listOf(16, 8, 0).all { shift -> ((pixel shr shift) and 0xFF) in 0..255 }
            },
        )
    }

    @Test
    fun `the plate count is respected`() {
        val two = SpotColorPrint.apply(
            blocks(), SpotColorPrintParams(enabled = true, inkCount = 2, misalignment = 0), ctx,
        ).data
        val four = SpotColorPrint.apply(
            blocks(), SpotColorPrintParams(enabled = true, inkCount = 4, misalignment = 0), ctx,
        ).data
        assertTrue("adding plates changed nothing", !two.contentEquals(four))
    }
}
