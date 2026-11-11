package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class StressReliefTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** Only the wear: no grain and no flecks. */
    private val wearOnly = StressReliefParams(enabled = true, edgeWear = 8, grain = 0, speckles = 0)

    /** Left half black, right half white. */
    private fun halves(size: Int = 128) = Pixels(IntArray(size * size) { if (it % size < size / 2) black else white }, size, size)

    @Test
    fun `switched off it returns the picture untouched`() {
        val source = halves()
        StressRelief.apply(source, StressReliefParams())
        assertTrue(halves().data.contentEquals(source.data))
    }

    @Test
    fun `wear frays the border and leaves the areas alone`() {
        val out = StressRelief.apply(halves(), wearOnly)
        val size = out.width
        val border = (0 until size).map { y -> (0 until size).first { x -> out.data[y * size + x] == white } }
        assertTrue("the border is still straight", border.toSet().size > 3)
        assertTrue((0 until size).all { y -> out.data[y * size + 20] == black && out.data[y * size + size - 20] == white })
    }

    @Test
    fun `wear adds no colour of its own`() {
        val out = StressRelief.apply(halves(), wearOnly)
        assertTrue(out.data.all { it == black || it == white })
    }

    @Test
    fun `flecks carry the colour next door across the border`() {
        val out = StressRelief.apply(halves(), StressReliefParams(enabled = true, edgeWear = 0, grain = 0, speckles = 100, speckleSize = 4))
        val size = out.width
        val strayed = out.data.indices.count { i -> (i % size < size / 2) != (out.data[i] == black) }
        assertTrue("$strayed pixels crossed", strayed > 20)
        assertTrue(out.data.all { it == black || it == white })
    }

    @Test
    fun `grain moves brightness and nothing else`() {
        val grey = Pixels(IntArray(64 * 64) { 0x40808080 }, 64, 64)
        val out = StressRelief.apply(grey, StressReliefParams(enabled = true, edgeWear = 0, grain = 20, speckles = 0))
        assertTrue(out.data.all { it ushr 24 == 0x40 })
        assertTrue(out.data.all { PixelOps.redOf(it) == PixelOps.greenOf(it) && PixelOps.redOf(it) in 108..148 })
        assertTrue(out.data.map { PixelOps.redOf(it) }.toSet().size > 10)
    }

    @Test
    fun `mix at zero keeps the picture`() {
        val source = halves()
        StressRelief.apply(source, StressReliefParams(enabled = true, edgeWear = 12, grain = 40, speckles = 100, mix = 0))
        assertTrue(halves().data.contentEquals(source.data))
    }

    @Test
    fun `the same seed wears the same way and another seed does not`() {
        val a = StressRelief.apply(halves(), wearOnly).data
        assertTrue(a.contentEquals(StressRelief.apply(halves(), wearOnly).data))
        assertEquals(false, a.contentEquals(StressRelief.apply(halves(), wearOnly.copy(seed = 2)).data))
    }
}
