package org.phioster.glyphsmith.pipeline

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class OpaqueTest {

    @Test
    fun `clear pixels turn black, solid ones stay, half ones halve`() {
        val out = onBlack(intArrayOf(0x00FFFFFF, 0xFF123456.toInt(), 0x80FF8000.toInt()))
        assertArrayEquals(intArrayOf(0xFF000000.toInt(), 0xFF123456.toInt(), 0xFF804000.toInt()), out)
    }
}
