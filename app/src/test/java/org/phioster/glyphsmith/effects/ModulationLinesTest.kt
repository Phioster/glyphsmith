package org.phioster.glyphsmith.effects

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext

/**
 * The axis, and the invariant it must not break.
 *
 * This pass runs its rows in parallel and each band owns its own rows of the output. Horizontal
 * lines reach past the band to find the lines whose dots land in it; vertical lines never leave
 * the row they are drawn on. Getting that wrong is not a visible bug — it is two workers writing
 * the same pixel, which shows up as a different image on a second run and nothing else.
 */
class ModulationLinesTest {

    private val side = 240

    private fun ctx(time: Float = 0f) = RenderContext(maxSide = 1024, time = time)

    /** A gradient with a bright disc, so displacement has something to follow. */
    private fun image() = Pixels(
        IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            val disc = if ((x - 150) * (x - 150) + (y - 90) * (y - 90) < 2000) 120 else 0
            val v = (x * 180 / side + y * 60 / side + disc).coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        },
        side,
        side,
    )

    private fun on(axis: LineAxis) =
        ModulationLinesParams(enabled = true, axis = axis, amplitude = 12, waveMix = 40)

    /** The default is what it drew before the axis existed, so no saved preset moves. */
    @Test
    fun `the axis defaults to horizontal`() {
        assertEquals(LineAxis.HORIZONTAL, ModulationLinesParams().axis)
    }

    @Test
    fun `the two axes draw different pictures`() {
        val across = ModulationLines.apply(image(), on(LineAxis.HORIZONTAL), ctx()).data
        val down = ModulationLines.apply(image(), on(LineAxis.VERTICAL), ctx()).data

        assertTrue("the axis changed nothing", !across.contentEquals(down))
    }

    /**
     * Both axes reproducible, which is the assertion that actually guards the parallelism.
     *
     * The bands are handed out by the runtime, so a pass that wrote outside its own rows would
     * not fail every time — it would fail on the run where two workers happened to overlap. Two
     * renders of the same input have to be identical for that to be ruled out.
     */
    @Test
    fun `both axes are reproducible`() {
        LineAxis.entries.forEach { axis ->
            val first = ModulationLines.apply(image(), on(axis), ctx()).data
            val second = ModulationLines.apply(image(), on(axis), ctx()).data

            assertArrayEquals("$axis is not reproducible", first, second)
        }
    }

    @Test
    fun `both axes keep the image size and its alpha`() {
        LineAxis.entries.forEach { axis ->
            val out = ModulationLines.apply(image(), on(axis), ctx())

            assertEquals("$axis changed the width", side, out.width)
            assertEquals("$axis changed the height", side, out.height)
            assertTrue("$axis lost the alpha", out.data.all { ((it ushr 24) and 0xFF) == 0xFF })
        }
    }

    /** Vertical lines are columns: the ink has to reach both ends of the picture. */
    @Test
    fun `a vertical field draws in the top and the bottom rows`() {
        val params = on(LineAxis.VERTICAL).copy(backgroundColor = 0xFF000000.toInt())
        val out = ModulationLines.apply(image(), params, ctx())

        val top = (0 until side).count { out.data[it] != 0xFF000000.toInt() }
        val bottom = (0 until side).count { out.data[(side - 1) * side + it] != 0xFF000000.toInt() }

        assertTrue("nothing was drawn in the top row", top > 0)
        assertTrue("nothing was drawn in the bottom row", bottom > 0)
    }

    /** And the clock still reaches the wave on the new axis. */
    @Test
    fun `a vertical field still moves with the clock`() {
        val params = on(LineAxis.VERTICAL).copy(waveSpeed = 80, waveMix = 90)
        val atStart = ModulationLines.apply(image(), params, ctx(time = 0f)).data
        val laterOn = ModulationLines.apply(image(), params, ctx(time = 0.3f)).data

        assertTrue("the wave did not move", !atStart.contentEquals(laterOn))
    }

    /** Disabled is still the identical buffer, on either axis. */
    @Test
    fun `a disabled pass returns the identical buffer`() {
        LineAxis.entries.forEach { axis ->
            val input = image()
            val out = ModulationLines.apply(input, ModulationLinesParams(axis = axis), ctx())

            assertTrue("$axis copied while disabled", input.data === out.data)
        }
    }
}
