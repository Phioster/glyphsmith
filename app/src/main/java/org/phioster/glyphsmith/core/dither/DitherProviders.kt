package org.phioster.glyphsmith.core.dither

import org.phioster.glyphsmith.core.provider.Provider
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.provider.Registry

/**
 * A dither algorithm: what it is called, where it is filed, and how it runs.
 *
 * It began as description only — the enum and a dozen `when (mode)` dispatches did the work.
 * [algorithm] is the half that was missing: the provider now carries the implementation rather
 * than pointing at a constant that twelve other files had to recognise.
 */
class DitherProvider(val mode: DitherMode) : Provider {
    override val id: String = DitherModeIds.idOf(mode)
    override val displayName: String = mode.label
    override val category = ProviderCategory.DITHER

    /** Which family the algorithm belongs to — the shelf the picker groups it under. */
    val family: DitherCategory = mode.category

    /** How it decides a cell. See [DitherAlgorithm]. */
    val algorithm: DitherAlgorithm = DitherAlgorithms.of(mode)
}

/** Every dither algorithm this build ships. */
object DitherProviders : Registry<DitherProvider>(
    ProviderCategory.DITHER,
    DitherMode.entries.map(::DitherProvider),
) {
    /**
     * Indexed by ordinal rather than hashed: this is now on the per-cell path, because asking a
     * style for its threshold means asking the provider for its algorithm first.
     */
    private val byMode: Array<DitherProvider> =
        DitherMode.entries.map { mode -> all.first { it.mode == mode } }.toTypedArray()

    private val byFamily = all.groupBy { it.family }

    /** The provider describing [mode]. Indexed rather than scanned: the picker asks per frame. */
    fun of(mode: DitherMode): DitherProvider = byMode[mode.ordinal]

    /** The algorithms in one family, in the order they are declared. */
    fun inFamily(family: DitherCategory): List<DitherProvider> = byFamily[family].orEmpty()
}
