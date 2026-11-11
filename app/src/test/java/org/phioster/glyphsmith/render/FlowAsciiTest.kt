package org.phioster.glyphsmith.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class FlowAsciiTest {

    private val side = 160

    private fun grid(luma: (Int, Int) -> Float): CellGrid {
        val values = FloatArray(side * side) { luma(it % side, it / side) }
        return CellGrid(side, side, values, IntArray(side * side) { 0xFFCC8844.toInt() })
    }

    /** Bright stripes running straight down. */
    private val stripes = grid { x, _ -> if ((x / 16) % 2 == 0) 0.9f else 0.3f }

    /** A bright disc on grey: a closed form for lines to run round. */
    private val disc = grid { x, y -> if (hypot(x - 80f, y - 80f) < 45f) 0.9f else 0.35f }

    private fun settings(flow: FlowAsciiParams = FlowAsciiParams()) =
        RenderSettings(renderMode = RenderMode.FlowAscii, colorMode = ColorMode.SOURCE, flowAscii = flow)

    @Test
    fun `beside an edge the flow runs along it, and where nothing happens it lies flat`() {
        val field = FlowField.of(stripes.luma, side, side)
        // On the boundary between two stripes, in the middle of the picture: straight down.
        val onEdge = field.angle[80 * side + 64]
        assertTrue("angle $onEdge", abs(abs(sin(onEdge)) - 1f) < 0.05f)
        assertTrue(field.sure[80 * side + 64] > 0.5f)

        val flat = FlowField.of(FloatArray(side * side) { 0.5f }, side, side)
        assertTrue(flat.sure.all { it == 0f })
        assertTrue(flat.angle.all { abs(it) < 1e-4f })
    }

    @Test
    fun `rows keep their distance and stay inside the picture`() {
        val lines = Streamlines.trace(FlowField.of(disc.luma, side, side), 5f, 4f)
        assertTrue(lines.count > 300)
        for (i in 0 until lines.count) {
            assertTrue(lines.x[i] >= 0f && lines.x[i] < side && lines.y[i] >= 0f && lines.y[i] < side)
        }
        // No two points nearer than the step along a row allows, anywhere: nothing is set twice.
        var nearest = Float.MAX_VALUE
        for (i in 0 until lines.count) for (j in i + 1 until lines.count) {
            nearest = minOf(nearest, hypot(lines.x[i] - lines.x[j], lines.y[i] - lines.y[j]))
        }
        assertTrue("nearest pair $nearest", nearest > 3.2f)
    }

    @Test
    fun `a line round a closed form goes round once, not again and again`() {
        // Were it to lap the disc, far more points would crowd its rim than its length has room for.
        val lines = Streamlines.trace(FlowField.of(disc.luma, side, side), 5f, 4f)
        val onRim = (0 until lines.count).count { abs(hypot(lines.x[it] - 80f, lines.y[it] - 80f) - 45f) < 2.5f }
        val roomFor = (2 * Math.PI * 45 / 4).toInt()
        assertTrue("$onRim on a rim with room for $roomFor", onRim <= roomFor * 2)
    }

    @Test
    fun `characters lie along the flow and every picture gives the same marks twice`() {
        val marks = FlowAscii.marks(stripes, settings())
        assertTrue(marks.count > 500)
        assertTrue((0 until marks.count).all { marks.glyph[it] != ' ' && marks.glyph[it] != FlowMarks.HAIR })
        // Most marks on a picture of upright stripes stand upright themselves, or lie flat in a stripe.
        val upright = (0 until marks.count).count { abs(cos(marks.angle[it])) < 0.2f }
        assertTrue("$upright of ${marks.count}", upright > marks.count / 4)
        val again = FlowAscii.marks(stripes, settings())
        assertEquals(marks.count, again.count)
        assertTrue(marks.x.copyOf(marks.count).contentEquals(again.x.copyOf(again.count)))
    }

    @Test
    fun `black stays black, and the threshold decides what black is`() {
        val dim = grid { _, _ -> 0.1f }
        assertEquals(0, FlowAscii.marks(grid { _, _ -> 0f }, settings()).count)
        assertTrue(FlowAscii.marks(dim, settings()).count > 0)
        assertEquals(0, FlowAscii.marks(dim, settings(FlowAsciiParams(blackBelow = 20))).count)
    }

    @Test
    fun `fur is hairs, many more of them, longer where it is bright`() {
        val glyphs = FlowAscii.marks(stripes, settings())
        val fur = FlowAscii.marks(stripes, settings(FlowAsciiParams(style = FlowStyle.FUR)))
        assertTrue(fur.count > glyphs.count * 3)
        assertTrue((0 until fur.count).all { fur.glyph[it] == FlowMarks.HAIR && fur.half[it] > 0f })
        fun mean(bright: Boolean): Float {
            val picked = (0 until fur.count).filter { (stripes.luma[fur.y[it].toInt().coerceIn(0, side - 1) * side + fur.x[it].toInt().coerceIn(0, side - 1)] > 0.5f) == bright }
            return picked.map { fur.half[it] }.average().toFloat()
        }
        assertTrue(mean(true) > mean(false) * 1.3f)
    }

    @Test
    fun `tilt changes the characters, wider spacing thins them, the outline adds line characters`() {
        val plain = FlowAscii.marks(disc, settings())
        val flat = FlowAscii.marks(disc, settings(FlowAsciiParams(byTilt = false)))
        assertTrue(String(plain.glyph, 0, plain.count).any { it in "c()C-" })
        assertTrue(String(flat.glyph, 0, flat.count).none { it in "c()C-" })
        assertTrue(FlowAscii.marks(disc, settings(FlowAsciiParams(spacing = 180))).count < plain.count * 0.7f)
        val outlined = FlowAscii.marks(disc, settings(FlowAsciiParams(outline = true)))
        assertTrue(outlined.count > plain.count)
        assertTrue(String(outlined.glyph, plain.count, outlined.count - plain.count).all { it in "-\\|/" })
    }

    @Test
    fun `colour follows the colour mode`() {
        val source = FlowAscii.marks(stripes, settings())
        assertTrue((0 until source.count).all { source.color[it] == 0xFFCC8844.toInt() })
        val single = FlowAscii.marks(stripes, settings().copy(colorMode = ColorMode.SINGLE, inkColor = 0x112233))
        assertTrue((0 until single.count).all { single.color[it] == 0xFF112233.toInt() })
    }

    @Test
    fun `any source comes down to the working size`() {
        assertEquals(1, FlowAscii.cellFor(1024, 480))
        assertEquals(2, FlowAscii.cellFor(1025, 480))
        assertEquals(2, FlowAscii.cellFor(2048, 2048))
        assertEquals(4, FlowAscii.cellFor(4000, 3000))
    }
}
