package org.phioster.glyphsmith.effects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class ContourLinesTest {

    /** Only the lines: the picture underneath is left as it is. */
    private val on = ContourLinesParams(enabled = true, neon = 0, wave = 0)

    /** A white disc on black. */
    private fun disc(size: Int = 320, alpha: Int = 0xFF) = Pixels(
        IntArray(size * size) {
            val dx = it % size - size / 2
            val dy = it / size - size / 2
            val v = if (dx * dx + dy * dy < size * size / 36) 255 else 0
            PixelOps.argb(alpha, v, v, v)
        },
        size, size,
    )

    private fun green(pixel: Int) = PixelOps.greenOf(pixel) > 120 && PixelOps.redOf(pixel) < 100

    @Test
    fun `switched off it returns the picture untouched`() {
        val source = disc()
        ContourLines.apply(source, ContourLinesParams())
        assertTrue(disc().data.contentEquals(source.data))
    }

    @Test
    fun `lines lie around the subject and not on it or far from it`() {
        val out = ContourLines.apply(disc(), on)
        val size = out.width
        val row = size / 2
        val ring = (size / 2 + size / 6 + 4 until size * 9 / 10).count { green(out.data[row * size + it]) }
        assertTrue("$ring line pixels beside the disc", ring > 4)
        assertTrue((size / 2 - 20..size / 2 + 20).none { green(out.data[row * size + it]) })
        assertFalse(green(out.data[2 * size + 2]))
    }

    @Test
    fun `more levels draw more lines`() {
        val count = { lines: Int -> ContourLines.apply(disc(), on.copy(lines = lines)).data.count(::green) }
        assertTrue(count(40) > count(10) * 2)
    }

    @Test
    fun `neon tones turn white into yellow and leave far black alone`() {
        val out = ContourLines.apply(disc(), on.copy(neon = 100))
        val size = out.width
        val centre = out.data[size / 2 * size + size / 2]
        assertTrue(PixelOps.redOf(centre) > 240 && PixelOps.greenOf(centre) > 240 && PixelOps.blueOf(centre) < 80)
        assertTrue(out.data[2 * size + 2] and 0xFFFFFF == 0)
    }

    @Test
    fun `it keeps the alpha it was given`() {
        assertTrue(ContourLines.apply(disc(alpha = 0x80), on).data.all { it ushr 24 == 0x80 })
    }

    @Test
    fun `the lines travel and the loop closes`() {
        val start = ContourLines.apply(disc(), on, 0f)
        val middle = ContourLines.apply(disc(), on, 0.5f)
        val end = ContourLines.apply(disc(), on, 1f)
        assertFalse(start.data.contentEquals(middle.data))
        assertTrue(start.data.contentEquals(end.data))
        val still = on.copy(flow = 0)
        assertTrue(ContourLines.apply(disc(), still, 0f).data.contentEquals(ContourLines.apply(disc(), still, 0.5f).data))
    }
}
