package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class PixelSortTest {

    private fun grey(value: Int) = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    /** One row, given as grey levels. */
    private fun row(vararg values: Int) = Pixels(IntArray(values.size) { grey(values[it]) }, values.size, 1)

    private fun levels(p: Pixels) = p.data.map { PixelOps.redOf(it) }

    private val on = PixelSortParams(enabled = true, thresholdLow = 20, thresholdHigh = 80)

    @Test
    fun `disabled changes nothing`() {
        val source = row(200, 10, 120, 60)
        val before = levels(source)
        PixelSort.apply(source, on.copy(enabled = false))
        assertEquals(before, levels(source))
    }

    /**
     * The band is the effect. Pixels outside it may not move at all — that is what keeps the
     * darks and lights of the picture readable while the mid-tones smear.
     */
    @Test
    fun `pixels outside the band keep their place`() {
        // 0 and 255 sit outside a 20..80 band; the three in between are inside it.
        val source = row(0, 180, 100, 140, 255)
        PixelSort.apply(source, on)
        val out = levels(source)

        assertEquals("dark anchor moved", 0, out[0])
        assertEquals("light anchor moved", 255, out[4])
        assertEquals(listOf(100, 140, 180), out.subList(1, 4))
    }

    @Test
    fun `sorting neither invents nor loses a pixel`() {
        val values = intArrayOf(90, 30, 200, 55, 120, 5, 70, 160)
        val source = Pixels(IntArray(values.size) { grey(values[it]) }, values.size, 1)
        PixelSort.apply(source, PixelSortParams(enabled = true, thresholdLow = 0, thresholdHigh = 100))
        assertEquals(values.sorted(), levels(source).sorted())
    }

    @Test
    fun `reverse turns the run around`() {
        val forward = row(60, 140, 100)
        val backward = row(60, 140, 100)
        PixelSort.apply(forward, on)
        PixelSort.apply(backward, on.copy(reverse = true))
        assertEquals(levels(forward).reversed(), levels(backward))
    }

    @Test
    fun `max run cuts a long stretch into pieces`() {
        val values = intArrayOf(140, 120, 100, 80, 60, 40)
        fun fresh() = Pixels(IntArray(values.size) { grey(values[it]) }, values.size, 1)

        val whole = fresh()
        PixelSort.apply(whole, on.copy(thresholdLow = 0, thresholdHigh = 100))
        assertEquals(values.sorted(), levels(whole))

        val chopped = fresh()
        PixelSort.apply(chopped, on.copy(thresholdLow = 0, thresholdHigh = 100, maxRun = 2))
        // Sorted in pairs, so the sequence is not globally ascending any more.
        assertEquals(listOf(120, 140, 80, 100, 40, 60), levels(chopped))
    }

    /** A band handed over backwards is a slip, not an instruction to do nothing. */
    @Test
    fun `an inverted band is read the way it was meant`() {
        val normal = row(60, 140, 100)
        val swapped = row(60, 140, 100)
        PixelSort.apply(normal, on)
        PixelSort.apply(swapped, on.copy(thresholdLow = 80, thresholdHigh = 20))
        assertEquals(levels(normal), levels(swapped))
    }

    @Test
    fun `the vertical axis sorts down the image instead of across`() {
        // A single column: horizontal sorting can do nothing here, vertical must reorder it.
        val values = intArrayOf(140, 60, 100)
        fun column() = Pixels(IntArray(3) { grey(values[it]) }, 1, 3)

        val across = column()
        PixelSort.apply(across, on.copy(axis = SortAxis.HORIZONTAL))
        assertEquals(values.toList(), levels(across))

        val down = column()
        PixelSort.apply(down, on.copy(axis = SortAxis.VERTICAL))
        assertEquals(values.sorted(), levels(down))
    }

    @Test
    fun `sorting by saturation leaves a grey run alone`() {
        // Every pixel is grey, so every key is identical and a stable sort cannot move them.
        val source = row(60, 140, 100)
        PixelSort.apply(source, on.copy(key = SortKey.SATURATION))
        assertEquals(listOf(60, 140, 100), levels(source))
    }

    @Test
    fun `a run of one is left alone`() {
        val source = row(0, 100, 0)
        PixelSort.apply(source, on)
        assertEquals(listOf(0, 100, 0), levels(source))
    }

    @Test
    fun `each row is sorted on its own`() {
        val data = intArrayOf(140, 60, 40, 120)
        val source = Pixels(IntArray(4) { grey(data[it]) }, 2, 2)
        PixelSort.apply(source, on.copy(thresholdLow = 0, thresholdHigh = 100))
        // Row 0 is (140,60) and row 1 is (40,120); neither may borrow from the other.
        assertTrue(levels(source) == listOf(60, 140, 40, 120))
    }
}
