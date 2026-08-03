package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import org.phioster.glyphsmith.core.image.Adjustments

class AdjustmentsTest {

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    /**
     * The one property that matters most: these controls were added to an app full of saved
     * presets, and at their neutral values nothing may move by a single level.
     */
    @Test
    fun `neutral values leave a colour exactly alone`() {
        for (v in 0..255 step 17) {
            val out = Adjustments.colorAdjust(v, 255 - v, 128, 0, 100)
            assertEquals(v, r(out))
            assertEquals(255 - v, g(out))
            assertEquals(128, b(out))
        }
    }

    @Test
    fun `neutral midtones and highlights leave the tone curve alone`() {
        for (i in 0..100) {
            val v = i / 100f
            assertEquals(v, Adjustments.tone(v, 50, 50), 1e-6f)
        }
    }

    @Test
    fun `saturation zero produces grey`() {
        val out = Adjustments.colorAdjust(200, 60, 30, 0, 0)
        assertEquals("red and green differ", r(out), g(out))
        assertEquals("green and blue differ", g(out), b(out))
    }

    /** Desaturating must not also darken or brighten — it pulls towards the cell's own grey. */
    @Test
    fun `desaturation keeps the brightness`() {
        val colour = Adjustments.colorAdjust(200, 60, 30, 0, 100)
        val grey = Adjustments.colorAdjust(200, 60, 30, 0, 0)
        val before = GlyphEngine.luminance(r(colour), g(colour), b(colour))
        val after = GlyphEngine.luminance(r(grey), g(grey), b(grey))
        assertEquals(before, after, 0.01f)
    }

    @Test
    fun `saturation above one hundred pushes colours apart`() {
        val normal = Adjustments.colorAdjust(180, 100, 60, 0, 100)
        val boosted = Adjustments.colorAdjust(180, 100, 60, 0, 180)
        val spreadNormal = r(normal) - b(normal)
        val spreadBoosted = r(boosted) - b(boosted)
        assertTrue("boosting did not widen the spread", spreadBoosted > spreadNormal)
    }

    /** A half turn of the wheel takes red to cyan; the exact matrix is standard. */
    @Test
    fun `hue rotation by one hundred and eighty degrees inverts the hue`() {
        val out = Adjustments.colorAdjust(255, 0, 0, 180, 100)
        assertTrue("red survived a half turn: ${r(out)}", r(out) < 120)
        assertTrue("green did not rise: ${g(out)}", g(out) > 120)
        assertTrue("blue did not rise: ${b(out)}", b(out) > 120)
    }

    @Test
    fun `a full turn of hue is a no-op`() {
        val out = Adjustments.colorAdjust(200, 90, 40, 360, 100)
        assertEquals(200, r(out))
        assertEquals(90, g(out))
        assertEquals(40, b(out))
    }

    @Test
    fun `grey has no hue to rotate`() {
        val out = Adjustments.colorAdjust(128, 128, 128, 90, 100)
        assertTrue(abs(r(out) - 128) <= 1 && abs(g(out) - 128) <= 1 && abs(b(out) - 128) <= 1)
    }

    @Test
    fun `midtones move the middle and leave the ends`() {
        assertEquals(0f, Adjustments.tone(0f, 90, 50), 1e-5f)
        assertEquals(1f, Adjustments.tone(1f, 90, 50), 1e-5f)
        assertTrue("raising midtones did not brighten", Adjustments.tone(0.5f, 90, 50) > 0.5f)
        assertTrue("lowering midtones did not darken", Adjustments.tone(0.5f, 10, 50) < 0.5f)
    }

    @Test
    fun `highlights only touch the upper half`() {
        assertEquals(0.25f, Adjustments.tone(0.25f, 50, 100), 1e-5f)
        assertEquals(0.5f, Adjustments.tone(0.5f, 50, 100), 1e-5f)
        assertTrue(Adjustments.tone(0.8f, 50, 100) > 0.8f)
        assertTrue(Adjustments.tone(0.8f, 50, 0) < 0.8f)
    }

    @Test
    fun `the tone curve never leaves the unit interval`() {
        for (m in 0..100 step 10) {
            for (h in 0..100 step 10) {
                for (i in 0..20) {
                    val out = Adjustments.tone(i / 20f, m, h)
                    assertTrue("$out out of range at m=$m h=$h", out in 0f..1f)
                }
            }
        }
    }

    /** A hard vertical edge with one bright speck sitting to the left of it. */
    private fun speckledEdge(cols: Int = 9, rows: Int = 5): FloatArray =
        FloatArray(cols * rows) { i ->
            val x = i % cols
            when {
                x >= cols / 2 -> 1f
                i == 2 * cols + 1 -> 1f
                else -> 0f
            }
        }

    /**
     * The reason both a blur and a denoise exist. A median drops the lone speck and leaves
     * the edge standing; a blur softens the edge and only dilutes the speck. If this ever
     * fails, one of the two has quietly become the other.
     */
    @Test
    fun `denoise removes a speck but keeps the edge`() {
        val cols = 9
        val rows = 5
        val grid = speckledEdge(cols, rows)
        val out = Adjustments.denoise(grid, cols, rows, 1)

        assertEquals("the speck survived", 0f, out[2 * cols + 1], 1e-5f)
        val step = out[2 * cols + cols / 2] - out[2 * cols + cols / 2 - 1]
        assertEquals("the edge was softened", 1f, step, 1e-5f)
    }

    @Test
    fun `blur softens the edge, which is what makes it the other tool`() {
        val cols = 9
        val rows = 5
        val grid = speckledEdge(cols, rows)
        val out = Adjustments.blur(grid, cols, rows, 1)

        val step = out[2 * cols + cols / 2] - out[2 * cols + cols / 2 - 1]
        assertTrue("blur left the edge hard", step < 0.9f)
    }

    @Test
    fun `a radius of zero is a no-op for both`() {
        val grid = speckledEdge()
        assertTrue(Adjustments.blur(grid, 9, 5, 0).contentEquals(grid))
        assertTrue(Adjustments.denoise(grid, 9, 5, 0).contentEquals(grid))
    }

    @Test
    fun `blur conserves the average`() {
        val cols = 8
        val rows = 8
        val grid = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }
        val out = Adjustments.blur(grid, cols, rows, 2)
        assertEquals(grid.average().toFloat(), out.average().toFloat(), 0.02f)
    }

    @Test
    fun `neither pass leaves the unit interval`() {
        val cols = 8
        val rows = 6
        val grid = FloatArray(cols * rows) { (it * 37 % 100) / 99f }
        listOf(Adjustments.blur(grid, cols, rows, 2), Adjustments.denoise(grid, cols, rows, 2))
            .forEach { out -> assertTrue(out.all { it in 0f..1f }) }
    }
}
