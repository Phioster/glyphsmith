package org.phioster.glyphsmith.ascii

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ramp override is one field serving two features — `auto-order` and the hand editor —
 * so what matters is that it behaves exactly like a set would, and that its absence changes
 * nothing at all.
 */
class RampOverrideTest {

    private val base = RenderSettings(charSetId = "ascii-standard-10", depth = 64)

    @Test
    fun `an empty override leaves the ramp exactly as it was`() {
        val set = CharacterSets.byId(base.charSetId)
        assertEquals(set.glyphs, base.baseGlyphs())
        assertEquals(set.glyphs, base.effectiveRamp())
    }

    @Test
    fun `an override replaces the set's glyphs`() {
        val params = base.copy(rampOverride = ".oO@")
        assertEquals(".oO@", params.baseGlyphs())
        assertEquals(".oO@", params.effectiveRamp())
    }

    /** Depth, injection and invert are downstream of the base, so they must still apply. */
    @Test
    fun `depth narrows an override the same way it narrows a set`() {
        val params = base.copy(rampOverride = "abcdefghij", depth = 3)
        val ramp = params.effectiveRamp()

        assertEquals(3, ramp.length)
        assertEquals('a', ramp.first())
        assertEquals('j', ramp.last())
    }

    @Test
    fun `injection and invert still apply on top of an override`() {
        val injected = base.copy(rampOverride = "abc", injection = "#")
        assertEquals("abc#", injected.effectiveRamp())

        val inverted = base.copy(rampOverride = "abc", invert = true)
        assertEquals("cba", inverted.effectiveRamp())
    }

    @Test
    fun `the offset range follows the override`() {
        assertEquals(4, base.copy(rampOverride = "abcd").offsetMax())
    }

    @Test
    fun `an override survives a round trip through a preset`() {
        val params = base.copy(rampOverride = " .:-=+*#%@")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val restored = json.decodeFromString<RenderSettings>(json.encodeToString(params))
        assertEquals(params.rampOverride, restored.rampOverride)
    }

    /** A stored preset from before the field existed must still load. */
    @Test
    fun `params without the field decode to no override`() {
        val json = Json { ignoreUnknownKeys = true }
        val restored = json.decodeFromString<RenderSettings>("""{"charSetId":"ascii-standard-10"}""")
        assertTrue(restored.rampOverride.isEmpty())
        assertEquals(CharacterSets.byId("ascii-standard-10").glyphs, restored.baseGlyphs())
    }
}
