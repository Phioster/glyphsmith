package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class PaintStrokesTest {

    private val on = PaintStrokesParams(enabled = true)
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** Left half black, right half white. */
    private fun halves(size: Int = 96) = Pixels(IntArray(size * size) { if (it % size < size / 2) black else white }, size, size)

    private fun noise(size: Int = 96) = Pixels(
        IntArray(size * size) { i ->
            var x = i * 374761393
            x = (x xor (x ushr 13)) * 1274126177
            if ((x ushr 16) and 1 == 0) black else white
        },
        size, size,
    )

    @Test
    fun `switched off it returns the picture untouched`() {
        val source = noise()
        val before = source.data.copyOf()
        PaintStrokes.apply(source, PaintStrokesParams())
        assertTrue(before.contentEquals(source.data))
    }

    @Test
    fun `dabs gather a dither's dots into patches`() {
        fun changes(data: IntArray) = (1 until data.size).sumOf { kotlin.math.abs(PixelOps.redOf(data[it]) - PixelOps.redOf(data[it - 1])) }
        val source = noise(240)
        val before = changes(source.data)
        val after = changes(PaintStrokes.apply(source, on).data)
        assertTrue("$after of change after, $before before", after < before / 3)
    }

    @Test
    fun `neighbouring dabs differ over a dither`() {
        val out = PaintStrokes.apply(noise(240), on)
        val reds = out.data.map { PixelOps.redOf(it) }
        assertTrue(reds.count { it < 110 } > out.data.size / 10)
        assertTrue(reds.count { it > 145 } > out.data.size / 10)
    }

    @Test
    fun `strokes do not carry paint across a hard edge`() {
        val out = PaintStrokes.apply(halves(), on.copy(edgeSensitivity = 100))
        val size = out.width
        assertTrue((0 until size).all { y -> PixelOps.redOf(out.data[y * size + 10]) < 40 })
        assertTrue((0 until size).all { y -> PixelOps.redOf(out.data[y * size + size - 10]) > 215 })
    }

    @Test
    fun `mix at zero keeps the picture and alpha is never touched`() {
        val source = noise()
        val before = source.data.copyOf()
        PaintStrokes.apply(source, on.copy(mix = 0))
        assertTrue(before.contentEquals(source.data))
        val clear = Pixels(IntArray(64 * 64) { 0x40808080 }, 64, 64)
        assertTrue(PaintStrokes.apply(clear, on).data.all { it ushr 24 == 0x40 })
    }

    @Test
    fun `the same seed paints the same picture and another seed does not`() {
        val a = PaintStrokes.apply(noise(), on).data
        assertTrue(a.contentEquals(PaintStrokes.apply(noise(), on).data))
        assertEquals(false, a.contentEquals(PaintStrokes.apply(noise(), on.copy(seed = 2)).data))
    }
}
