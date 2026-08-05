package org.phioster.glyphsmith.ui.panels

import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PresetStore

/**
 * What the preset list is narrowed to.
 *
 * Typed rather than a nullable string, for the reason [Shelf] is: "no filter" and "the category
 * called nothing" are different answers, and a string cannot tell them apart. [Favourites] is
 * not a category either — a starred preset keeps whatever shelf it was filed under.
 */
sealed interface PresetFilter {

    /** Everything, which is what the list did before there was a filter at all. */
    data object All : PresetFilter

    data object Favourites : PresetFilter

    /** A stored category token — see [PresetStore.categories]. */
    data class Category(val id: String) : PresetFilter
}

/**
 * The rule behind the filter row, kept out of the composable so it can be tested without a UI.
 *
 * The library reached ninety-odd presets across eleven shelves and the picker was one sorted
 * list. Everything here exists to make that list shorter without making anything unreachable.
 */
object PresetFilters {

    /**
     * The chips worth offering for [presets].
     *
     * Derived from the list rather than from [PresetStore.categories], and that is the whole
     * design: a chip that selects nothing is a dead control, so a shelf appears exactly when it
     * holds something. A category somebody invents by saving a preset under it turns up on its
     * own, and one they empty disappears again.
     *
     * Order is [PresetFilter.All], then favourites when anything is starred, then the shipped
     * shelf order, then anything unknown by name — so the row reads the same way the list below
     * it sorts.
     */
    fun available(presets: List<Preset>): List<PresetFilter> {
        if (presets.isEmpty()) return listOf(PresetFilter.All)

        val known = PresetStore.categories.filter { category ->
            presets.any { it.category == category }
        }
        val unknown = presets.map { it.category }
            .filterNot { it in PresetStore.categories }
            .distinct()
            .sorted()

        return buildList {
            add(PresetFilter.All)
            if (presets.any { it.favourite }) add(PresetFilter.Favourites)
            (known + unknown).forEach { add(PresetFilter.Category(it)) }
        }
    }

    /** [presets] narrowed to [filter]. */
    fun apply(presets: List<Preset>, filter: PresetFilter): List<Preset> = when (filter) {
        PresetFilter.All -> presets
        PresetFilter.Favourites -> presets.filter { it.favourite }
        is PresetFilter.Category -> presets.filter { it.category == filter.id }
    }

    /**
     * [filter] if it still selects something, and [PresetFilter.All] if it does not.
     *
     * The case this exists for: filter to favourites, then un-star the last one. The chip is
     * gone from the row but the list is still narrowed by it, so the panel shows nothing and
     * offers no way back — the control that would clear the filter is the one that vanished.
     * Asking this on every draw means the row and the list can never disagree.
     */
    fun resolve(presets: List<Preset>, filter: PresetFilter): PresetFilter =
        if (filter in available(presets)) filter else PresetFilter.All

    /** What the chip says. Short, because eleven of them share one row. */
    fun label(filter: PresetFilter): String = when (filter) {
        PresetFilter.All -> "all"
        PresetFilter.Favourites -> "★"
        is PresetFilter.Category -> PresetStore.label(filter.id)
    }

    /** How many presets a chip would select, for the count beside its label. */
    fun count(presets: List<Preset>, filter: PresetFilter): Int = apply(presets, filter).size
}
