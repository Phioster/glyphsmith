package org.phioster.glyphsmith.effects

import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class AnalogErrorTest {

    private val on = AnalogErrorParams(enabled = true)

    /** A grey ramp across, with a bright block in the middle: something for the fault to read. */
    private fun picture(w: Int = 480, h: Int = 300) = Pixels(
        IntArray(w * h) {
            val x = it % w
            val y = it / w
            val v = if (x in w / 3..w / 2 && y in h / 4..h / 2) 240 else 40 + x * 120 / w
            PixelOps.argb(0x80, v, v, v)
        },
        w, h,
    )

    private fun colour(pixel: Int) =
        abs(PixelOps.redOf(pixel) - PixelOps.greenOf(pixel)) + abs(PixelOps.greenOf(pixel) - PixelOps.blueOf(pixel))

    @Test
    fun `switched off or turned to nothing it returns the picture untouched`() {
        val off = picture()
        AnalogError.apply(off, AnalogErrorParams())
        assertTrue(picture().data.contentEquals(off.data))
        val none = picture()
        AnalogError.apply(none, on.copy(amount = 0))
        assertTrue(picture().data.contentEquals(none.data))
    }

    @Test
    fun `a grey picture comes back with colour of its own`() {
        val out = AnalogError.apply(picture(), on)
        assertTrue(out.data.count { colour(it) > 40 } > out.data.size / 50)
    }

    @Test
    fun `with nothing leaking and the receiver in tune grey stays grey`() {
        val out = AnalogError.apply(picture(), on.copy(rainbow = 0, detune = 0, bend = 0, tear = 0, amount = 1))
        // Left of the block: a brightness step does read as colour, even with everything in tune.
        val calm = out.data.filterIndexed { i, _ -> i % out.width < out.width / 4 }
        assertTrue("worst ${calm.maxOf(::colour)}", calm.all { colour(it) < 12 })
    }

    @Test
    fun `it keeps the alpha it was given`() {
        assertTrue(AnalogError.apply(picture(), on).data.all { it ushr 24 == 0x80 })
    }

    @Test
    fun `the same frame is the same picture, and another moment another`() {
        val a = AnalogError.apply(picture(), on, 0.3f)
        val b = AnalogError.apply(picture(), on, 0.3f)
        val c = AnalogError.apply(picture(), on, 0.6f)
        assertTrue(a.data.contentEquals(b.data))
        assertFalse(a.data.contentEquals(c.data))
    }

    @Test
    fun `the loop closes`() {
        val start = AnalogError.apply(picture(), on.copy(swell = 80), 0f)
        val end = AnalogError.apply(picture(), on.copy(swell = 80), 1f)
        assertTrue(start.data.contentEquals(end.data))
    }

    @Test
    fun `swell lets the fault ebb in the middle of the loop`() {
        val swelling = on.copy(swell = 100)
        val loud = AnalogError.apply(picture(), swelling, 0f).data.sumOf { colour(it) }
        val calm = AnalogError.apply(picture(), swelling, 0.5f).data.sumOf { colour(it) }
        assertTrue("$calm against $loud", calm * 4 < loud)
    }

    @Test
    fun `a picture twice as large shows the same bands`() {
        val small = AnalogError.apply(picture(480, 300), on.copy(tear = 0))
        val large = AnalogError.apply(picture(960, 600), on.copy(tear = 0))
        val mean = { p: Pixels -> p.data.sumOf { colour(it) }.toFloat() / p.data.size }
        assertTrue("${mean(small)} against ${mean(large)}", abs(mean(small) - mean(large)) < mean(small) * 0.15f)
    }
}
