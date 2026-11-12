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

    @Test
    fun `a loop shows the frame its moment falls on, and wraps`() {
        assertEquals(0, LayerImages.frameAt(0f, 24))
        assertEquals(12, LayerImages.frameAt(0.5f, 24))
        assertEquals(23, LayerImages.frameAt(0.999f, 24))
        assertEquals(0, LayerImages.frameAt(1f, 24))
        assertEquals(6, LayerImages.frameAt(1.25f, 24))
        assertEquals(0, LayerImages.frameAt(0.7f, 1))
        assertEquals(0, LayerImages.frameAt(Float.NaN, 24))
    }

    @Test
    fun `a loop's frames are fetched as needed, and a still stays what it was`() {
        val asked = mutableListOf<Int>()
        LayerImages.put("loop", LayerImages.Picture(IntArray(1) { -1 }, 1, 1))
        LayerImages.put("still", LayerImages.Picture(IntArray(1) { 7 }, 1, 1))
        LayerImages.setLoop("loop", 4)
        LayerImages.frameLoader = { _, index ->
            asked += index
            LayerImages.Picture(IntArray(1) { index }, 1, 1)
        }
        assertEquals(4, LayerImages.frames("loop"))
        assertEquals(1, LayerImages.frames("still"))
        assertEquals(2, LayerImages.at("loop", 0.5f)?.pixels?.get(0))
        assertEquals(2, LayerImages.at("loop", 0.505f)?.pixels?.get(0))
        assertEquals(listOf(2), asked)
        assertEquals(7, LayerImages.at("still", 0.5f)?.pixels?.get(0))

        // A frame that cannot be fetched leaves the still in its place.
        LayerImages.frameLoader = { _, _ -> null }
        assertEquals(-1, LayerImages.at("loop", 0f)?.pixels?.get(0))

        LayerImages.keepOnly(listOf("still"))
        assertEquals(1, LayerImages.frames("loop"))
        LayerImages.frameLoader = null
    }

    @Test
    fun `a moment between two frames is the two mixed, and the last runs into the first`() {
        fun grey(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
        LayerImages.put("fade", LayerImages.Picture(IntArray(1), 1, 1))
        LayerImages.setLoop("fade", 4)
        LayerImages.frameLoader = { _, index -> LayerImages.Picture(IntArray(1) { grey(index * 40) }, 1, 1) }
        assertEquals(grey(40), LayerImages.at("fade", 0.25f)?.pixels?.get(0))
        assertEquals(grey(60), LayerImages.at("fade", 0.375f)?.pixels?.get(0))
        // Halfway from the last frame back to the first.
        assertEquals(grey(60), LayerImages.at("fade", 0.875f)?.pixels?.get(0))
        LayerImages.keepOnly(emptyList())
        LayerImages.frameLoader = null
    }

    @Test
    fun `light fading in keeps its colour and does not go through black`() {
        val empty = LayerImages.Picture(IntArray(1), 1, 1)
        val red = LayerImages.Picture(IntArray(1) { 0xFFFF0000.toInt() }, 1, 1)
        val half = LayerImages.mixed(empty, red, 0.5f).pixels[0]
        assertEquals(0x80, half ushr 24)
        assertEquals(0xFF0000, half and 0xFFFFFF)
        assertEquals(red.pixels[0], LayerImages.mixed(red, red, 0.3f).pixels[0])
        // Frames of different sizes cannot be mixed; the first is shown.
        assertEquals(0, LayerImages.mixed(empty, LayerImages.Picture(IntArray(4), 2, 2), 0.5f).pixels[0])
    }
}
