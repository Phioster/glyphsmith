package org.phioster.glyphsmith.core.dither

import org.phioster.glyphsmith.core.provider.Provider
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.provider.Registry

/**
 * A dither algorithm, described rather than implemented.
 *
 * The algorithm itself is [DitherMode] and the code behind it, untouched. This adds the one
 * thing the enum had no room for: a uniform way to ask what it is called, what it is filed
 * under and what it is called on disk, without switching over the enum to find out.
 */
class DitherProvider(val mode: DitherMode) : Provider {
    override val id: String = DitherModeIds.idOf(mode)
    override val displayName: String = mode.label
    override val category = ProviderCategory.DITHER

    /** Which family the algorithm belongs to — the shelf the picker groups it under. */
    val family: DitherCategory = mode.category
}

/** Every dither algorithm this build ships. */
object DitherProviders : Registry<DitherProvider>(
    ProviderCategory.DITHER,
    DitherMode.entries.map(::DitherProvider),
) {
    private val byMode = all.associateBy { it.mode }
    private val byFamily = all.groupBy { it.family }

    /** The provider describing [mode]. Indexed rather than scanned: the picker asks per frame. */
    fun of(mode: DitherMode): DitherProvider = byMode.getValue(mode)

    /** The algorithms in one family, in the order they are declared. */
    fun inFamily(family: DitherCategory): List<DitherProvider> = byFamily[family].orEmpty()
}
