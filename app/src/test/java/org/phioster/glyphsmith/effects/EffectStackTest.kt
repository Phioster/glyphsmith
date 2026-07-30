package org.phioster.glyphsmith.effects

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class EffectStackTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `default order is the enum order`() {
        assertEquals(EffectId.entries.toList(), EffectStack().effectiveOrder())
    }

    /**
     * A preset written before the chain became reorderable has no `order` field at all, and
     * one written before an effect existed has an order that predates it. Neither may drop
     * an effect out of the chain — the missing ones have to be appended, not lost.
     */
    @Test
    fun `a preset from before an effect existed still runs every effect`() {
        val old = EffectStack(order = listOf(EffectId.POST, EffectId.GLOW))
        val order = old.effectiveOrder()

        assertEquals(EffectId.entries.size, order.size)
        assertEquals(EffectId.entries.toSet(), order.toSet())
        assertEquals("the stored prefix must be respected", EffectId.POST, order[0])
        assertEquals(EffectId.GLOW, order[1])
    }

    @Test
    fun `a stack serialised without an order decodes to the default order`() {
        val decoded = json.decodeFromString<EffectStack>("""{"glow":{"enabled":true}}""")
        assertEquals(EffectId.entries.toList(), decoded.effectiveOrder())
        assertTrue(decoded.glow.enabled)
    }

    @Test
    fun `a duplicated entry is collapsed rather than run twice`() {
        val stack = EffectStack(order = listOf(EffectId.GLOW, EffectId.GLOW, EffectId.POST))
        val order = stack.effectiveOrder()
        assertEquals(EffectId.entries.size, order.size)
        assertEquals(1, order.count { it == EffectId.GLOW })
    }

    @Test
    fun `reorder moves one slot and stops at the ends`() {
        val stack = EffectStack()
        val first = EffectId.entries.first()
        val second = EffectId.entries[1]

        val moved = stack.reorder(first, 1)
        assertEquals(second, moved.effectiveOrder()[0])
        assertEquals(first, moved.effectiveOrder()[1])

        // Nothing above the first slot, nothing below the last — both must be no-ops.
        assertEquals(stack.effectiveOrder(), stack.reorder(first, -1).effectiveOrder())
        assertEquals(
            stack.effectiveOrder(),
            stack.reorder(EffectId.entries.last(), 1).effectiveOrder(),
        )
    }

    @Test
    fun `active count follows the enabled flags`() {
        assertEquals(0, EffectStack().activeCount)
        val two = EffectStack(
            glow = GlowParams(enabled = true),
            blurSharpen = BlurSharpenParams(enabled = true, amount = 40),
        )
        assertEquals(2, two.activeCount)
    }

    private fun ramp(width: Int, height: Int) = Pixels(
        IntArray(width * height) { i ->
            val v = (i % width) * 255 / (width - 1)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        },
        width,
        height,
    )

    /**
     * The whole point of a movable chain: running two passes the other way round has to
     * produce a different image. Tint clamps towards a colour and blur averages neighbours,
     * so tinting a blur is not the same as blurring a tint — if this ever passes trivially,
     * the reordering has stopped reaching the pipeline.
     */
    @Test
    fun `swapping two passes changes the result`() {
        val tint = TintParams(enabled = true, color = 0xFFFF0000.toInt(), amount = 80)
        val blur = BlurSharpenParams(enabled = true, amount = 100, radius = 4)

        val tintFirst = BlurSharpen.apply(Tint.apply(ramp(32, 8), tint), blur)
        val blurFirst = Tint.apply(BlurSharpen.apply(ramp(32, 8), blur), tint)

        assertNotEquals(
            "order made no difference — the chain is not actually ordered",
            tintFirst.data.toList(),
            blurFirst.data.toList(),
        )
    }

    @Test
    fun `blur and sharpen pull a gradient in opposite directions`() {
        // A hard step has a big jump at the seam: blurring must soften it, sharpening must
        // exaggerate it. Measuring the seam is the only direction-independent check.
        val step = Pixels(
            IntArray(32 * 4) { i -> if (i % 32 < 16) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() },
            32,
            4,
        )
        fun seam(p: Pixels): Int {
            val row = 2 * 32
            return PixelOps.redOf(p.data[row + 16]) - PixelOps.redOf(p.data[row + 15])
        }

        val original = seam(step)
        val blurred = seam(BlurSharpen.apply(step.copy(), BlurSharpenParams(true, 100, 4)))
        val sharpened = seam(BlurSharpen.apply(step.copy(), BlurSharpenParams(true, -100, 4)))

        assertTrue("blur did not soften the step ($blurred vs $original)", blurred < original)
        assertTrue("sharpen did not harden the step", sharpened >= original)
    }

    @Test
    fun `a disabled or zero blur leaves the pixels untouched`() {
        val source = ramp(16, 4)
        val before = source.data.toList()
        BlurSharpen.apply(source, BlurSharpenParams(enabled = false, amount = 100))
        assertEquals(before, source.data.toList())
        BlurSharpen.apply(source, BlurSharpenParams(enabled = true, amount = 0))
        assertEquals(before, source.data.toList())
    }
}
