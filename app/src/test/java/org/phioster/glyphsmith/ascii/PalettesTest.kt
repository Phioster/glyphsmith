package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import org.phioster.glyphsmith.core.color.Palettes

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
        val params = RenderSettings(paletteOverride = stops, paletteDepth = 2)

        assertEquals(5, params.activePalette().colors.size)
        assertEquals(2, params.renderPalette().colors.size)
    }

    /**
     * The one that guards every shipped palette at once.
     *
     * [Palettes.sample] maps a cell's luminance straight onto list position, so a palette
     * whose stops are out of order paints bright areas dark. The nineteen originals were
     * hand-sorted and never checked; this checks them along with everything added since.
     */
    @Test
    fun `every shipped palette runs darkest to lightest`() {
        Palettes.all.forEach { palette ->
            val luminance = palette.colors.map { Palettes.luminanceOf(it) }
            assertEquals(
                "${palette.id} is not ordered darkest first: $luminance",
                luminance.sorted(),
                luminance,
            )
        }
    }

    @Test
    fun `every shipped palette has enough stops to be a ramp`() {
        Palettes.all.forEach { palette ->
            assertTrue("${palette.id} has ${palette.colors.size} stops", palette.colors.size >= 2)
        }
    }

    @Test
    fun `shipped palette ids and names are unique`() {
        val ids = Palettes.all.map { it.id }
        val names = Palettes.all.map { it.name.lowercase() }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every shipped stop is fully opaque`() {
        Palettes.all.forEach { palette ->
            palette.colors.forEach { colour ->
                assertEquals(
                    "${palette.id} has a translucent stop",
                    0xFF,
                    (colour ushr 24) and 0xFF,
                )
            }
        }
    }

    @Test
    fun `every category holds at least one palette and is reachable`() {
        assertTrue(Palettes.categories.isNotEmpty())
        Palettes.categories.forEach { category ->
            assertTrue("$category is empty", Palettes.inCategory(category).isNotEmpty())
        }
        Palettes.all.forEach { palette ->
            assertTrue(
                "${palette.id} sits in ${palette.category}, which the picker never shows",
                palette.category in Palettes.categories,
            )
        }
    }

    @Test
    fun `locks default to unlocked past the end of the list`() {
        val params = RenderSettings(paletteLocks = listOf(true))
        assertTrue(params.isStopLocked(0))
        assertTrue(!params.isStopLocked(1))
        assertTrue(!params.isStopLocked(99))
    }
}
