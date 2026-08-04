package org.phioster.glyphsmith.pipeline

import org.phioster.glyphsmith.core.color.PaletteProviders
import org.phioster.glyphsmith.core.dither.DitherProviders
import org.phioster.glyphsmith.core.provider.Provider
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.provider.Registry
import org.phioster.glyphsmith.effects.EffectProviders
import org.phioster.glyphsmith.render.RenderModules

/**
 * Every provider registry in the application, in one place.
 *
 * It lives here because this is the only package allowed to see all of them at once: the
 * registries themselves sit with the code they describe, so that `core` need not know what a
 * render module is and `render` need not know what a glyph is.
 *
 * The point of gathering them is that the rules about ids can then be stated once, over
 * *everything*, instead of over a list somebody has to remember to extend. Before this, the id
 * tests enumerated three serialisers by hand; a fourth would have been born untested. Here a
 * category that is not registered fails a test rather than going quietly uncovered.
 */
object Providers {

    val all: List<Registry<out Provider>> = listOf(RenderModules, DitherProviders, EffectProviders, PaletteProviders)

    /** Every provider this build ships, whatever kind it is. */
    val everything: List<Provider> get() = all.flatMap { it.all }

    fun registryFor(category: ProviderCategory): Registry<out Provider> =
        all.first { it.category == category }

    /**
     * The provider [id] names, whichever category it belongs to, or null if none does.
     *
     * The id carries its own category, so nothing has to be told where to look.
     */
    fun find(id: String): Provider? = all.firstNotNullOfOrNull { it.find(id) }
}
