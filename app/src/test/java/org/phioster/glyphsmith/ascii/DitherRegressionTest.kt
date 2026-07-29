package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The net under the algorithm round.
 *
 * Roughly fifty styles are about to be added, and every one of them touches shared machinery:
 * the threshold function, the kernel lookup, the loop that walks the grid. The danger is not
 * that a new style is wrong — that gets noticed immediately, because someone went looking for
 * it. The danger is that adding it quietly changes an old one that nobody thought to check.
 */
class DitherRegressionTest {

    /**
     * Small cells on a large image, giving a 64×32 grid.
     *
     * The size is load-bearing, which is not obvious. A 16×16 Bayer tile needs sixteen rows
     * before it differs from the 8×8 one it contains, and a staggered orb lattice needs two
     * lattice rows before the stagger exists at all. On a coarser grid those pairs render
     * identically — correctly so — and the test below would call it a collision.
     */
    private val params = AsciiParams(charSetId = "ascii-standard-10", depth = 10, cellSize = 2)

    /** A gradient with a hard diagonal through it — flat fields alone hide most mistakes. */
    private fun testImage(width: Int = SIDE, height: Int = SIDE): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val ramp = (x * 255 / (width - 1))
        val v = if (x + y > width) (255 - ramp) else ramp
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    private fun render(mode: DitherMode): String =
        AsciiEngine.convert(testImage(), SIDE, SIDE, params.copy(ditherMode = mode)).toText()

    /**
     * The one property this phase can actually prove about itself.
     *
     * `threshold` grew a brightness argument so that later styles can read the picture as
     * well as the position. Every style that existed before must ignore it completely — if
     * one starts listening, its texture changes with the image content and no saved preset
     * looks the way it did.
     */
    @Test
    fun `the styles that predate the brightness argument ignore it`() {
        val options = PatternOptions()
        DitherMode.entries.filter { Dither.isThresholdBased(it) }.forEach { mode ->
            for (x in 0 until 17) {
                for (y in 0 until 17) {
                    val dark = Dither.threshold(mode, x, y, 0f, options)
                    val mid = Dither.threshold(mode, x, y, 0.5f, options)
                    val light = Dither.threshold(mode, x, y, 1f, options)
                    assertEquals("$mode reacted to brightness at ($x,$y)", dark, mid, 0f)
                    assertEquals("$mode reacted to brightness at ($x,$y)", dark, light, 0f)
                }
            }
        }
    }

    @Test
    fun `every style produces a full grid of glyphs`() {
        DitherMode.entries.forEach { mode ->
            val art = AsciiEngine.convert(testImage(), SIDE, SIDE, params.copy(ditherMode = mode))
            assertEquals("$mode returned the wrong size", art.cols * art.rows, art.glyphs.size)
            assertTrue("$mode produced no glyphs", art.glyphs.isNotEmpty())
        }
    }

    /** A one-cell image is the smallest thing that can go wrong at an array boundary. */
    @Test
    fun `every style survives a single cell`() {
        DitherMode.entries.forEach { mode ->
            val art = AsciiEngine.convert(IntArray(4) { -1 }, 2, 2, params.copy(ditherMode = mode))
            assertTrue("$mode failed on a 2x2 image", art.glyphs.isNotEmpty())
        }
    }

    /**
     * Rendering is deterministic — twice through the same style gives the same picture.
     *
     * Sounds trivial; it is not. The generated matrices are built lazily and cached, and the
     * orb lattice hashes its own coordinates. Either could pick up state between runs.
     */
    @Test
    fun `rendering twice gives the same result`() {
        DitherMode.entries.forEach { mode ->
            assertEquals("$mode is not deterministic", render(mode), render(mode))
        }
    }

    /**
     * Two different styles must not render identically.
     *
     * This is what catches a new style wired to the wrong branch — it compiles, it appears in
     * the picker, and it silently draws what Floyd–Steinberg drew. No pair is exempt: if two
     * styles agree on this image, either one of them is misrouted or the grid is too coarse
     * to tell them apart, and both are worth stopping for.
     */
    @Test
    fun `styles are distinguishable from one another`() {
        val rendered = DitherMode.entries.associateWith { render(it) }
        val collisions = rendered.entries
            .groupBy { it.value }
            .filterValues { it.size > 1 }
            .map { group -> group.value.map { it.key.name } }
        assertTrue("these styles render identically: $collisions", collisions.isEmpty())
    }

    private companion object {
        const val SIDE = 128
    }
}
