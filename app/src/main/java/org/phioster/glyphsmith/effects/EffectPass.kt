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
 * Here everything a pass needs is one declaration: the slice of the stack it is configured by, how
 * that slice is written back, the flag in it, how it is rolled at random, and the code. It is
 * written inside the effect's own object, which is what makes the wiring checkable by the compiler
 * rather than by eye — [P] is pinned by whatever `apply` takes, and no two effects share a params
 * type.
 *
 * [P] appears only in the private members, so a caller holding an `EffectPass<*>` can still ask it
 * everything below. That is the point of erasing it: the chain runs seventeen passes with
 * seventeen unrelated params types and must not know a single one of them. It is also what lets
 * [rolled] exist — Surprise Me can roll an effect it cannot name, which is why that roll is here
 * and no longer a seventeen-branch `when` in `state/RandomLook`.
 */
class EffectPass<P : Any>(
    /** This pass's own slice of the stack — the params object its panel writes to. */
    private val select: (EffectStack) -> P,
    /**
     * The same slice, written back.
     *
     * The other half of [select], and stated beside it so the two cannot address different
     * fields. It is what lets anything hold an `EffectPass<*>` and still *change* the effect
     * rather than only read it.
     */
    private val write: EffectStack.(P) -> EffectStack,
    /** The flag in that slice which switches the pass on. */
    private val isOn: (P) -> Boolean,
    /**
     * This effect's own contribution to Surprise Me, or null where a random roll of it is not
     * worth offering.
     *
     * A roll must switch the pass on — that is the contract, and `EffectCatalogTest` holds every
     * effect to it. Null is a real answer, not an oversight: an effect nobody would want rolled
     * blind simply declares none and the roll passes over it.
     */
    private val randomise: (P.(EffectRoll) -> P)? = null,
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

    /** Whether this pass offers itself to a random roll. See [rolled]. */
    val isRandomisable: Boolean get() = randomise != null

    /**
     * [stack] with this one effect rolled at random, or null where the pass declares no roll.
     *
     * Only this pass's own slice moves. Everything else in the stack is left exactly as it was,
     * which is what lets a caller roll two or three effects in a row and get all of them.
     */
    fun rolled(stack: EffectStack, roll: EffectRoll): EffectStack? =
        randomise?.let { stack.write(it(select(stack), roll)) }

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
