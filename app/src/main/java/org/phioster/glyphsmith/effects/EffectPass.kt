package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext

/**
 * What one effect pass is, mechanically.
 *
 * Until now the answer was split in two, and both halves were `when (EffectId)` blocks a long way
 * from the effect they described: one in [EffectNodes] saying which object runs, one in
 * [EffectStack] saying which flag switches it on. Nothing tied the two together, so a slot could
 * run one effect and read another's toggle and still compile — the picture would simply refuse to
 * change and no test would say why.
 *
 * Here the three things a pass needs are one declaration: the slice of the stack it is configured
 * by, the flag in that slice, and the code. It is written inside the effect's own object, which is
 * what makes the wiring checkable by the compiler rather than by eye — [P] is pinned by whatever
 * `apply` takes, and no two effects share a params type.
 *
 * [P] appears only in the private members, so a caller holding an `EffectPass<*>` can still ask it
 * everything below. That is the point of erasing it: the chain runs seventeen passes with
 * seventeen unrelated params types and must not know a single one of them.
 */
class EffectPass<P : Any>(
    /** This pass's own slice of the stack — the params object its panel writes to. */
    private val select: (EffectStack) -> P,
    /** The flag in that slice which switches the pass on. */
    private val isOn: (P) -> Boolean,
    /**
     * The pass itself.
     *
     * Takes the [RenderContext] whether or not it reads it: the newer effects need the clock and
     * the output budget, the older ones write `_` and say so in one character.
     */
    private val run: (Pixels, P, RenderContext) -> Pixels,
) {

    /** The params [stack] holds for this pass. */
    fun paramsIn(stack: EffectStack): P = select(stack)

    /** Whether this pass would do anything, as [stack] is configured. */
    fun enabledIn(stack: EffectStack): Boolean = isOn(select(stack))

    /**
     * Runs the pass over [pixels] with the settings in [stack].
     *
     * Does not consult [enabledIn] first. The chain skips a disabled node rather than letting it
     * early-return — see [org.phioster.glyphsmith.core.pipeline.NodePipeline] — and every effect
     * checks its own flag anyway, so a caller that wants the check has one to call.
     */
    fun apply(pixels: Pixels, stack: EffectStack, ctx: RenderContext): Pixels =
        run(pixels, select(stack), ctx)
}
