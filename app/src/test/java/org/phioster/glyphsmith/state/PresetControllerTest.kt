package org.phioster.glyphsmith.state

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.glyphsmith.data.PresetLibrary
import org.phioster.glyphsmith.data.PresetSchema
import org.phioster.glyphsmith.data.PresetStore
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.render.RenderSettings
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The library the interface holds, against the one on disk.
 *
 * The invariant is the whole point of the class: [PresetController.presets] must be what the
 * store would hand back, after every operation. Drift between the two is the failure that shows
 * up as a deletion that did not stick, or work reappearing after a restart — and it is exactly
 * the kind of thing that hides in a nine-hundred-line ViewModel, because reaching it meant
 * standing one up.
 *
 * Each test therefore checks the same thing twice: what the controller says it holds, and what
 * a *second* controller reading the same file finds.
 */
@RunWith(RobolectricTestRunner::class)
class PresetControllerTest {

    private lateinit var controller: PresetController
    private lateinit var file: File

    private fun context() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun settings(cell: Int = 4) = RenderSettings.newSession().copy(cellSize = cell)

    /** What is actually on disk, read by something that shares no state with the controller. */
    private fun onDisk(): List<Preset> = PresetStore(context()).load()

    private fun assertAgreesWithDisk() {
        assertEquals(
            "what the controller holds is not what the next launch would read",
            onDisk(),
            controller.presets,
        )
    }

    @Before
    fun setUp() {
        file = File(context().filesDir, "presets.json")
        file.delete()
        controller = PresetController(context())
    }

    // --- opening ------------------------------------------------------------------------

    @Test
    fun `a fresh install opens on the shipped library`() {
        assertEquals(PresetLibrary.builtIns, controller.presets)
    }

    @Test
    fun `an existing library is picked up rather than replaced`() {
        PresetStore(context()).save(listOf(Preset("mine", settings())))

        assertEquals(listOf("mine"), PresetController(context()).presets.map { it.name })
    }

    // --- every operation keeps the two in step ------------------------------------------

    @Test
    fun `saving adds a preset and stays in step with the file`() {
        val status = controller.save("mine", settings(cell = 12))

        assertEquals("preset saved", status)
        assertEquals(12, controller.presets.first { it.name == "mine" }.params.cellSize)
        assertAgreesWithDisk()
    }

    @Test
    fun `saving twice under one name overwrites rather than accumulating`() {
        controller.save("mine", settings(cell = 12))
        controller.save("mine", settings(cell = 30))

        assertEquals(1, controller.presets.count { it.name == "mine" })
        assertEquals(30, controller.presets.first { it.name == "mine" }.params.cellSize)
        assertAgreesWithDisk()
    }

    @Test
    fun `renaming keeps it in step`() {
        controller.save("before", settings())

        val status = controller.rename("before", "after", "a reason")

        assertEquals("preset renamed", status)
        assertTrue(controller.presets.none { it.name == "before" })
        assertEquals("a reason", controller.presets.first { it.name == "after" }.description)
        assertAgreesWithDisk()
    }

    @Test
    fun `deleting keeps it in step`() {
        controller.save("mine", settings())

        val status = controller.delete("mine")

        assertEquals("preset deleted", status)
        assertTrue(controller.presets.none { it.name == "mine" })
        assertAgreesWithDisk()
    }

    @Test
    fun `starring keeps it in step`() {
        controller.save("mine", settings())

        controller.toggleFavourite("mine")

        assertTrue(controller.presets.first { it.name == "mine" }.favourite)
        assertAgreesWithDisk()

        controller.toggleFavourite("mine")
        assertTrue(!controller.presets.first { it.name == "mine" }.favourite)
        assertAgreesWithDisk()
    }

    @Test
    fun `resetting puts the shipped library back and stays in step`() {
        controller.save("mine", settings())

        val status = controller.reset()

        assertEquals("presets reset to the built-in library", status)
        assertEquals(PresetLibrary.builtIns, controller.presets)
        assertAgreesWithDisk()
        assertTrue("the stored file outlived the reset", !file.exists())
    }

    // --- import -------------------------------------------------------------------------

    @Test
    fun `importing merges and stays in step`() {
        controller.save("mine", settings())
        val incoming = PresetSchema.encode(listOf(Preset("incoming", settings(cell = 20))))

        val status = controller.import(incoming)

        assertTrue(status, "1 imported" in status)
        assertTrue(controller.presets.any { it.name == "incoming" })
        assertTrue("the user's own preset was dropped", controller.presets.any { it.name == "mine" })
        assertAgreesWithDisk()
    }

    @Test
    fun `a refused import changes nothing`() {
        controller.save("mine", settings())
        val before = controller.presets

        val status = controller.import("{ this is not json")

        assertEquals("that file isn't a preset export", status)
        assertEquals("a refused import moved the library", before, controller.presets)
        assertAgreesWithDisk()
    }

    /** What this build cannot read is reported and does not reach the library. */
    @Test
    fun `an unreadable entry is reported and not stored`() {
        controller.save("mine", settings())
        val incoming = """
            {"schemaVersion":2,"presets":[
              {"name":"from tomorrow","category":"CUSTOM",
               "params":{"renderMode":"render.pixel-dither","ditherMode":"dither.not-invented-yet"}}
            ]}
        """.trimIndent()

        val status = controller.import(incoming)

        assertTrue(status, "not known to this build" in status)
        assertTrue(controller.presets.none { it.name == "from tomorrow" })
        assertAgreesWithDisk()
    }

    // --- export -------------------------------------------------------------------------

    @Test
    fun `what is exported is what is held`() {
        controller.save("mine", settings(cell = 12))

        val reread = PresetSchema.decode(controller.exportJson())

        assertEquals(controller.presets, reread)
    }
}
