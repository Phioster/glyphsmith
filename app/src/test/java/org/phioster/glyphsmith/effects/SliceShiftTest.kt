package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class SliceShiftTest {

    /** Every pixel distinct, so a displacement is visible and traceable. */
    private fun ramp(width: Int = 32, height: Int = 16) = Pixels(
        IntArray(width * height) { i -> (0xFF shl 24) or (i * 37 and 0xFFFFFF) },
        width,
        height,
    )

    private val on = SliceShiftParams(enabled = true, slices = 8, maxOffset = 20, density = 100)

    @Test
    fun `disabled or zero offset changes nothing`() {
        val source = ramp()
        val before = source.data.toList()
        SliceShift.apply(source, on.copy(enabled = false))
        assertEquals(before, source.data.toList())
        SliceShift.apply(source, on.copy(maxOffset = 0))
        assertEquals(before, source.data.toList())
    }

    @Test
    fun `the same seed gives the same image`() {
        val a = ramp()
        val b = ramp()
        SliceShift.apply(a, on.copy(seed = 7))
        SliceShift.apply(b, on.copy(seed = 7))
        assertEquals(a.data.toList(), b.data.toList())
    }

    @Test
    fun `a different seed gives a different image`() {
        val a = ramp()
        val b = ramp()
        SliceShift.apply(a, on.copy(seed = 7))
        SliceShift.apply(b, on.copy(seed = 8))
        assertTrue("the seed had no effect", a.data.toList() != b.data.toList())
    }

    /**
     * A band is displaced, not rewritten: it wraps, so the same pixels are still there in a
     * different order. If this fails the bands are trailing off the edge and leaving holes.
     */
    @Test
    fun `every row keeps exactly the pixels it started with`() {
        val source = ramp()
        val original = (0 until source.height).map { y ->
            source.data.toList().subList(y * source.width, (y + 1) * source.width).sorted()
        }
        SliceShift.apply(source, on.copy(colorShift = 0))

        for (y in 0 until source.height) {
            val after = source.data.toList().subList(y * source.width, (y + 1) * source.width).sorted()
            assertEquals("row $y lost or gained a pixel", original[y], after)
        }
    }

    @Test
    fun `density zero leaves the image alone`() {
        val source = ramp()
        val before = source.data.toList()
        SliceShift.apply(source, on.copy(density = 0))
        assertEquals(before, source.data.toList())
    }

    @Test
    fun `something actually moves at full density`() {
        val source = ramp()
        val before = source.data.toList()
        SliceShift.apply(source, on)
        assertTrue("nothing was displaced", before != source.data.toList())
    }

    @Test
    fun `vertical bands move columns instead of rows`() {
        val across = ramp()
        val down = ramp()
        SliceShift.apply(across, on.copy(vertical = false))
        SliceShift.apply(down, on.copy(vertical = true))
        assertTrue(across.data.toList() != down.data.toList())
    }

    @Test
    fun `alpha survives the shift`() {
        val source = Pixels(IntArray(32 * 8) { 0x80FF00FF.toInt() }, 32, 8)
        SliceShift.apply(source, on)
        assertTrue(source.data.all { PixelOps.alphaOf(it) == 0x80 })
    }
}
