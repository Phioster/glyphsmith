package org.phioster.glyphsmith.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Only [StillSource] is covered: [VideoSource] needs a real decoder and a real file, neither
 * of which a JVM unit test has. What is worth pinning down here is the contract the rest of
 * the app leans on — that a still ignores the position it is handed.
 */
class SourceTest {

    private val pixels = IntArray(12) { it }

    @Test
    fun `a still returns the same buffer at every position`() {
        val source = StillSource(pixels, 4, 3)
        // Identity, not equality: the render loop calls this once per frame, and copying the
        // image each time would turn a 60-frame export into 60 needless allocations.
        assertSame(pixels, source.pixelsAt(0f))
        assertSame(pixels, source.pixelsAt(0.5f))
        assertSame(pixels, source.pixelsAt(1f))
        assertSame(pixels, source.pixelsAt(-3f))
    }

    @Test
    fun `a still is not moving and closing it is harmless`() {
        val source = StillSource(pixels, 4, 3)
        assertFalse(source.isMoving)
        source.close()
        source.close()
        assertSame(pixels, source.pixelsAt(0f))
    }
}
