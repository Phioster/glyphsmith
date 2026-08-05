package org.phioster.glyphsmith.ui.panels

import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectStack

/**
 * How the effects panel divides seventeen effects into a page worth scrolling.
 *
 * The panel used to draw all of them the same way: one bordered block each, in chain order,
 * every one carrying its toggle and a pair of reorder buttons. Two things were wrong with that
 * and only the first is obvious.
 *
 * Seventeen blocks is a long page when two of them are doing anything.
 *
 * The other is worse. The reorder buttons on a *disabled* effect change the stored order and
 * change nothing anybody can see — a control that silently edits the document and reports
 * nothing. The user finds out later, when they switch that effect on and it appears somewhere
 * they did not put it.
 *
 * So the chain is what is running, and everything else is a list of things that could run. An
 * effect switched on from that list appears in the chain at the position it already held —
 * [EffectStack.effectiveOrder] never stopped tracking it — which is why nothing is lost by not
 * offering to move it while it is off.
 *
 * Compose-free so the division can be tested without a UI host, on the pattern
 * [MappingSections] set.
 */
object EffectChain {

    /** The effects that are running, in the order they run. */
    fun running(stack: EffectStack): List<EffectId> =
        stack.effectiveOrder().filter { stack.enabledOf(it) }

    /**
     * The rest, in chain order rather than alphabetically.
     *
     * Chain order because that is where each of them *would* go: the list doubles as a preview
     * of the position an effect will take when it is switched on.
     */
    fun idle(stack: EffectStack): List<EffectId> =
        stack.effectiveOrder().filterNot { stack.enabledOf(it) }

    /**
     * Whether [id] can still be moved earlier or later *within the running chain*.
     *
     * Asked against the running list rather than the whole order, because that is what the
     * arrows appear to do: an effect at the top of what is running cannot go higher, whatever
     * disabled entries sit above it in the stored order.
     */
    fun canMoveUp(stack: EffectStack, id: EffectId): Boolean = running(stack).firstOrNull() != id

    fun canMoveDown(stack: EffectStack, id: EffectId): Boolean = running(stack).lastOrNull() != id

    /**
     * Moving [id] past the next *running* effect rather than by one position in the stored
     * order.
     *
     * The difference shows the moment anything is disabled. In the stored order a disabled
     * effect can sit between two running ones, and stepping by one would then swap a running
     * effect with an invisible one: the arrow is pressed and the chain does not change. Pressed
     * again it finally moves, which reads as a control that misses every other tap.
     */
    fun reorder(stack: EffectStack, id: EffectId, delta: Int): EffectStack {
        val chain = running(stack)
        val from = chain.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in chain.indices) return stack

        val order = stack.effectiveOrder().toMutableList()
        // Swap the two effects' *positions in the stored order*, so whatever disabled entries
        // sit between them stay where they are.
        val a = order.indexOf(id)
        val b = order.indexOf(chain[to])
        order[a] = chain[to]
        order[b] = id
        return stack.copy(order = order)
    }
}
