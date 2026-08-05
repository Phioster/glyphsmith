package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an imported screen has to be true about.
 *
 * The property that makes an ordered matrix work is not "looks like the picture" — it is that
 * every threshold appears exactly once. A screen that merely copied brightness would leave some
 * levels unreachable and double others, and a flat picture would produce a screen that dithers
 * nothing. Ranking is what avoids that, and these hold it to it.
 */
class ScreenImportTest {

    private fun field(n: Int, f: (Int, Int) -> Float) = FloatArray(n * n) { f(it % n, it / n) }

    @Test
    fun `a screen holds every threshold exactly once`() {
        ScreenImport.SIZES.forEach { size ->
            val screen = ScreenImport.screenOf(field(64) { x, y -> (x * y).toFloat() }, 64, 64, size)

            assertEquals("$size has the wrong number of cells", size * size, screen.size)
            assertEquals(
                "$size does not hold 0 until ${size * size}",
                (0 until size * size).toList(),
                screen.sorted(),
            )
        }
    }

    /**
     * The case that would break a brightness copy.
     *
     * A flat picture has no ordering to read, so a copy would put every threshold at the same
     * place and the style would quantise without dithering at all. Ranked, it still yields a
     * complete screen — arbitrary, but usable, and the same one every time.
     */
    @Test
    fun `a flat picture still gives a complete screen, deterministically`() {
        val flat = field(32) { _, _ -> 0.5f }

        val first = ScreenImport.screenOf(flat, 32, 32, 16)
        val second = ScreenImport.screenOf(flat, 32, 32, 16)

        assertEquals((0 until 256).toList(), first.sorted())
        assertEquals("a flat field is not deterministic", first, second)
    }

    /** Any input size: a photograph and an icon both have to reduce. */
    @Test
    fun `an image of any size reduces to the screen size`() {
        listOf(7 to 5, 64 to 64, 300 to 121).forEach { (w, h) ->
            val luma = FloatArray(w * h) { (it % 13) / 13f }
            val screen = ScreenImport.screenOf(luma, w, h, 16)

            assertEquals("${w}x$h", 256, screen.size)
            assertEquals("${w}x$h", (0 until 256).toList(), screen.sorted())
        }
    }

    /** A brighter region has to end up with higher thresholds than a darker one. */
    @Test
    fun `the ranking follows the picture`() {
        val ramp = ScreenImport.screenOf(field(32) { x, _ -> x / 32f }, 32, 32, 16)

        val leftColumn = (0 until 16).map { ramp[it * 16] }
        val rightColumn = (0 until 16).map { ramp[it * 16 + 15] }

        assertTrue("the dark side did not rank low", leftColumn.max() < rightColumn.min())
    }

    @Test
    fun `the size is read back off the screen itself`() {
        ScreenImport.SIZES.forEach { size ->
            val screen = ScreenImport.screenOf(field(40) { x, y -> (x + y).toFloat() }, 40, 40, size)

            assertEquals(size, ScreenImport.sizeOf(screen))
        }
    }

    /** Anything that is not a screen this build stores reads as none, rather than as a guess. */
    @Test
    fun `a malformed screen is no screen`() {
        assertEquals(0, ScreenImport.sizeOf(emptyList()))
        assertEquals(0, ScreenImport.sizeOf(List(255) { it }))
        assertEquals(0, ScreenImport.sizeOf(List(64) { it }))
        assertNull(ScreenImport.thresholdAt(emptyList(), 3, 4))
        assertNull(ScreenImport.thresholdAt(List(255) { it }, 3, 4))
    }

    @Test
    fun `thresholds stay inside the unit interval and tile in both directions`() {
        val screen = ScreenImport.screenOf(field(32) { x, y -> (x * 3 + y).toFloat() }, 32, 32, 16)

        for (y in -20..20) {
            for (x in -20..20) {
                val t = ScreenImport.thresholdAt(screen, x, y)
                assertTrue("no threshold at $x,$y", t != null)
                assertTrue("$t is outside 0..1", t!! in 0f..1f)
                assertEquals("does not tile at $x,$y", t, ScreenImport.thresholdAt(screen, x + 16, y + 16))
            }
        }
    }

    @Test
    fun `an unsupported size is refused rather than rounded to one that is`() {
        listOf(8, 20, 64).forEach { size ->
            val threw = runCatching { ScreenImport.screenOf(field(32) { _, _ -> 0f }, 32, 32, size) }
            assertTrue("$size was accepted", threw.isFailure)
        }
    }
}
