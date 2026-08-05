package org.phioster.glyphsmith.ui.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectProviders
import org.phioster.glyphsmith.effects.EffectRoll
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.core.color.Palettes
import kotlin.random.Random

/**
 * Splitting the effect list into what is running and what could run.
 *
 * The property worth testing is not the split — that is one `filter` each way. It is that the
 * arrows now step past the next *running* effect rather than by one position in the stored
 * order, because the two are the same thing only while nothing is disabled, and that is
 * precisely the case a hand test is done in.
 */
class EffectChainTest {

    private val roll = EffectRoll(Random(1), Palettes.all.first())

    /** A stack with exactly [ids] switched on, each through its own declaration. */
    private fun stackWith(vararg ids: EffectId): EffectStack =
        ids.fold(EffectStack()) { stack, id ->
            EffectProviders.of(id).pass.rolled(stack, roll) ?: stack
        }

    @Test
    fun `the two halves are the whole list, once each`() {
        val stack = stackWith(EffectId.TINT, EffectId.GLOW)

        assertEquals(
            EffectId.entries.size,
            EffectChain.running(stack).size + EffectChain.idle(stack).size,
        )
        assertEquals(
            stack.effectiveOrder().toSet(),
            (EffectChain.running(stack) + EffectChain.idle(stack)).toSet(),
        )
    }

    @Test
    fun `nothing is running in a default stack, and everything could be`() {
        val stack = EffectStack()

        assertTrue(EffectChain.running(stack).isEmpty())
        assertEquals(EffectId.entries.size, EffectChain.idle(stack).size)
    }

    /** Both halves keep chain order: the idle list doubles as a preview of where each would go. */
    @Test
    fun `both halves read in chain order`() {
        val stack = stackWith(EffectId.WARP, EffectId.POST, EffectId.SORT)
        val order = stack.effectiveOrder()

        listOf(EffectChain.running(stack), EffectChain.idle(stack)).forEach { half ->
            assertEquals(half.sortedBy { order.indexOf(it) }, half)
        }
    }

    /** The ends of the *running* chain, not of the stored order. */
    @Test
    fun `the arrows stop at the ends of what is running`() {
        val stack = stackWith(EffectId.TINT, EffectId.SORT, EffectId.WARP)

        assertTrue(!EffectChain.canMoveUp(stack, EffectId.TINT))
        assertTrue(EffectChain.canMoveDown(stack, EffectId.TINT))
        assertTrue(EffectChain.canMoveUp(stack, EffectId.WARP))
        assertTrue(!EffectChain.canMoveDown(stack, EffectId.WARP))
        assertTrue(EffectChain.canMoveUp(stack, EffectId.SORT))
        assertTrue(EffectChain.canMoveDown(stack, EffectId.SORT))
    }

    // --- the reason this file exists ------------------------------------------------------

    /**
     * One press, one visible move — with fourteen disabled effects in between.
     *
     * `POST` and `WARP` are the first and last slots of the default order, so everything else
     * sits between them. Stepping by one position in the stored order would swap `POST` with
     * a disabled neighbour: the arrow is pressed and the chain does not change. Pressed enough
     * times it finally moves, which reads as a control that misses every other tap.
     */
    @Test
    fun `one press moves an effect past the next running one, not into a gap`() {
        val stack = stackWith(EffectId.POST, EffectId.WARP)
        assertEquals(listOf(EffectId.POST, EffectId.WARP), EffectChain.running(stack))

        val moved = EffectChain.reorder(stack, EffectId.POST, 1)

        assertEquals(listOf(EffectId.WARP, EffectId.POST), EffectChain.running(moved))
    }

    @Test
    fun `moving is reversible`() {
        val stack = stackWith(EffectId.POST, EffectId.SORT, EffectId.WARP)
        val there = EffectChain.reorder(stack, EffectId.SORT, 1)
        val back = EffectChain.reorder(there, EffectId.SORT, -1)

        assertEquals(EffectChain.running(stack), EffectChain.running(back))
    }

    /** A move must not disturb where the disabled effects sit, or enabling one later surprises. */
    @Test
    fun `moving two running effects leaves the idle ones where they were`() {
        val stack = stackWith(EffectId.POST, EffectId.WARP)
        val before = EffectChain.idle(stack)

        val moved = EffectChain.reorder(stack, EffectId.POST, 1)

        assertEquals(before, EffectChain.idle(moved))
    }

    @Test
    fun `a move off either end changes nothing`() {
        val stack = stackWith(EffectId.TINT, EffectId.GLOW)

        assertEquals(stack, EffectChain.reorder(stack, EffectId.TINT, -1))
        assertEquals(stack, EffectChain.reorder(stack, EffectId.GLOW, 1))
    }

    /** Moving something that is not running is not a thing the panel can ask for, and is a no-op. */
    @Test
    fun `moving an effect that is not running does nothing`() {
        val stack = stackWith(EffectId.TINT)

        assertEquals(stack, EffectChain.reorder(stack, EffectId.WARP, -1))
    }

    /** The order stays a permutation: no effect lost, none duplicated. */
    @Test
    fun `a move keeps every effect exactly once`() {
        var stack = stackWith(EffectId.POST, EffectId.SORT, EffectId.WARP, EffectId.TINT)

        repeat(12) { step ->
            val chain = EffectChain.running(stack)
            stack = EffectChain.reorder(stack, chain[step % chain.size], if (step % 2 == 0) 1 else -1)

            assertEquals(EffectId.entries.size, stack.effectiveOrder().size)
            assertEquals(EffectId.entries.toSet(), stack.effectiveOrder().toSet())
        }
    }
}
