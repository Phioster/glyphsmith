package org.phioster.glyphsmith.data

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The shelf imported palettes sit on, against a real file in a real `filesDir`.
 *
 * Robolectric for the same reason [PresetStoreTest] uses it and no other reason: the format is
 * plain Kotlin and tested as such below, but the part that actually *keeps* someone's colours
 * touches a file, and a store that silently loses them is exactly the failure worth a slower
 * test. Everything here is about what survives a save and a reload.
 */
@RunWith(RobolectricTestRunner::class)
class PaletteStoreTest {

    private lateinit var store: PaletteStore
    private lateinit var file: File

    private fun palette(name: String, vararg colors: String) =
        PaletteFile(name, colors.toList().ifEmpty { listOf("#101010", "#F0F0F0") })

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = File(context.filesDir, "palettes.json")
        file.delete()
        store = PaletteStore(context)
    }

    @Test
    fun `nothing imported yet is an empty shelf, not a failure`() {
        assertEquals(emptyList<PaletteFile>(), store.load())
    }

    @Test
    fun `what is kept comes back`() {
        store.add(listOf(palette("dusk", "#221133", "#EEDDAA")))

        val loaded = store.load()

        assertEquals(1, loaded.size)
        assertEquals("dusk", loaded.single().name)
        assertEquals(listOf("#221133", "#EEDDAA"), loaded.single().colors)
    }

    @Test
    fun `a whole pack lands in one go`() {
        store.add(listOf(palette("one"), palette("two"), palette("three")))

        assertEquals(listOf("one", "two", "three"), store.load().map { it.name })
    }

    /**
     * The case that decides whether the shelf is usable a month in: re-importing a corrected file
     * has to update what is there. Two entries a letter apart is how a library becomes unusable.
     */
    @Test
    fun `importing the same name again replaces it rather than duplicating it`() {
        store.add(listOf(palette("dusk", "#111111")))
        store.add(listOf(palette("DUSK", "#222222", "#333333")))

        val loaded = store.load()

        assertEquals(1, loaded.size)
        assertEquals("DUSK", loaded.single().name)
        assertEquals(listOf("#222222", "#333333"), loaded.single().colors)
    }

    @Test
    fun `a pack that repeats a name keeps one of it`() {
        store.add(listOf(palette("twice", "#111111"), palette("twice", "#222222")))

        val loaded = store.load()

        assertEquals(1, loaded.size)
        assertEquals(listOf("#222222"), loaded.single().colors)
    }

    @Test
    fun `a replaced palette moves to the end, the others keep their order`() {
        store.add(listOf(palette("a"), palette("b"), palette("c")))
        store.add(listOf(palette("a", "#ABCDEF")))

        assertEquals(listOf("b", "c", "a"), store.load().map { it.name })
    }

    @Test
    fun `forgetting one leaves the rest`() {
        store.add(listOf(palette("a"), palette("b"), palette("c")))

        val kept = store.remove("B")

        assertEquals(listOf("a", "c"), kept.map { it.name })
        assertEquals(listOf("a", "c"), store.load().map { it.name })
    }

    @Test
    fun `forgetting something that was never there is not an error`() {
        store.add(listOf(palette("a")))

        assertEquals(listOf("a"), store.remove("nothing of the sort").map { it.name })
    }

    @Test
    fun `an empty palette is not kept`() {
        store.add(listOf(PaletteFile("hollow", emptyList()), palette("real")))

        assertEquals(listOf("real"), store.load().map { it.name })
    }

    @Test
    fun `adding nothing usable leaves the shelf as it was`() {
        store.add(listOf(palette("keep me")))

        assertEquals(listOf("keep me"), store.add(listOf(PaletteFile("hollow", emptyList()))).map { it.name })
        assertEquals(listOf("keep me"), store.load().map { it.name })
    }

    /** A file somebody hand-edited into nonsense should cost them that file, not the shelf. */
    @Test
    fun `a corrupt file reads as an empty shelf`() {
        file.writeText("{ this is not json at all")

        assertEquals(emptyList<PaletteFile>(), store.load())
    }

    @Test
    fun `the category a file carries survives the round trip`() {
        store.add(listOf(PaletteFile("greens", listOf("#0F0"), category = "SEASONAL")))
        // Three-digit hex is not a colour this format reads, but the entry is still kept: the
        // store's job is to hold what it was given, and colour parsing is the renderer's.
        assertEquals("SEASONAL", store.load().single().category)
    }
}
