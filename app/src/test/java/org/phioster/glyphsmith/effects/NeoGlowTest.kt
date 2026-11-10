package org.phioster.glyphsmith.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels

class NeoGlowTest {

    private fun grey(level: Int) = Pixels(IntArray(32 * 32) { PixelOps.argb(255, level, level, level) }, 32, 32)

    @Test
    fun `a run is brightest at its head and fades along its tail`() {
        val p = NeoGlowParams(enabled = true, stagger = 0)
        val head = NeoGlow.runAt(t = 50f, lane = 0, along = 100f, tail = 30f, phase = 0.5f / 1.3f, params = p)
        val tail = NeoGlow.runAt(t = 30f, lane = 0, along = 100f, tail = 30f, phase = 0.5f / 1.3f, params = p)
        val ahead = NeoGlow.runAt(t = 60f, lane = 0, along = 100f, tail = 30f, phase = 0.5f / 1.3f, params = p)
        assertTrue(head > 0.95f)
        assertTrue(tail in 0.2f..0.5f)
        assertEquals(0f, ahead, 0f)
    }

    @Test
    fun `nothing is painted onto black`() {
        val out = NeoGlow.apply(grey(0), NeoGlowParams(enabled = true, rest = 100, intensity = 400))
        assertTrue(out.data.all { PixelOps.redOf(it) == 0 })
    }

    @Test
    fun `lit pixels flare inside a run and dim outside it`() {
        val out = NeoGlow.apply(grey(100), NeoGlowParams(enabled = true, rest = 30, intensity = 300, offset = 40))
        val reds = out.data.map { PixelOps.redOf(it) }
        assertTrue("some flare", reds.any { it > 140 })
        assertTrue("the rest dims", reds.any { it < 60 })
    }

    @Test
    fun `a full sweep of the offset comes back to the start`() {
        val a = NeoGlow.apply(grey(120), NeoGlowParams(enabled = true, offset = 0)).data
        val b = NeoGlow.apply(grey(120), NeoGlowParams(enabled = true, offset = 100)).data
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `more runs light more of a lane at once`() {
        fun litCount(runs: Int) = NeoGlow.apply(grey(120), NeoGlowParams(enabled = true, rest = 0, length = 15, runs = runs))
            .data.count { PixelOps.redOf(it) > 0 }
        assertTrue(litCount(4) > litCount(1))
    }

    @Test
    fun `a white head turns a coloured light pale at the tip`() {
        val green = 0xFF00FF00.toInt()
        val plain = NeoGlow.apply(grey(200), NeoGlowParams(enabled = true, color = green, stagger = 0, offset = 50))
        val headed = NeoGlow.apply(grey(200), NeoGlowParams(enabled = true, color = green, stagger = 0, offset = 50, head = 100))
        assertTrue(headed.data.max { PixelOps.blueOf(it) } > plain.data.max { PixelOps.blueOf(it) })
    }

    @Test
    fun `a low gamma lights faint drawing but leaves black black`() {
        val faint = NeoGlow.apply(grey(40), NeoGlowParams(enabled = true, gamma = 0, rest = 100, intensity = 400))
        val linear = NeoGlow.apply(grey(40), NeoGlowParams(enabled = true, gamma = 50, rest = 100, intensity = 400))
        assertTrue(faint.data.max { PixelOps.redOf(it) } > linear.data.max { PixelOps.redOf(it) })
        val black = NeoGlow.apply(grey(5), NeoGlowParams(enabled = true, gamma = 0, rest = 100, intensity = 400))
        assertTrue(black.data.all { PixelOps.redOf(it) == 5 })
    }
}
