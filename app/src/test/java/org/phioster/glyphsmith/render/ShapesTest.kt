package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapesTest {

    private val drawn = ShapeKind.entries.filter { it != ShapeKind.EMPTY }

    @Test
    fun `every shape stays inside the unit box`() {
        drawn.forEach { kind ->
            Shapes.contours(kind).forEach { c ->
                assertTrue("$kind leaves the box", c.all { it in -1.0001f..1.0001f })
            }
        }
    }

    @Test
    fun `every shape fills a real part of its box`() {
        drawn.forEach { kind ->
            val c = Shapes.contours(kind)
            var hits = 0
            for (y in -10..10) for (x in -10..10) if (Shapes.inside(c, x / 10f + 0.01f, y / 10f + 0.01f)) hits++
            assertTrue("$kind covers $hits of 441 probes", hits in 40..440)
        }
    }

    @Test
    fun `empty draws nothing and a ring has a hole`() {
        assertTrue(Shapes.contours(ShapeKind.EMPTY).isEmpty())
        val ring = Shapes.contours(ShapeKind.RING)
        assertFalse(Shapes.inside(ring, 0.01f, 0.01f))
        assertTrue(Shapes.inside(ring, 0.8f, 0.01f))
        assertTrue(Shapes.inside(Shapes.contours(ShapeKind.CIRCLE), 0.01f, 0.01f))
    }

    @Test
    fun `path data is one closed sub-path per outline`() {
        drawn.forEach { kind ->
            val d = Shapes.pathData(kind)
            assertTrue(d.startsWith("M"))
            assertEquals(Shapes.contours(kind).size, d.count { it == 'Z' })
        }
    }

    @Test
    fun `the bayer tiles fill two, six and ten of sixteen cells`() {
        fun filled(kind: ShapeKind): Int {
            val c = Shapes.contours(kind)
            return (0 until 16).count { Shapes.inside(c, -0.75f + (it % 4) * 0.5f, -0.75f + (it / 4) * 0.5f) }
        }
        assertEquals(listOf(2, 6, 10), listOf(ShapeKind.BAYER_13, ShapeKind.BAYER_38, ShapeKind.BAYER_63).map(::filled))
    }

    @Test
    fun `the triangles point where their names say`() {
        assertTrue(Shapes.inside(Shapes.contours(ShapeKind.TRIANGLE), 0f, -0.9f))
        assertTrue(Shapes.inside(Shapes.contours(ShapeKind.TRIANGLE_DOWN), 0f, 0.9f))
        assertTrue(Shapes.inside(Shapes.contours(ShapeKind.TRIANGLE_LEFT), -0.9f, 0f))
        assertTrue(Shapes.inside(Shapes.contours(ShapeKind.TRIANGLE_RIGHT), 0.9f, 0f))
    }
}
