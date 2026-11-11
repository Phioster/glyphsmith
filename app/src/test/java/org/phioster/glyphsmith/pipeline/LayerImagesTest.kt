package org.phioster.glyphsmith.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerImagesTest {

    private val solid = 0xFF336699.toInt()

    @Test
    fun `a picture is held by its id until it is no longer named`() {
        LayerImages.put("a", LayerImages.Picture(IntArray(4), 2, 2))
        LayerImages.put("b", LayerImages.Picture(IntArray(4), 2, 2))
        assertTrue(LayerImages.has("a"))
        LayerImages.keepOnly(listOf("b"))
        assertNull(LayerImages["a"])
        assertEquals(2, LayerImages["b"]?.width)
    }

    @Test
    fun `a picture is rendered to fit inside the frame whatever its shape`() {
        assertEquals(400, LayerImages.sideInside(1000, 500, 400, 400))
        assertEquals(400, LayerImages.sideInside(500, 1000, 400, 400))
        assertEquals(300, LayerImages.sideInside(100, 100, 800, 300))
        assertEquals(3, LayerImages.sideInside(4000, 10, 3, 3))
        assertEquals(1, LayerImages.sideInside(10, 10, 0, 0))
    }

    @Test
    fun `a rendering is cut back to the picture's silhouette`() {
        // Left half of the picture is clear, right half opaque; the rendering is twice its size.
        val picture = LayerImages.Picture(IntArray(4 * 4) { if (it % 4 < 2) 0x00000000 else solid }, 4, 4)
        val rendered = IntArray(8 * 8) { solid }
        LayerImages.cutOut(rendered, 8, 8, picture)
        for (i in rendered.indices) {
            val expected = if (i % 8 < 4) 0x00 else 0xFF
            assertEquals(expected, rendered[i] ushr 24)
            assertEquals(solid and 0xFFFFFF, rendered[i] and 0xFFFFFF)
        }
    }

    @Test
    fun `a half-clear edge keeps the weaker of the two alphas`() {
        val picture = LayerImages.Picture(intArrayOf(0x80FFFFFF.toInt()), 1, 1)
        val rendered = intArrayOf(solid, 0x40336699)
        LayerImages.cutOut(rendered, 2, 1, picture)
        assertEquals(0x80, rendered[0] ushr 24)
        assertEquals(0x40, rendered[1] ushr 24)
    }
}
