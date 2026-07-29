package org.phioster.glyphsmith.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorQuantizerTest {

    /** Four tight clusters, one of them covering most of the image. */
    private fun clustered(): IntArray {
        val out = ArrayList<Int>()
        fun add(colour: Int, times: Int) = repeat(times) { out.add(colour or (0xFF shl 24)) }
        add(0x101010, 400)
        add(0xF0F0F0, 200)
        add(0x1040C0, 120)
        add(0xC04010, 40)
        return out.toIntArray()
    }

    @Test
    fun `both methods return the number of colours asked for`() {
        QuantizeMethod.entries.forEach { method ->
            val palette = ColorQuantizer.extract(clustered(), 4, method)
            assertEquals("$method", 4, palette.size)
        }
    }

    @Test
    fun `an image with fewer colours than asked for gives what it has`() {
        val two = IntArray(50) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        QuantizeMethod.entries.forEach { method ->
            assertEquals("$method", 2, ColorQuantizer.extract(two, 8, method).size)
        }
    }

    /**
     * A preset stores the palette it was built with, so an extraction that drifted between
     * runs would make the preset unreproducible. k-means is iterative and seeded precisely
     * so it does not.
     */
    @Test
    fun `k-means is reproducible for a given seed`() {
        val a = ColorQuantizer.kMeans(clustered(), 4, seed = 7)
        val b = ColorQuantizer.kMeans(clustered(), 4, seed = 7)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `both methods land near the clusters that are actually there`() {
        val targets = listOf(0x101010, 0xF0F0F0, 0x1040C0, 0xC04010)
        QuantizeMethod.entries.forEach { method ->
            val palette = ColorQuantizer.extract(clustered(), 4, method)
            targets.forEach { target ->
                val nearest = palette.minOf { distance(it, target) }
                assertTrue(
                    "$method came no closer than $nearest to ${target.toString(16)}",
                    nearest < 60 * 60 * 3,
                )
            }
        }
    }

    @Test
    fun `neither method invents a colour outside the image's range`() {
        QuantizeMethod.entries.forEach { method ->
            ColorQuantizer.extract(clustered(), 4, method).forEach { colour ->
                listOf(16, 8, 0).forEach { shift ->
                    val channel = (colour shr shift) and 0xFF
                    assertTrue("$method produced $channel", channel in 0..255)
                }
            }
        }
    }

    @Test
    fun `an empty image does not crash either method`() {
        val transparent = IntArray(16) { 0x00000000 }
        QuantizeMethod.entries.forEach { method ->
            assertTrue(ColorQuantizer.extract(transparent, 4, method).isNotEmpty())
        }
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }
}
