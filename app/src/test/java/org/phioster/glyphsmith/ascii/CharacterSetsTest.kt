package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSetsTest {

    @Test
    fun `library matches the advertised 48 sets across 11 categories`() {
        assertEquals(48, CharacterSets.all.size)
        assertEquals(11, CharacterSets.categories.size)
    }

    @Test
    fun `ids are unique`() {
        val ids = CharacterSets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * A repeated glyph inside one ramp is always a typo: it wastes a tonal step and makes
     * two different luminance bands render identically.
     */
    @Test
    fun `no set repeats a glyph`() {
        CharacterSets.all.forEach { set ->
            assertEquals("${set.id} repeats a glyph", set.glyphs.length, set.glyphs.toSet().size)
        }
    }

    @Test
    fun `every set has at least two levels`() {
        CharacterSets.all.forEach { assertTrue("${it.id} is too short", it.glyphs.length >= 2) }
    }

    @Test
    fun `unknown id falls back to the default set`() {
        assertEquals(CharacterSets.default, CharacterSets.byId("does-not-exist"))
    }

    @Test
    fun `categories partition the library`() {
        val counted = CharacterSets.categories.sumOf { CharacterSets.inCategory(it).size }
        assertEquals(CharacterSets.all.size, counted)
    }
}
