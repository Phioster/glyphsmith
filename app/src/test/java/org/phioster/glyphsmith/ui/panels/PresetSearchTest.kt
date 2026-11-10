package org.phioster.glyphsmith.ui.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.data.PresetLibrary

class PresetSearchTest {

    private val library = PresetLibrary.builtIns

    @Test
    fun `an empty query keeps everything`() {
        assertEquals(library, PresetFilters.search(library, "   "))
    }

    @Test
    fun `a name is found in any case`() {
        val hits = PresetFilters.search(library, "MATRIX reveal").map { it.name }
        assertTrue("matrix reveal" in hits)
    }

    @Test
    fun `an effect finds the presets that switch it on`() {
        val hits = PresetFilters.search(library, "neo glow").map { it.name }
        assertTrue("matrix reveal" in hits)
        assertTrue(hits.none { it == "one bit" })
    }

    @Test
    fun `every word has to match, in any order`() {
        val both = PresetFilters.search(library, "triad motion").map { it.name }
        assertTrue("triad tube" in both)
        assertTrue(PresetFilters.search(library, "triad zzzz").isEmpty())
    }
}
