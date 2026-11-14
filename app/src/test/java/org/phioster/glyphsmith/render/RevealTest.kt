package org.phioster.glyphsmith.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealTest {

    private val w = 200
    private val h = 160
    private val look = 0xFF102030.toInt()
    private val photo = 0xFFC08040.toInt()

    private fun at(amount: Int, seed: Int = 1): IntArray =
        Reveal.apply(IntArray(w * h) { look }, w, h, IntArray(50 * 40) { photo }, 50, 40, RevealParams(amount, seed = seed))

    @Test
    fun `nothing has come through at none and everything at full`() {
        assertTrue(at(0).all { it == look })
        assertTrue(at(100).all { it == photo })
    }

    @Test
    fun `more comes through as the amount rises, and what is through stays through`() {
        var before = at(0)
        var shown = 0
        for (amount in 10..100 step 10) {
            val now = at(amount)
            for (i in now.indices) if (before[i] == photo) assertEquals("pixel $i at $amount", photo, now[i])
            val count = now.count { it == photo }
            assertTrue("at $amount", count > shown)
            shown = count
            before = now
        }
    }

    @Test
    fun `a block gives way whole, and the same settings give the same picture`() {
        val half = at(50)
        val side = 3 // 16 thousandths of 200 px
        for (y in 0 until h step side) for (x in 0 until w step side) {
            val first = half[y * w + x]
            for (dy in 0 until side) for (dx in 0 until side) {
                if (y + dy < h && x + dx < w) assertEquals(first, half[(y + dy) * w + x + dx])
            }
        }
        assertArrayEquals(half, at(50))
        assertTrue(!half.contentEquals(at(50, seed = 2)))
    }
}
