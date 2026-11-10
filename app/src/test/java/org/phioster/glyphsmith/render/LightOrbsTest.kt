package org.phioster.glyphsmith.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightOrbsTest {

    private val on = LightOrbsParams(enabled = true, count = 10, size = 30, intensity = 80, seed = 3)

    @Test
    fun `switched off, nothing changes`() {
        val luma = FloatArray(40 * 30) { 0.2f }
        LightOrbs.apply(on.copy(enabled = false), 40, 30, luma, null)
        luma.forEach { assertEquals(0.2f, it, 0f) }
    }

    @Test
    fun `orbs add light, never take it away, and never pass white`() {
        val luma = FloatArray(40 * 30) { 0.2f }
        LightOrbs.apply(on, 40, 30, luma, null)
        assertTrue(luma.all { it >= 0.2f && it <= 1f })
        assertTrue("some cells got brighter", luma.count { it > 0.5f } > 5)
    }

    @Test
    fun `the same settings give the same orbs`() {
        assertArrayEquals(LightOrbs.field(on, 40, 30), LightOrbs.field(on, 40, 30), 0f)
    }

    @Test
    fun `drift moves them, and a full drift brings them back`() {
        val still = LightOrbs.field(on, 40, 30)
        val moved = LightOrbs.field(on.copy(drift = 30), 40, 30)
        assertTrue(still.indices.any { still[it] != moved[it] })
        val wrapped = LightOrbs.field(on.copy(drift = 100, direction = 0), 40, 30)
        assertArrayEquals(still, wrapped, 1e-4f)
    }

    @Test
    fun `a full drift sweep closes in a slanted direction too`() {
        val start = LightOrbs.field(on.copy(drift = -100, direction = 250), 40, 30)
        val end = LightOrbs.field(on.copy(drift = 100, direction = 250), 40, 30)
        assertArrayEquals(start, end, 1e-4f)
    }

    @Test
    fun `colour cells brighten with the luminance`() {
        val colors = IntArray(40 * 30) { 0xFF202020.toInt() }
        LightOrbs.apply(on, 40, 30, FloatArray(40 * 30) { 0.1f }, colors)
        assertTrue(colors.any { (it and 0xFF) > 0x80 })
    }
}
