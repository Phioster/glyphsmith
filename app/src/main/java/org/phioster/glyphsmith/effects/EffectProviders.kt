package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.core.provider.Provider
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.provider.Registry

/**
 * One pass in the effect chain, described rather than implemented.
 *
 * The pass is [EffectId] and the code behind it. Note that the registry's order is the enum's
 * order, which is also the chain's default order — [EffectStack.order] is what a preset actually
 * carries, and nothing here changes it.
 */
class EffectProvider(val effect: EffectId) : Provider {
    override val id: String = EffectIds.idOf(effect)
    override val displayName: String = effect.label
    override val category = ProviderCategory.EFFECT
}

/** Every effect pass this build ships, in the chain's default order. */
object EffectProviders : Registry<EffectProvider>(
    ProviderCategory.EFFECT,
    EffectId.entries.map(::EffectProvider),
) {
    fun of(effect: EffectId): EffectProvider = all.first { it.effect == effect }
}
