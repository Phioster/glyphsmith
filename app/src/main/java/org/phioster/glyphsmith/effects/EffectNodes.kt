package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.core.pipeline.ImageProcessorNode
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.core.image.Pixels

/**
 * A node built from one entry of an [EffectStack].
 *
 * The effect itself is a lambda rather than a subclass per effect: they all share the shape
 * `(Pixels, Params) -> Pixels`, so there is nothing for a dozen-odd classes to say that this one
 * cannot. The [enabled] flag is read once when the node is built, which is correct because a node
 * lives exactly as long as the render it was built for.
 *
 * The lambda takes the [RenderContext] as well, because the newer effects need the clock and the
 * output budget from it. The older ones ignore it.
 */
private class EffectNode(
    override val id: String,
    override val enabled: Boolean,
    private val apply: (Pixels, RenderContext) -> Pixels,
) : ImageProcessorNode {
    override fun process(input: Pixels, ctx: RenderContext): Pixels = apply(input, ctx)
}

/**
 * Turns an [EffectStack] into the node list the chain runs.
 *
 * The order comes from [EffectStack.effectiveOrder], which already handles a preset written
 * before an effect existed. What each slot *does* is no longer stated here: this file used to
 * carry a `when` naming all seventeen implementations, and now it asks the provider, which
 * carries the pass. What is left is the adaptation — a pass and a stack become a node — which is
 * the only thing this file was ever really about.
 */
object EffectNodes {

    fun of(stack: EffectStack): List<ImageProcessorNode> =
        stack.effectiveOrder().map { id -> nodeFor(EffectProviders.of(id), stack) }

    private fun nodeFor(provider: EffectProvider, stack: EffectStack): ImageProcessorNode =
        EffectNode(provider.effect.name, provider.pass.enabledIn(stack)) { pixels, ctx ->
            provider.pass.apply(pixels, stack, ctx)
        }
}
