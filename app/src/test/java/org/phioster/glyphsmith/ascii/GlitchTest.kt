package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glitch and special styles, held to the claims their comments make.
 *
 * These are the ones with no published maths behind them — the vendor names them and gives a
 * line of description, and the implementation is this app's own. That makes the tests more
 * important rather than less: there is no reference to fall back on, so the only thing keeping
 * a style honest is a statement of what makes it that style and a check that it still holds.
 */
class GlitchTest {

    private fun kernel(mode: DitherMode) = Dither.diffusionKernel(mode)

    /** Denying it the diagonal is what makes the cracks run without ever crossing. */
    @Test
    fun `cracked diffuse never touches a diagonal`() {
        kernel(DitherMode.CRACKED_DIFFUSE).forEach { tap ->
            assertTrue("a diagonal tap at ${tap.dx},${tap.dy}", tap.dx == 0 || tap.dy == 0)
        }
    }

    /** Nothing reaches the row below, so each line drifts on its own. That is the artefact. */
    @Test
    fun `artifact glitch never leaves its row`() {
        kernel(DitherMode.ARTIFACT_GLITCH).forEach { tap ->
            assertEquals("it reached row ${tap.dy}", 0, tap.dy)
        }
    }

    @Test
    fun `the Atkinson family discards a fixed share at every brightness`() {
        assertEquals(0.75f, kernel(DitherMode.ATKINSON).sumOf { it.weight.toDouble() }.toFloat(), 1e-4f)
        assertEquals(0.75f, kernel(DitherMode.ATKINSON_VHS).sumOf { it.weight.toDouble() }.toFloat(), 1e-4f)

        // The modulated one shifts its bias with brightness but must never shift how much it
        // keeps — otherwise the contrast would drift with the picture instead of the grain.
        val sums = (0..64).map { i ->
            Dither.variableKernel(DitherMode.ATKINSON_LINE_MOD, i / 64f)!!
                .sumOf { it.weight.toDouble() }.toFloat()
        }
        assertEquals("the loss is not constant", 1, sums.map { Math.round(it * 1000f) }.toSet().size)
    }

    @Test
    fun `atkinson line modulation leans along the row as it brightens`() {
        val dark = Dither.variableKernel(DitherMode.ATKINSON_LINE_MOD, 0f)!!
        val light = Dither.variableKernel(DitherMode.ATKINSON_LINE_MOD, 1f)!!
        val along = { k: List<DiffusionTap> -> k.filter { it.dy == 0 }.sumOf { it.weight.toDouble() } }
        assertTrue("brightness did not shift the smear", along(light) > along(dark))
    }

    /** The whole point of the name: error travels further along a row than down the image. */
    @Test
    fun `stucki diffusion lines really do favour the row`() {
        val k = kernel(DitherMode.STUCKI_LINES)
        val along = k.filter { it.dy == 0 }.sumOf { it.weight.toDouble() }
        val down = k.filter { it.dy > 0 }.sumOf { it.weight.toDouble() }
        assertTrue("it sends $along along and $down down", along > down)
        // But not purely horizontal — that would be Artifact Glitch again.
        assertTrue("nothing reaches the next row at all", down > 0.1)
    }

    // --- the two that need a neighbourhood ---------------------------------------------

    private fun flatField(cols: Int, rows: Int) = FloatArray(cols * rows) { 0.5f }

    private fun edgeField(cols: Int, rows: Int) = FloatArray(cols * rows) { i ->
        if (i % cols < cols / 2) 0f else 1f
    }

    /**
     * The claim that justifies these not being kernels: less error crosses a hard edge than
     * crosses flat ground. If this fails they are ordinary one-dimensional diffusion with an
     * expensive neighbourhood scan attached.
     */
    @Test
    fun `contrast aware diffusion gives way at an edge`() {
        val cols = 32
        val rows = 8
        val flat = Directional.contrastAware(flatField(cols, rows), cols, rows, 4, 1f, false)
        val edge = Directional.contrastAware(edgeField(cols, rows), cols, rows, 4, 1f, false)

        // On flat ground the error accumulates and the row eventually breaks into two values;
        // across the edge the two halves stay clean.
        val leftHalf = (0 until cols / 2).map { edge[it] }.toSet()
        assertEquals("the dark side was smeared into", setOf(0), leftHalf)
        assertTrue("the flat field never dithered at all", flat.toSet().size > 1)
    }

    @Test
    fun `both contrast aware axes fill the grid and differ from one another`() {
        val cols = 24
        val rows = 24
        val luma = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }
        val across = Directional.contrastAware(luma, cols, rows, 5, 1f, vertical = false)
        val down = Directional.contrastAware(luma, cols, rows, 5, 1f, vertical = true)
        assertEquals(cols * rows, across.size)
        assertTrue("an index left the ramp", across.all { it in 0..4 })
        assertTrue("the two axes agree exactly", !across.contentEquals(down))
    }

    @Test
    fun `the spiral covers every cell and is deterministic`() {
        val cols = 17
        val rows = 11
        val luma = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }
        val once = Directional.spiral(luma, cols, rows, 4, 1f)
        val twice = Directional.spiral(luma, cols, rows, 4, 1f)
        assertEquals(cols * rows, once.size)
        assertTrue("an index left the ramp", once.all { it in 0..3 })
        assertTrue("the spiral is not deterministic", once.contentEquals(twice))
    }

    /** A spiral has to organise itself around the middle, or it is just another scan order. */
    @Test
    fun `the spiral is not a row scan`() {
        val cols = 24
        val rows = 24
        val luma = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }
        val spiral = Directional.spiral(luma, cols, rows, 5, 1f)
        val curve = FractalDiffuse.quantise(luma, cols, rows, 5, 1f)
        assertTrue("the spiral matched the Hilbert walk", !spiral.contentEquals(curve))
    }
}
