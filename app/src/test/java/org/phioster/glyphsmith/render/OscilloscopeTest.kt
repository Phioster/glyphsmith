package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OscilloscopeTest {

    private fun grid(cols: Int, rows: Int, tone: (Int, Int) -> Float) =
        CellGrid(cols, rows, FloatArray(cols * rows) { tone(it % cols, it / cols) }, null)

    private fun settings(scope: OscilloscopeParams = OscilloscopeParams(graticule = false)) =
        RenderSettings(renderMode = RenderMode.Oscilloscope, oscilloscope = scope)

    private fun green(pixel: Int) = pixel shr 8 and 0xFF

    /**
     * The rows of [picture] where a trace begins, read down one column. Measured against the
     * brightest pixel of that column rather than a fixed level, because a trace over a dark part
     * of the picture is a dim one.
     */
    private fun tracesIn(picture: ScopePicture, x: Int): List<Int> {
        val light = IntArray(picture.height) { green(picture.pixels[it * picture.width + x]) }
        val level = light.max() * 0.6f
        return (1 until picture.height).filter { light[it] > level && light[it - 1] <= level }
    }

    @Test
    fun `a trace rises where the picture is bright`() {
        // Dark on the left, white on the right, with room for the smoothing in between.
        val picture = Oscilloscope.draw(grid(768, 200) { x, _ -> if (x < 384) 0f else 1f }, settings(), 1f)

        val dark = tracesIn(picture, 100)
        val bright = tracesIn(picture, 668)
        assertTrue("${bright.size} traces over white", bright.size > 10)
        // The default height is 34: white lifts every trace by that much. Matched from the
        // bottom, because the topmost traces over white rise out of the picture.
        dark.reversed().zip(bright.reversed()).forEach { (low, high) -> assertEquals(34f, (low - high).toFloat(), 1.5f) }
    }

    @Test
    fun `a picture half the size gets the same traces`() {
        val large = Oscilloscope.draw(grid(768, 400) { _, _ -> 0.5f }, settings(), 1f)
        val small = Oscilloscope.draw(grid(384, 200) { _, _ -> 0.5f }, settings(), 2f)

        assertEquals(large.width, small.width)
        assertEquals(tracesIn(large, 300).size, tracesIn(small, 300).size)
    }

    @Test
    fun `a near trace hides the far ones behind it, and only when asked to`() {
        // One white band across the lower part: its traces rise over the dark ones above it.
        val band = grid(768, 300) { _, y -> if (y in 180..240) 1f else 0f }
        val tall = OscilloscopeParams(graticule = false, height = 70)

        // Lit rows in one column, whatever their brightness: the dim traces over the dark part
        // are the ones that get hidden.
        fun lit(picture: ScopePicture) = (0 until picture.height).count { green(picture.pixels[it * picture.width + 400]) > 25 }
        val hidden = lit(Oscilloscope.draw(band, settings(tall.copy(glow = 0)), 1f))
        val all = lit(Oscilloscope.draw(band, settings(tall.copy(glow = 0, hidden = false)), 1f))
        assertTrue("$hidden lit rows with hiding, $all without", hidden < all)
    }

    @Test
    fun `the tube is dark between the traces and takes its phosphor's colour`() {
        val flat = grid(768, 200) { _, _ -> 0.5f }
        val picture = Oscilloscope.draw(flat, settings(OscilloscopeParams(graticule = false, glow = 0, phosphor = Phosphor.AMBER)), 1f)

        val column = (0 until picture.height).map { picture.pixels[it * picture.width + 300] }
        val brightest = column.maxByOrNull { (it shr 16 and 0xFF) }!!
        assertTrue("amber is more red than blue", (brightest shr 16 and 0xFF) > (brightest and 0xFF) + 60)
        assertTrue("most of the tube is dark", column.count { (it shr 16 and 0xFF) < 30 } > column.size / 2)
        assertTrue("every pixel is opaque", picture.pixels.all { it ushr 24 == 0xFF })
    }

    private fun moving(motion: ScopeMotion, time: Float): ScopePicture {
        val picture = grid(384, 200) { x, y -> ((x * 7 + y * 13) % 97) / 97f }
        val params = settings(OscilloscopeParams(graticule = false, motion = motion))
        return Oscilloscope.draw(picture, params.copy(temporal = params.temporal.copy(time = time)), 1f)
    }

    @Test
    fun `every motion moves, and comes back to where it started`() {
        for (motion in ScopeMotion.entries - ScopeMotion.STILL) {
            val start = moving(motion, 0f)
            assertTrue("$motion does not move", !start.pixels.contentEquals(moving(motion, 0.4f).pixels))
            assertTrue("$motion does not close its loop", start.pixels.contentEquals(moving(motion, 1f).pixels))
        }
    }

    @Test
    fun `a still tube ignores the clock`() {
        assertTrue(moving(ScopeMotion.STILL, 0f).pixels.contentEquals(moving(ScopeMotion.STILL, 0.4f).pixels))
    }
}
