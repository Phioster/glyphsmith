package org.phioster.glyphsmith.effects

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.NodePipeline
import org.phioster.glyphsmith.core.pipeline.RenderContext

/**
 * What the pass declarations have to be true about.
 *
 * A slot used to be described in two places at once — the code in [EffectNodes], the toggle in
 * [EffectStack] — and the two were kept in step by hand. They are one declaration now, which
 * removes that particular way of being wrong and introduces one of its own: a pass is reached
 * through an `EffectPass<*>`, so the params type that pinned it at the declaration is gone by the
 * time the chain runs it. These are the properties that hold the new wiring to what the old pair
 * of tables promised.
 */
class EffectPassTest {

    private fun ctx(time: Float = 0f) = RenderContext(maxSide = 1024, time = time)

    private fun image(side: Int = 24) = Pixels(
        IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            (0xFF shl 24) or ((x * 255 / side) shl 16) or ((y * 255 / side) shl 8) or 0x60
        },
        side,
        side,
    )

    /**
     * The declarations are shared, never rebuilt.
     *
     * A pass is fetched once per slot per frame. Building one per lookup would put seventeen
     * allocations on every frame of a preview and nothing would look any different — only the
     * frame rate would say so.
     */
    @Test
    fun `a slot's pass is declared once rather than rebuilt per lookup`() {
        EffectId.entries.forEach { id ->
            assertSame(
                "${id.name} builds a new pass on every lookup",
                EffectProviders.of(id).pass,
                EffectProviders.of(id).pass,
            )
        }
    }

    /**
     * Seventeen slots, seventeen params types.
     *
     * This is what makes the declarations self-checking. Inside its own object a pass can only be
     * built around the params type its `apply` takes, and each of those types appears exactly once
     * in [EffectStack] — so pinning the type pins the field. The moment two slots read the same
     * kind of params that stops being true, and one of them will quietly follow the other's
     * toggle.
     */
    @Test
    fun `no two slots read the same controls`() {
        val read = EffectId.entries.associateWith {
            EffectProviders.of(it).pass.paramsIn(EffectStack())::class
        }

        val shared = read.entries.groupBy { it.value }.filterValues { it.size > 1 }
        assertTrue(
            "these slots read the same params: ${shared.values.map { g -> g.map { it.key.name } }}",
            shared.isEmpty(),
        )
        assertEquals(EffectId.entries.size, read.values.toSet().size)
    }

    /** A fresh stack has nothing switched on, and every slot agrees about that. */
    @Test
    fun `a default stack switches nothing on`() {
        val stack = EffectStack()
        EffectId.entries.forEach { id ->
            assertFalse("${id.name} is on in a default stack", EffectProviders.of(id).pass.enabledIn(stack))
            assertFalse("${id.name} disagrees with the stack", stack.enabledOf(id))
        }
    }

    /**
     * The chain is built from the stack's order, and only the switched-on slots run.
     *
     * Both halves used to be read off a hand-kept table; both are the provider's answer now, and
     * this is the assertion that says the answer still reaches the chain.
     */
    @Test
    fun `the nodes are the stored order and only the enabled ones run`() {
        val stack = EffectStack(
            glow = GlowParams(enabled = true),
            blurSharpen = BlurSharpenParams(enabled = true, amount = 40),
            order = listOf(EffectId.GLOW, EffectId.BLUR),
        )
        val nodes = EffectNodes.of(stack)

        assertEquals(stack.effectiveOrder().map { it.name }, nodes.map { it.id })
        assertEquals(listOf(EffectId.GLOW.name, EffectId.BLUR.name), NodePipeline.activeOf(nodes))
    }

    /**
     * A pass reaches its own effect with its own settings.
     *
     * Tint is the spot check because it is unmistakable: it is the only pass that pulls every
     * pixel towards a colour, so running it through the provider and running it directly have to
     * agree pixel for pixel. If the slot were wired to another effect's controls, or to another
     * effect, this is where the two images part company.
     */
    @Test
    fun `a pass runs its own effect with the stack's settings`() {
        val params = TintParams(enabled = true, color = 0xFFFF0000.toInt(), amount = 80)
        val stack = EffectStack(tint = params)

        val throughProvider = EffectProviders.of(EffectId.TINT).pass.apply(image(), stack, ctx())
        val directly = Tint.apply(image(), params)

        assertArrayEquals("the tint slot did not run the tint", directly.data, throughProvider.data)
    }

    /**
     * The clock survives the trip through the provider.
     *
     * Five passes take the [RenderContext] and one of them moves with it. A pass declaration that
     * dropped the context — `_` where `ctx` belongs — still compiles and still draws a perfectly
     * good picture; the only symptom is an animation that has stopped moving, in an export nobody
     * watches until it is finished.
     */
    @Test
    fun `a pass that reads the clock still gets it`() {
        val stack = EffectStack(
            modulationLines = ModulationLinesParams(enabled = true, waveSpeed = 80, waveMix = 90),
        )
        val pass = EffectProviders.of(EffectId.MODULATION).pass

        val atStart = pass.apply(image(300), stack, ctx(time = 0f)).data
        val laterOn = pass.apply(image(300), stack, ctx(time = 0.3f)).data

        assertFalse("the clock did not reach the pass", atStart.contentEquals(laterOn))
    }
}
