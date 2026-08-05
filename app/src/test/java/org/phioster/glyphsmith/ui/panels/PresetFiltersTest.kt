package org.phioster.glyphsmith.ui.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PresetLibrary
import org.phioster.glyphsmith.data.PresetStore
import org.phioster.glyphsmith.render.RenderSettings

/**
 * The filter row's rule.
 *
 * Two things can go wrong with a filter and neither is visible in a screenshot: it can offer a
 * chip that selects nothing, and it can stay applied after the thing it selected is gone. The
 * second is the worse one, because the control that would clear it is the chip that vanished.
 */
class PresetFiltersTest {

    private fun preset(name: String, category: String, favourite: Boolean = false) =
        Preset(name, RenderSettings(), category, favourite)

    private val library = PresetLibrary.builtIns

    @Test
    fun `an empty library still offers a way to see everything`() {
        assertEquals(listOf(PresetFilter.All), PresetFilters.available(emptyList()))
    }

    /** A chip that selects nothing is a dead control, so a shelf appears only when it holds one. */
    @Test
    fun `only the shelves that hold something are offered`() {
        val presets = listOf(
            preset("a", PresetStore.CATEGORY_MOTION),
            preset("b", PresetStore.CATEGORY_GLITCH),
        )

        val offered = PresetFilters.available(presets)

        assertTrue(PresetFilter.Category(PresetStore.CATEGORY_MOTION) in offered)
        assertTrue(PresetFilter.Category(PresetStore.CATEGORY_GLITCH) in offered)
        assertTrue(PresetFilter.Category(PresetStore.CATEGORY_PRINT) !in offered)
    }

    @Test
    fun `the favourites chip appears only when something is starred`() {
        val plain = listOf(preset("a", PresetStore.CATEGORY_MOTION))
        val starred = listOf(preset("a", PresetStore.CATEGORY_MOTION, favourite = true))

        assertTrue(PresetFilter.Favourites !in PresetFilters.available(plain))
        assertTrue(PresetFilter.Favourites in PresetFilters.available(starred))
    }

    /** Every chip has to select at least one, or the row is lying about what is there. */
    @Test
    fun `no offered chip selects nothing`() {
        PresetFilters.available(library).forEach { filter ->
            assertTrue(
                "${PresetFilters.label(filter)} selects nothing",
                PresetFilters.apply(library, filter).isNotEmpty(),
            )
        }
    }

    /** And nothing is unreachable: every preset is behind at least one chip besides `all`. */
    @Test
    fun `every preset is reachable through a chip of its own`() {
        val shelves = PresetFilters.available(library).filterNot { it == PresetFilter.All }

        library.forEach { preset ->
            assertTrue(
                "${preset.name} is only reachable through 'all'",
                shelves.any { preset in PresetFilters.apply(library, it) },
            )
        }
    }

    /** A category somebody invents by saving under it turns up on its own. */
    @Test
    fun `an unknown category is offered too, after the shipped ones`() {
        val presets = listOf(
            preset("a", PresetStore.CATEGORY_MOTION),
            preset("b", "MY OWN SHELF"),
        )

        val offered = PresetFilters.available(presets)

        assertEquals(PresetFilter.Category("MY OWN SHELF"), offered.last())
    }

    @Test
    fun `the row reads in the order the list sorts`() {
        val offered = PresetFilters.available(library)

        assertEquals(PresetFilter.All, offered.first())
        val categories = offered.filterIsInstance<PresetFilter.Category>().map { it.id }
        assertEquals(categories.sortedBy { PresetStore.categories.indexOf(it) }, categories)
    }

    // --- the one that is not visible in a screenshot ------------------------------------

    /**
     * Filter to favourites, un-star the last one, and the chip is gone while the list is still
     * narrowed by it — an empty panel with no control to clear it.
     */
    @Test
    fun `a filter whose chip has gone falls back to everything`() {
        val starred = listOf(preset("a", PresetStore.CATEGORY_MOTION, favourite = true))
        val plain = listOf(preset("a", PresetStore.CATEGORY_MOTION))

        assertEquals(PresetFilter.Favourites, PresetFilters.resolve(starred, PresetFilter.Favourites))
        assertEquals(PresetFilter.All, PresetFilters.resolve(plain, PresetFilter.Favourites))
    }

    @Test
    fun `a category that has been emptied falls back too`() {
        val presets = listOf(preset("a", PresetStore.CATEGORY_MOTION))

        assertEquals(
            PresetFilter.All,
            PresetFilters.resolve(presets, PresetFilter.Category(PresetStore.CATEGORY_PRINT)),
        )
    }

    @Test
    fun `resolving is stable for a filter that still selects something`() {
        PresetFilters.available(library).forEach { filter ->
            assertEquals(filter, PresetFilters.resolve(library, filter))
        }
    }

    // --- what the chips select ----------------------------------------------------------

    @Test
    fun `all selects the whole library and the counts add up`() {
        assertEquals(library, PresetFilters.apply(library, PresetFilter.All))

        val shelves = PresetFilters.available(library)
            .filterIsInstance<PresetFilter.Category>()

        assertEquals(
            "the shelves do not partition the library",
            library.size,
            shelves.sumOf { PresetFilters.count(library, it) },
        )
    }

    @Test
    fun `a category chip selects exactly that category`() {
        val motion = PresetFilters.apply(library, PresetFilter.Category(PresetStore.CATEGORY_MOTION))

        assertTrue(motion.isNotEmpty())
        assertTrue(motion.all { it.category == PresetStore.CATEGORY_MOTION })
    }

    /** The label is what a user reads on a phone-width row, so it stays short. */
    @Test
    fun `every chip has a short label`() {
        PresetFilters.available(library).forEach { filter ->
            val label = PresetFilters.label(filter)
            assertTrue("a chip has no label", label.isNotBlank())
            assertTrue("'$label' is too long for the row", label.length <= 16)
        }
    }
}
