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
    fun `growth and flatten-by are off by default and change the painting when set`() {
        val plain = PaintStrokes.apply(noise(), on).data
        assertEquals("growth", false, plain.contentEquals(PaintStrokes.apply(noise(), on.copy(brushGrowth = 60)).data))
        // Red beside green at one brightness: colour keeps them apart, luminance lets strokes cross.
        fun twoHues() = Pixels(IntArray(96 * 96) { if (it % 96 < 48) 0xFFB40000.toInt() else 0xFF003500.toInt() }, 96, 96)
        fun crossed(by: PaintFlatten): Int {
            val out = PaintStrokes.apply(twoHues(), on.copy(edgeSensitivity = 100, flattenBy = by, brushSize = 30))
            return out.data.indices.count { i -> i % 96 < 48 && PixelOps.greenOf(out.data[i]) > PixelOps.redOf(out.data[i]) }
        }
        val byColour = crossed(PaintFlatten.COLOUR)
        val byLuma = crossed(PaintFlatten.LUMINANCE)
        assertTrue("$byLuma crossed by luminance, $byColour by colour", byLuma > byColour + 20)
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
