package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.core.provider.Provider
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.provider.Registry

/**
 * One pass in the effect chain: what it is called, and how it runs.
 *
 * It began as description only — the id and the label — while two `when (EffectId)` blocks
 * elsewhere did the work. [pass] is the half that was missing: the provider now carries the
 * implementation rather than pointing at a constant that the chain and the stack each had to
 * recognise separately.
 *
 * Note that the registry's order is the enum's order, which is also the chain's default order —
 * [EffectStack.order] is what a preset actually carries, and nothing here changes it.
 */
class EffectProvider(val effect: EffectId) : Provider {
    override val id: String = EffectIds.idOf(effect)
    override val displayName: String = effect.label
    override val category = ProviderCategory.EFFECT

    /** What the pass reads and writes, what switches it on, how it rolls, and the code. */
    val pass: EffectPass<*> = EffectPasses.of(effect)
}

/** Every effect pass this build ships, in the chain's default order. */
object EffectProviders : Registry<EffectProvider>(
    ProviderCategory.EFFECT,
    EffectId.entries.map(::EffectProvider),
) {
    /**
     * Indexed by ordinal rather than scanned: this is on the render path now, because asking
     * whether a pass is switched on means asking the provider for it first.
     */
    private val bySlot: Array<EffectProvider> =
        EffectId.entries.map { effect -> all.first { it.effect == effect } }.toTypedArray()

    fun of(effect: EffectId): EffectProvider = bySlot[effect.ordinal]

    /**
     * The effects that offer themselves to a random roll, in the chain's default order.
     *
     * This is the whole of what Surprise Me knows about effects. It used to be a `when` over all
     * seventeen slots in `state/RandomLook` — a file outside the effect category — which meant a
     * new effect either came with an edit there or was silently unreachable by the button. An
     * effect declares its own roll now, and appears here by declaring it.
     */
    val randomisable: List<EffectProvider> = all.filter { it.pass.isRandomisable }
}
