package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.color.Palettes

class AsciiParamsTest {

    private val tenLevel = AsciiParams(charSetId = "ascii-standard-10", depth = 10)

    @Test
    fun `depth beyond the set length uses the whole set`() {
        val ramp = tenLevel.copy(depth = AsciiParams.MAX_DEPTH).effectiveRamp()
        assertEquals(CharacterSets.byId("ascii-standard-10").glyphs, ramp)
    }

    @Test
    fun `depth narrows the ramp and keeps both ends`() {
        val full = CharacterSets.byId("ascii-standard-10").glyphs
        val ramp = tenLevel.copy(depth = 4).effectiveRamp()
        assertEquals(4, ramp.length)
        assertEquals(full.first(), ramp.first())
        assertEquals(full.last(), ramp.last())
    }

    @Test
    fun `depth of one keeps only the densest glyph`() {
        val ramp = tenLevel.copy(depth = 1).effectiveRamp()
        assertEquals(1, ramp.length)
        assertEquals(CharacterSets.byId("ascii-standard-10").glyphs.last(), ramp.first())
    }

    @Test
    fun `injection is appended and capped`() {
        val ramp = tenLevel.copy(injection = "0123456789ABCDEF").effectiveRamp()
        assertTrue(ramp.endsWith("0123456789"))
        assertEquals(10 + AsciiParams.MAX_INJECTION, ramp.length)
    }

    @Test
    fun `invert reverses the ramp after injection`() {
        val plain = tenLevel.copy(injection = "@@").effectiveRamp()
        val inverted = tenLevel.copy(injection = "@@", invert = true).effectiveRamp()
        assertEquals(plain.reversed(), inverted)
    }

    @Test
    fun `offset max tracks the ramp length`() {
        assertEquals(10, tenLevel.offsetMax())
        assertEquals(14, tenLevel.copy(injection = "abcd").offsetMax())
    }

    @Test
    fun `palette override replaces the named palette's stops`() {
        val stops = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertEquals(stops, tenLevel.copy(paletteOverride = stops).activePalette().colors)
        assertEquals(Palettes.byId(tenLevel.paletteId).colors, tenLevel.activePalette().colors)
    }
}
