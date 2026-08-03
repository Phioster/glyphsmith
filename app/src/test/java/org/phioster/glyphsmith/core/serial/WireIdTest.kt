package org.phioster.glyphsmith.core.serial

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.DitherModeIds
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectIds
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderModeIds

/**
 * The stable ids: the identities a saved preset is made of.
 *
 * Everything here guards the same promise — that what a preset names does not move when the
 * source code does. An id that is duplicated, malformed, or quietly resolved to something else
 * breaks that promise in a way nobody notices until a preset comes back looking wrong.
 */
class WireIdTest {

    private val tables = listOf(RenderModeIds, DitherModeIds, EffectIds)

    private val json = Json { encodeDefaults = true }

    // --- uniqueness ------------------------------------------------------------------

    @Test
    fun `every render mode id is unique`() = assertUnique(RenderModeIds.ids)

    @Test
    fun `every dither id is unique`() = assertUnique(DitherModeIds.ids)

    @Test
    fun `every effect id is unique`() = assertUnique(EffectIds.ids)

    private fun <T : Enum<T>> assertUnique(ids: Map<T, String>) {
        val duplicates = ids.values.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("two entries share an id: $duplicates", duplicates.isEmpty())
    }

    // --- format ----------------------------------------------------------------------

    @Test
    fun `every id follows the agreed format`() {
        tables.forEach { table ->
            table.ids.forEach { (value, id) ->
                assertTrue("$value has a malformed id: \"$id\"", WireId.isValid(id))
            }
        }
    }

    @Test
    fun `every id is prefixed with its own category`() {
        tables.forEach { table ->
            table.ids.forEach { (value, id) ->
                assertTrue(
                    "$value is a ${table.category} but its id reads \"$id\"",
                    id.startsWith("${table.category}."),
                )
            }
        }
    }

    /**
     * The point of the whole exercise. An id derived from the constant would track a rename,
     * which is exactly what a preset must not do — so at least one of them has to differ, or
     * the table is doing nothing that `name.lowercase()` would not.
     */
    @Test
    fun `ids are not merely the enum names in lower case`() {
        tables.forEach { table ->
            val derived = table.ids.count { (value, id) ->
                id.substringAfter('.') == value.name.lowercase().replace('_', '-')
            }
            assertTrue(
                "${table.category} ids are all mechanical renderings of the constant names",
                derived < table.ids.size,
            )
        }
    }

    @Test
    fun `the format rejects what it is meant to reject`() {
        listOf(
            "dither.Floyd-Steinberg",   // upper case
            "dither.floyd_steinberg",   // underscore
            "floyd-steinberg",          // no category
            "dither.floyd.steinberg",   // two dots
            "dither.",                  // no name
            "dither.floyd steinberg",   // space
            "dither.floyd--steinberg",  // empty word
            "dither.-floyd",            // leading hyphen
            "",
        ).forEach { assertTrue("\"$it\" should not be a valid id", !WireId.isValid(it)) }
    }

    // --- what the ids resolve to ------------------------------------------------------

    @Test
    fun `every id resolves back to the value it was written for`() {
        tables.forEach { table ->
            table.ids.forEach { (value, id) -> assertEquals(value, table.of(id)) }
        }
    }

    /** Presets written before the ids named their constants, and must still read. */
    @Test
    fun `every legacy enum name still resolves to the same value`() {
        tables.forEach { table ->
            table.ids.keys.forEach { value -> assertEquals(value, table.of(value.name)) }
        }
    }

    @Test
    fun `an unknown id is refused rather than resolved to something else`() {
        tables.forEach { table ->
            val unknown = "${table.category}.nothing-of-the-sort"
            runCatching { table.of(unknown) }.fold(
                onSuccess = { fail("${table.category} resolved an unknown id to $it") },
                onFailure = {
                    val thrown = it as? UnknownWireIdException
                        ?: return@fold fail("${table.category} threw ${it::class.simpleName}")
                    assertEquals(table.category, thrown.category)
                    assertEquals(unknown, thrown.id)
                },
            )
        }
    }

    @Test
    fun `a legacy name is migrated to its id and an id is left alone`() {
        assertEquals("dither.floyd-steinberg", DitherModeIds.migrated("FLOYD_STEINBERG"))
        assertEquals(null, DitherModeIds.migrated("dither.floyd-steinberg"))
        assertEquals(null, DitherModeIds.migrated("dither.from-a-later-build"))
    }

    // --- what actually reaches the file ------------------------------------------------

    @Test
    fun `the enums serialise as their ids`() {
        assertEquals("\"render.pixel-dither\"", json.encodeToString(RenderMode.serializer(), RenderMode.PurePixel))
        assertEquals("\"dither.atkinson\"", json.encodeToString(DitherMode.serializer(), DitherMode.ATKINSON))
        assertEquals("\"effect.glow\"", json.encodeToString(EffectId.serializer(), EffectId.GLOW))
    }

    @Test
    fun `an id read back is the value that was written`() {
        DitherMode.entries.forEach { mode ->
            val text = json.encodeToString(DitherMode.serializer(), mode)
            assertEquals(mode, json.decodeFromString(DitherMode.serializer(), text))
        }
    }
}
