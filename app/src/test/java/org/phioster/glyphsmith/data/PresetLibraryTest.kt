package org.phioster.glyphsmith.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.ascii.CharacterSets
import org.phioster.glyphsmith.ascii.Palettes

class PresetLibraryTest {

    private val library = PresetStore.builtIns

    @Test
    fun `names are unique`() {
        val names = library.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every preset sits in a known category`() {
        library.forEach { preset ->
            assertTrue(
                "${preset.name} is in unknown category ${preset.category}",
                preset.category in PresetStore.categories,
            )
        }
    }

    /** A preset naming a set or palette that does not exist would silently fall back. */
    @Test
    fun `every preset points at a real set and palette`() {
        library.forEach { preset ->
            assertTrue(
                "${preset.name} names a missing set: ${preset.params.charSetId}",
                CharacterSets.all.any { it.id == preset.params.charSetId },
            )
            assertTrue(
                "${preset.name} names a missing palette: ${preset.params.paletteId}",
                Palettes.all.any { it.id == preset.params.paletteId },
            )
        }
    }

    /**
     * The MOTION set exists so that applying one and pressing play is the whole interaction.
     * A preset in there that does not actually move would make the category a lie.
     */
    @Test
    fun `every motion preset really animates`() {
        val motion = library.filter { it.category == PresetStore.CATEGORY_MOTION }
        assertTrue("no motion presets shipped", motion.isNotEmpty())

        motion.forEach { preset ->
            val params = preset.params
            val moves = params.animation.activeCount > 0 || params.temporal.enabled
            assertTrue("${preset.name} has animation switched off", params.animation.enabled)
            assertTrue("${preset.name} animates nothing", moves)
        }
    }

    @Test
    fun `nothing outside motion arrives with animation switched on`() {
        library.filterNot { it.category == PresetStore.CATEGORY_MOTION }.forEach {
            assertFalse("${it.name} animates unexpectedly", it.params.animation.enabled)
        }
    }

    /** The whole point of the expansion: the newer engine features have starting points. */
    @Test
    fun `the library exercises the features added after the first nine`() {
        assertTrue("no modulation preset", library.any { it.params.ditherMode.name.startsWith("MOD_") })
        assertTrue("no beehive preset", library.any { it.params.ditherMode.name == "BEEHIVE" })
        assertTrue("no cmyk preset", library.any { it.params.effects.cmyk.enabled })
        assertTrue("no subtexture preset", library.any { it.params.effects.subtexture.enabled })
        assertTrue("no pixel sort preset", library.any { it.params.effects.pixelSort.enabled })
        assertTrue("no slice shift preset", library.any { it.params.effects.sliceShift.enabled })
        assertTrue("no edge preset", library.any { it.params.edgeEnabled })
        assertTrue("no temporal preset", library.any { it.params.temporal.enabled })
        assertTrue(
            "no preset with a reordered chain",
            library.any { it.params.effects.order != it.params.effects.effectiveOrder().sortedBy { id -> id.ordinal } },
        )
    }

    @Test
    fun `an animated preset closes its loop`() {
        library.filter { it.params.animation.enabled }.forEach { preset ->
            preset.params.animation.tracks.filter { it.enabled }.forEach { track ->
                assertTrue(
                    "${preset.name} drives ${track.target} over a fraction of a cycle",
                    track.cycles >= 1,
                )
            }
        }
    }

    /** Presets stored before category and favourite existed must still load. */
    @Test
    fun `a preset without the new fields still decodes`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<Preset>(
            """{"name":"old","params":{"charSetId":"ascii-standard-10"}}""",
        )
        assertEquals("old", decoded.name)
        assertEquals(PresetStore.CATEGORY_CUSTOM, decoded.category)
        assertFalse(decoded.favourite)
    }
}
