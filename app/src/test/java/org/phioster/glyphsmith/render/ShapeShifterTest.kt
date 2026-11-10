package org.phioster.glyphsmith.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeShifterTest {

    private val two = listOf(ShapeBand(ShapeKind.EMPTY, 100, 50), ShapeBand(ShapeKind.CIRCLE, 100, 50))
    private val black = 0xFF000000.toInt()

    private fun settings(shifter: ShapeShifterParams = ShapeShifterParams(bands = two)) = RenderSettings(
        renderMode = RenderMode.ShapeShifter,
        colorMode = ColorMode.SINGLE,
        inkColor = 0xFFFFFFFF.toInt(),
        backgroundColor = black,
        shapeShifter = shifter,
    )

    private fun flat(tone: Float, cols: Int = 4, rows: Int = 4) = CellGrid(cols, rows, FloatArray(cols * rows) { tone }, null)

    @Test
    fun `bounds follow the widths and end at one`() {
        val bands = listOf(ShapeBand(width = 25), ShapeBand(width = 25), ShapeBand(width = 50))
        assertArrayEquals(floatArrayOf(0.25f, 0.5f, 1f), ShapeBands.bounds(bands), 1e-6f)
        assertEquals(0, ShapeBands.indexOf(ShapeBands.bounds(bands), 0.1f))
        assertEquals(2, ShapeBands.indexOf(ShapeBands.bounds(bands), 1f))
        assertEquals("0–63", ShapeBands.rangeLabel(bands, 0))
        assertEquals("128–255", ShapeBands.rangeLabel(bands, 2))
    }

    @Test
    fun `split, remove, even and reverse edit the list`() {
        val split = ShapeBands.split(two, 1)
        assertEquals(listOf(ShapeKind.EMPTY, ShapeKind.CIRCLE, ShapeKind.CIRCLE), split.map { it.shape })
        assertEquals(25, split[1].width)
        assertEquals(two, ShapeBands.remove(split, 2).map { it.copy(width = 50) })
        assertEquals(1, ShapeBands.remove(ShapeBands.remove(two, 0), 0).size)
        assertTrue(ShapeBands.distribute(split).all { it.width == 50 })
        val reversed = ShapeBands.reverse(listOf(ShapeBand(ShapeKind.EMPTY, 10, 20), ShapeBand(ShapeKind.STAR4, 90, 80)))
        assertEquals(listOf(ShapeBand(ShapeKind.STAR4, 90, 20), ShapeBand(ShapeKind.EMPTY, 10, 80)), reversed)
        var many = two
        repeat(20) { many = ShapeBands.split(many, 0) }
        assertEquals(ShapeBands.MAX_BANDS, many.size)
    }

    @Test
    fun `dark cells in an empty band draw nothing and bright cells draw shapes`() {
        val dark = ShapeShifter.render(ShapeShifter.field(flat(0.1f), settings()), 12, settings())
        assertTrue(dark.data.all { it == black })
        val bright = ShapeShifter.render(ShapeShifter.field(flat(0.9f), settings()), 12, settings())
        assertEquals(0xFFFFFFFF.toInt(), bright.data[6 * bright.width + 6])
        assertEquals("the corner between four circles stays background", black, bright.data[0])
    }

    @Test
    fun `band size and tone scaling set the radius`() {
        val big = settings(ShapeShifterParams(bands = listOf(ShapeBand(ShapeKind.SQUARE, 200)), shapeSize = 100))
        assertEquals(1f, ShapeShifter.field(flat(0.5f), big).radius[0], 1e-6f)
        val scaled = settings(ShapeShifterParams(bands = listOf(ShapeBand(ShapeKind.SQUARE, 100)), toneScaling = 100))
        assertEquals(0.25f, ShapeShifter.field(flat(0.5f), scaled).radius[0], 1e-6f)
    }

    @Test
    fun `offset shifts every other row by half a cell`() {
        val field = ShapeShifter.field(flat(0.9f), settings(ShapeShifterParams(bands = two, layout = ShapeLayout.OFFSET)))
        assertEquals(0.5f, field.x[0], 1e-6f)
        assertEquals(1f, field.x[4], 1e-6f)
    }

    @Test
    fun `feather mixes the bands around their boundary only`() {
        val feathered = settings(ShapeShifterParams(bands = two, feather = 100))
        val edge = ShapeShifter.field(flat(0.5f, 8, 8), feathered).shape.toSet()
        assertEquals(setOf(ShapeKind.EMPTY, ShapeKind.CIRCLE), edge)
        assertEquals(setOf(ShapeKind.CIRCLE), ShapeShifter.field(flat(0.95f, 8, 8), feathered).shape.toSet())
    }

    @Test
    fun `a transparent background stays transparent around the shapes`() {
        val clear = settings().copy(transparentBackground = true)
        val out = ShapeShifter.render(ShapeShifter.field(flat(0.9f), clear), 12, clear)
        assertEquals(0, out.data[0] ushr 24)
        assertEquals(255, out.data[6 * out.width + 6] ushr 24)
    }

    @Test
    fun `the block is capped by the source and by a pixel budget`() {
        assertEquals(24, ShapeShifter.blockFor(40, 30, 8, 8192))
        assertEquals(10, ShapeShifter.blockFor(40, 30, 8, 400))
        assertEquals(2, ShapeShifter.blockFor(2000, 2000, 16, 8192))
    }

    @Test
    fun `the svg places one use per drawn shape and defines only what it uses`() {
        val params = settings()
        val grid = CellGrid(2, 1, floatArrayOf(0.1f, 0.9f), null)
        val (svg, count) = ShapeVector.svg(ShapeShifter.field(grid, params), 10, params)
        assertEquals(1, count)
        assertEquals(1, Regex("<use ").findAll(svg).count())
        assertTrue(svg.contains("id=\"s${ShapeKind.CIRCLE.ordinal}\""))
        assertTrue(svg.contains("translate(15.0 5.0) scale(5.0)"))
        assertTrue(svg.contains("fill=\"#ffffff\""))
    }

    @Test
    fun `colour per band overrides the colour mode`() {
        val red = 0xFFFF0000.toInt()
        val bands = listOf(ShapeBand(ShapeKind.SQUARE, color = red))
        val own = ShapeShifter.field(flat(0.5f), settings(ShapeShifterParams(bands = bands, perBandColor = true)))
        assertEquals(red, own.color[0])
        val shared = ShapeShifter.field(flat(0.5f), settings(ShapeShifterParams(bands = bands)))
        assertEquals(0xFFFFFFFF.toInt(), shared.color[0])
    }

    @Test
    fun `rotation jitter turns cells apart in whole steps and none leaves them alike`() {
        val still = ShapeShifter.field(flat(0.9f, 8, 8), settings(ShapeShifterParams(bands = two, rotation = 30)))
        assertEquals(setOf(30), still.rotation.toSet())
        val loose = ShapeShifter.field(flat(0.9f, 8, 8), settings(ShapeShifterParams(bands = two, rotationJitter = 100)))
        assertTrue(loose.rotation.toSet().size > 4)
        assertTrue(loose.rotation.all { it % 6 == 0 && it in 0..359 })
    }

    @Test
    fun `crisp edges leave only full and empty pixels`() {
        val crisp = settings(ShapeShifterParams(bands = two, crispEdges = true))
        val out = ShapeShifter.render(ShapeShifter.field(flat(0.9f), crisp), 12, crisp)
        assertEquals(setOf(black, 0xFFFFFFFF.toInt()), out.data.toSet())
        val soft = ShapeShifter.render(ShapeShifter.field(flat(0.9f), settings()), 12, settings())
        assertTrue(soft.data.toSet().size > 2)
    }
}
