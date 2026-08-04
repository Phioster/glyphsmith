package org.phioster.glyphsmith.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderSettings
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The store itself, against a real file in a real `filesDir`.
 *
 * Everything around it was already tested — [PresetSchema] for the format, [PresetLibrary] for
 * what ships, [Import] for what an import reports — because all three are free of Android. The
 * store was not, and so the part that actually *keeps* people's work had no tests at all: not
 * the round trip, not the merge, and not `reset()`, which is the one operation that can destroy
 * a library. It could not even be checked on a device, since running it there would have meant
 * wiping a real one.
 *
 * That is the whole reason Robolectric is here. It is deliberately not used anywhere it is not
 * needed: a plain JVM test runs in milliseconds, and most of this codebase can have one.
 */
@RunWith(RobolectricTestRunner::class)
class PresetStoreTest {

    private lateinit var store: PresetStore
    private lateinit var file: File

    private fun preset(name: String, cell: Int = 4, category: String = PresetStore.CATEGORY_CUSTOM) =
        Preset(name, RenderSettings.newSession().copy(cellSize = cell), category)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        file = File(context.filesDir, "presets.json")
        file.delete()
        store = PresetStore(context)
    }

    // --- a fresh install ----------------------------------------------------------------

    @Test
    fun `a store with no file hands back the shipped library`() {
        assertTrue("the store wrote a file it was never asked to write", !file.exists())
        assertEquals(PresetLibrary.builtIns, store.load())
    }

    @Test
    fun `what is saved is what comes back`() {
        val mine = listOf(preset("mine"), preset("other", cell = 12))

        store.save(mine)

        assertEquals(mine, store.load())
    }

    /**
     * A half-written file must not cost the library. The store falls back rather than throwing,
     * which is the behaviour a truncated write or a hand-edited export depends on.
     */
    @Test
    fun `a corrupt file falls back to the shipped library`() {
        file.writeText("{ this is not json")

        assertEquals(PresetLibrary.builtIns, store.load())
    }

    @Test
    fun `an empty library falls back rather than showing nothing`() {
        file.writeText("""{"schemaVersion":2,"presets":[]}""")

        assertEquals(PresetLibrary.builtIns, store.load())
    }

    // --- saving one --------------------------------------------------------------------

    @Test
    fun `upsert files a new preset by what it is`() {
        // Seeded rather than emptied: an empty library is not a state the store has, since
        // load() reads one as "nothing saved yet" and hands back what ships.
        store.save(listOf(preset("seed")))

        val after = store.upsert(
            "ordered thing",
            RenderSettings.newSession().copy(ditherMode = DitherMode.BAYER_4),
        )
        val saved = after.first { it.name == "ordered thing" }

        assertEquals(PresetStore.CATEGORY_ORDERED, saved.category)
        assertEquals("the seed should still be there", 2, after.size)
    }

    /**
     * Overwriting keeps where a preset sits and whether it was starred. Re-saving a favourite
     * should not quietly demote it, and a category its owner chose outranks anything derived.
     */
    @Test
    fun `overwriting keeps the shelf, the star and the description`() {
        store.save(
            listOf(
                Preset(
                    name = "mine",
                    params = RenderSettings.newSession(),
                    category = PresetStore.CATEGORY_PRINT,
                    favourite = true,
                    description = "why this look",
                ),
            ),
        )

        val saved = store.upsert("mine", RenderSettings.newSession().copy(cellSize = 20)).single()

        assertEquals(PresetStore.CATEGORY_PRINT, saved.category)
        assertTrue("re-saving a favourite demoted it", saved.favourite)
        assertEquals("why this look", saved.description)
        assertEquals("the settings should be the new ones", 20, saved.params.cellSize)
    }

    @Test
    fun `a name that differs only in case overwrites rather than duplicating`() {
        store.save(listOf(preset("Mine")))

        val after = store.upsert("mine", RenderSettings.newSession().copy(cellSize = 20))

        assertEquals(1, after.size)
        assertEquals(20, after.single().params.cellSize)
    }

    @Test
    fun `renaming keeps the settings and the star`() {
        store.save(listOf(preset("before").copy(favourite = true)))

        val renamed = store.rename("before", "after", "a reason").single()

        assertEquals("after", renamed.name)
        assertTrue(renamed.favourite)
        assertEquals("a reason", renamed.description)
        assertEquals(4, renamed.params.cellSize)
    }

    @Test
    fun `starring and deleting survive a reload`() {
        store.save(listOf(preset("a"), preset("b")))

        store.toggleFavourite("a")
        assertTrue(store.load().first { it.name == "a" }.favourite)

        store.delete("b")
        assertEquals(listOf("a"), store.load().map { it.name })
    }

    // --- reset --------------------------------------------------------------------------

    /**
     * The operation no device test can safely perform, since running it on a real phone means
     * destroying a real library to find out what it does.
     */
    @Test
    fun `reset restores the shipped library and discards what was saved`() {
        store.save(listOf(preset("mine"), preset("other")))
        assertTrue(file.exists())

        val after = store.reset()

        assertEquals(PresetLibrary.builtIns, after)
        assertEquals("a reset that is not visible on the next load is not a reset", PresetLibrary.builtIns, store.load())
        assertTrue("the stored file outlived the reset", !file.exists())
    }

    // --- import -------------------------------------------------------------------------

    @Test
    fun `an import merges by name instead of duplicating`() {
        store.save(listOf(preset("shared", cell = 4), preset("kept", cell = 5)))
        val incoming = PresetSchema.encode(listOf(preset("shared", cell = 30)))

        val imported = store.importJson(incoming)!!

        assertEquals(setOf("kept", "shared"), imported.presets.map { it.name }.toSet())
        assertEquals(30, imported.presets.first { it.name == "shared" }.params.cellSize)
        assertEquals(1, imported.added)
        assertTrue(imported.skipped.isEmpty())
    }

    @Test
    fun `an import is on disk before anyone reloads`() {
        store.save(listOf(preset("seed")))

        store.importJson(PresetSchema.encode(listOf(preset("incoming"))))

        assertEquals(listOf("seed", "incoming"), store.load().map { it.name })
    }

    @Test
    fun `a file that is not a preset export is refused`() {
        store.save(listOf(preset("mine")))

        assertNull(store.importJson("{ this is not json"))
        assertNull(store.importJson("""{"unrelated":true}"""))
        assertEquals("a refused import must not touch the library", listOf("mine"), store.load().map { it.name })
    }

    /**
     * The case this build cannot read: an entry naming a style from a newer one. It is reported,
     * not swallowed, and — the part that matters — it is never written into the stored library.
     */
    @Test
    fun `an entry this build cannot read is reported and not stored`() {
        store.save(listOf(preset("mine")))
        val incoming = """
            {"schemaVersion":2,"presets":[
              {"name":"readable","category":"CUSTOM","params":{"renderMode":"render.pixel-dither"}},
              {"name":"from tomorrow","category":"CUSTOM",
               "params":{"renderMode":"render.pixel-dither","ditherMode":"dither.not-invented-yet"}}
            ]}
        """.trimIndent()

        val imported = store.importJson(incoming)!!

        assertEquals(1, imported.added)
        assertEquals(listOf("from tomorrow"), imported.skipped.map { it.name })
        assertTrue(
            "an unreadable entry was written into the library",
            store.load().none { it.name == "from tomorrow" },
        )
        assertTrue("the readable one should have arrived", store.load().any { it.name == "readable" })
        assertTrue(imported.summary().contains("not known to this build"))
    }

    @Test
    fun `a document nothing can be read from still counts as an import`() {
        store.save(listOf(preset("mine")))
        val incoming = """
            {"schemaVersion":2,"presets":[
              {"name":"from tomorrow","category":"CUSTOM",
               "params":{"renderMode":"render.pixel-dither","ditherMode":"dither.not-invented-yet"}}
            ]}
        """.trimIndent()

        val imported = store.importJson(incoming)
        assertNotNull("this is a preset export, merely an unreadable one", imported)
        assertEquals(0, imported!!.added)
        assertEquals(listOf("mine"), store.load().map { it.name })
    }

    // --- the round trip that matters ----------------------------------------------------

    /**
     * Export and re-import the whole shipped library through a real file. This is the closest
     * thing to what a user actually does with the transfer buttons, and it exercises the format
     * end to end rather than in a string held in memory.
     */
    @Test
    fun `the shipped library survives an export and a re-import`() {
        val exported = store.exportJson()
        store.save(listOf(preset("mine")))

        val imported = store.importJson(exported)!!

        assertTrue(imported.skipped.isEmpty())
        assertEquals(PresetLibrary.builtIns.size, imported.added)
        val byName = store.load().associateBy { it.name }
        PresetLibrary.builtIns.forEach { original ->
            assertEquals("${original.name} did not survive the round trip", original, byName[original.name])
        }
        assertTrue("the user's own preset was dropped", byName.containsKey("mine"))
    }

    @Test
    fun `a legacy preset still loads as glyph art`() {
        file.writeText(
            """{"presets":[{"name":"old","params":{"charSetId":"ascii-standard-10","cellSize":7}}]}""",
        )

        val loaded = store.load().single()

        assertEquals("old", loaded.name)
        assertEquals(RenderMode.GlyphMatrix, loaded.params.renderMode)
        assertEquals(7, loaded.params.cellSize)
    }
}
