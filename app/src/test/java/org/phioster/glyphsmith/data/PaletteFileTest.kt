package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette file format, which is the one part of a palette a stranger ever sees.
 *
 * It had no tests, which was tolerable while it only had to survive its own encoder. Reading a
 * *pack* changes that: the file now comes from somewhere else, may hold one palette or many, and
 * may have been edited by hand on the way. What it does with a file it does not understand is
 * therefore as much of a feature as what it does with one it does.
 */
class PaletteFileTest {

    @Test
    fun `one palette written and read back is the same palette`() {
        val text = PaletteFile.encodeAll(listOf(PaletteFile("dusk", listOf("#221133", "#EEDDAA"))))

        val read = PaletteFile.decodeAll(text)

        assertEquals(1, read.size)
        assertEquals("dusk", read.single().name)
        assertEquals(listOf("#221133", "#EEDDAA"), read.single().colors)
    }

    @Test
    fun `a pack written and read back keeps its order`() {
        val packed = listOf(
            PaletteFile("one", listOf("#111111")),
            PaletteFile("two", listOf("#222222")),
            PaletteFile("three", listOf("#333333")),
        )

        assertEquals(
            listOf("one", "two", "three"),
            PaletteFile.decodeAll(PaletteFile.encodeAll(packed)).map { it.name },
        )
    }

    /**
     * Both shapes open, and that is the point of `decodeAll`: a pack and a single palette arrive
     * the same way, as a `.json` somebody sent, and asking the caller to know which it holds
     * would push the question out to every call site.
     */
    @Test
    fun `a single-palette document opens as well as a pack`() {
        val single = PaletteFile.encode(
            org.phioster.glyphsmith.core.color.Palette("x", "solo", "IMPORTED", listOf(0xFF112233.toInt())),
        )

        val read = PaletteFile.decodeAll(single)

        assertEquals(1, read.size)
        assertEquals("solo", read.single().name)
    }

    @Test
    fun `one bad entry in a pack does not cost the rest`() {
        val text = """[
            {"name":"good","colors":["#101010"],"category":"IMPORTED"},
            {"name":"hollow","colors":[],"category":"IMPORTED"},
            {"name":"also good","colors":["#F0F0F0"],"category":"IMPORTED"}
        ]"""

        assertEquals(listOf("good", "also good"), PaletteFile.decodeAll(text).map { it.name })
    }

    @Test
    fun `a document that is not a palette at all reads as nothing`() {
        assertTrue(PaletteFile.decodeAll("{ not json").isEmpty())
        assertTrue(PaletteFile.decodeAll("""{"name":"empty","colors":[]}""").isEmpty())
        assertTrue(PaletteFile.decodeAll("[]").isEmpty())
    }

    @Test
    fun `a single palette with no colours is refused rather than loaded empty`() {
        assertNull(PaletteFile.decode("""{"name":"hollow","colors":[]}"""))
    }

    @Test
    fun `a file written before categories existed still loads`() {
        val read = PaletteFile.decodeAll("""{"name":"old","colors":["#010101"]}""")

        assertEquals("IMPORTED", read.single().category)
    }

    @Test
    fun `colours come back opaque and darkest first, whatever order the file used`() {
        val file = PaletteFile("mixed", listOf("#FFFFFF", "#000000", "#808080"))

        val colors = PaletteFile.colorsOf(file)

        assertEquals(listOf(0xFF000000.toInt(), 0xFF808080.toInt(), 0xFFFFFFFF.toInt()), colors)
    }

    @Test
    fun `eight digits are read with the alpha dropped, and nonsense is skipped`() {
        val file = PaletteFile("mixed", listOf("80FF0000", "not a colour", "#00FF00", "#FFF"))

        val colors = PaletteFile.colorsOf(file)

        assertTrue("expected the two readable colours, got $colors", colors.size == 2)
        assertTrue("alpha should be forced opaque", colors.all { it ushr 24 == 0xFF })
    }
}
