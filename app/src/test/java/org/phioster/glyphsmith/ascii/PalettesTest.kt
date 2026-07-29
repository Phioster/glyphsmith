package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PalettesTest {

    private val black = 0xFF000000.toInt()
    private val grey = 0xFF808080.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    /**
     * [Palettes.sample] maps a cell's luminance straight onto list position, so an unsorted
     * palette paints bright areas dark. Anything built from an image must come out ordered.
     */
    @Test
    fun `an extracted palette comes out darkest first`() {
        val colors = Palettes.fromColors(listOf(white, red, black, blue, grey))
        val luminance = colors.map { Palettes.luminanceOf(it) }

        assertEquals(luminance.sorted(), luminance)
        assertEquals(black, colors.first())
        assertEquals(white, colors.last())
    }

    @Test
    fun `extraction forces opacity and drops duplicates`() {
        val colors = Palettes.fromColors(listOf(0x00FF0000, 0xFFFF0000.toInt(), 0x80FF0000.toInt()))
        assertEquals(listOf(red), colors)
    }

    @Test
    fun `palette depth keeps both ends and hits the requested count`() {
        val base = listOf(black, 0xFF404040.toInt(), grey, 0xFFC0C0C0.toInt(), white)
        val reduced = Palettes.withDepth(base, 3)

        assertEquals(3, reduced.size)
        assertEquals(black, reduced.first())
        assertEquals(white, reduced.last())
    }

    @Test
    fun `a depth of zero or one that is no reduction leaves the palette alone`() {
        val base = listOf(black, grey, white)
        assertEquals(base, Palettes.withDepth(base, 0))
        assertEquals(base, Palettes.withDepth(base, 3))
        assertEquals(base, Palettes.withDepth(base, 9))
    }

    @Test
    fun `shuffle leaves locked stops exactly where they are`() {
        val base = listOf(black, grey, white, red, blue)
        val locks = listOf(true, false, false, true, false)

        repeat(40) { seed ->
            val shuffled = Palettes.shuffle(base, locks, Random(seed))
            assertEquals("stop 0 moved", base[0], shuffled[0])
            assertEquals("stop 3 moved", base[3], shuffled[3])
            // Whatever the permutation, no colour may be invented or lost.
            assertEquals(base.sorted(), shuffled.sorted())
        }
    }

    @Test
    fun `shuffle actually moves something when it can`() {
        val base = listOf(black, grey, white, red, blue)
        val locks = List(base.size) { false }
        val moved = (0 until 40).any { seed -> Palettes.shuffle(base, locks, Random(seed)) != base }
        assertTrue("40 shuffles never changed the order", moved)
    }

    @Test
    fun `shuffle is a no-op when everything is locked`() {
        val base = listOf(black, grey, white)
        assertEquals(base, Palettes.shuffle(base, listOf(true, true, true), Random(1)))
        // A missing lock entry means unlocked, so a short list must not crash.
        assertEquals(3, Palettes.shuffle(base, listOf(true), Random(1)).size)
    }

    @Test
    fun `render palette applies the depth while the editable one keeps every stop`() {
        val stops = listOf(black, 0xFF404040.toInt(), grey, 0xFFC0C0C0.toInt(), white)
        val params = AsciiParams(paletteOverride = stops, paletteDepth = 2)

        assertEquals(5, params.activePalette().colors.size)
        assertEquals(2, params.renderPalette().colors.size)
    }

    @Test
    fun `locks default to unlocked past the end of the list`() {
        val params = AsciiParams(paletteLocks = listOf(true))
        assertTrue(params.isStopLocked(0))
        assertTrue(!params.isStopLocked(1))
        assertTrue(!params.isStopLocked(99))
    }
}
