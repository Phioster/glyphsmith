package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.dither.Riemersma
import org.phioster.glyphsmith.core.dither.Ostromoukhov
import org.phioster.glyphsmith.core.dither.FractalDiffuse
import org.phioster.glyphsmith.core.dither.DotDiffusion
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.Dither

/**
 * The published algorithms, held to their published values.
 *
 * These are the three that were left out of earlier rounds because their tables could not be
 * verified. Each is now transcribed from a primary source, and each is checked here against
 * that source rather than against whatever the code happens to produce — a test written the
 * other way round would pass just as happily on a typo.
 */
class LiteratureTest {

    // --- Ostromoukhov ------------------------------------------------------------------

    @Test
    fun `the table has one row per tabulated level`() {
        assertEquals(128, Ostromoukhov.LEVELS)
    }

    /**
     * Spot values straight out of Appendix I. Level 0 is `13, 0, 5`; level 7 is `15, 3, 8`;
     * level 127, the mid-grey the table folds around, is `4, 1, 1`.
     */
    @Test
    fun `the tabulated coefficients match the paper`() {
        fun ratios(level: Int) = Ostromoukhov.kernelFor(level / 255f).map { it.weight }

        assertRatio(ratios(0), 13f, 0f, 5f)
        assertRatio(ratios(7), 15f, 3f, 8f)
        assertRatio(ratios(127), 4f, 1f, 1f)
    }

    private fun assertRatio(weights: List<Float>, a: Float, b: Float, c: Float) {
        val sum = a + b + c
        assertEquals(a / sum, weights[0], 1e-4f)
        assertEquals(b / sum, weights[1], 1e-4f)
        assertEquals(c / sum, weights[2], 1e-4f)
    }

    /** The paper states `D(i) == D(255 - i)`, which is why only half the table is written out. */
    @Test
    fun `the table is symmetric about mid grey`() {
        for (level in 0..127) {
            val low = Ostromoukhov.kernelFor(level / 255f).map { it.weight }
            val high = Ostromoukhov.kernelFor((255 - level) / 255f).map { it.weight }
            assertEquals("level $level breaks the mirror", low, high)
        }
    }

    @Test
    fun `every row conserves the error and none is empty`() {
        for (level in 0..255) {
            val kernel = Ostromoukhov.kernelFor(level / 255f)
            assertEquals("level $level has the wrong shape", 3, kernel.size)
            assertEquals("level $level leaks error", 1f, kernel.sumOf { it.weight.toDouble() }.toFloat(), 1e-4f)
        }
    }

    /** The three coefficients go right, down-left and down. Anywhere else is a lost error. */
    @Test
    fun `the coefficients land where the paper puts them`() {
        val kernel = Ostromoukhov.kernelFor(0.5f)
        assertEquals(listOf(1 to 0, -1 to 1, 0 to 1), kernel.map { it.dx to it.dy })
    }

    /** Returned by reference, not rebuilt — this runs once per cell. */
    @Test
    fun `the same level hands back the same instance`() {
        assertTrue(Ostromoukhov.kernelFor(0.3f) === Ostromoukhov.kernelFor(0.3f))
    }

    // --- Shiau-Fan ---------------------------------------------------------------------

    @Test
    fun `Shiau-Fan matches the figure it was transcribed from`() {
        val kernel = Dither.diffusionKernel(DitherMode.SHIAU_FAN)
        val expected = mapOf(
            (1 to 0) to 8 / 16f,
            (-3 to 1) to 1 / 16f,
            (-2 to 1) to 1 / 16f,
            (-1 to 1) to 2 / 16f,
            (0 to 1) to 4 / 16f,
        )
        assertEquals(expected.size, kernel.size)
        kernel.forEach { tap ->
            assertEquals("tap at ${tap.dx},${tap.dy}", expected[tap.dx to tap.dy], tap.weight)
        }
    }

    // --- Knuth's dot diffusion ---------------------------------------------------------

    @Test
    fun `the class matrix is a permutation of all sixty-four classes`() {
        val values = DotDiffusion.classes.flatMap { it.asList() }.sorted()
        assertEquals((0 until 64).toList(), values)
    }

    /**
     * Knuth's own check on his own construction: the diffusion instructions come to exactly
     * 256. A class matrix that is subtly wrong still halftones plausibly, so this number is
     * the only cheap way to know the transcription is right.
     */
    @Test
    fun `the class matrix compiles to the instruction count Knuth states`() {
        assertEquals(256, DotDiffusion.instructionCount())
    }

    /** Classes 62 and 63 have no higher neighbour — Knuth calls them barons, and they exist. */
    @Test
    fun `the two barons are the highest classes`() {
        val barons = (0 until 64).filter { k ->
            var found = false
            for (y in 0 until DotDiffusion.SIZE) {
                for (x in 0 until DotDiffusion.SIZE) {
                    if (DotDiffusion.classes[y][x] != k) continue
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            if (DotDiffusion.classAt(x + dx, y + dy) > k) found = true
                        }
                    }
                }
            }
            !found
        }
        assertEquals(listOf(62, 63), barons)
    }

    @Test
    fun `dot diffusion quantises every cell into the ramp`() {
        val cols = 24
        val rows = 16
        val luma = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }
        val out = DotDiffusion.quantise(luma, cols, rows, 6, 1f)
        assertEquals(cols * rows, out.size)
        assertTrue("an index left the ramp", out.all { it in 0..5 })
        assertTrue("the result is flat", out.toSet().size > 1)
    }

    // --- Fractal diffuse ----------------------------------------------------------------

    @Test
    fun `fractal diffuse covers the grid and stays in the ramp`() {
        val cols = 23
        val rows = 9
        val luma = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }
        val out = FractalDiffuse.quantise(luma, cols, rows, 4, 1f)
        assertEquals(cols * rows, out.size)
        assertTrue("an index left the ramp", out.all { it in 0..3 })
    }

    /** Same curve, different memory — if these ever agree, one of them lost its character. */
    @Test
    fun `fractal diffuse is not Riemersma`() {
        val cols = 32
        val rows = 32
        val luma = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }
        val fractal = FractalDiffuse.quantise(luma, cols, rows, 6, 1f)
        val riemersma = Riemersma.quantise(luma, cols, rows, 6, 1f)
        assertTrue("the two curve walkers agree exactly", !fractal.contentEquals(riemersma))
    }

    // --- Variable error -----------------------------------------------------------------

    @Test
    fun `variable error leans one way in light and the other in shadow`() {
        val dark = Dither.variableKernel(DitherMode.VARIABLE_ERROR, 0f)!!
        val light = Dither.variableKernel(DitherMode.VARIABLE_ERROR, 1f)!!
        val rightDark = dark.first { it.dx == 1 && it.dy == 0 }.weight
        val rightLight = light.first { it.dx == 1 && it.dy == 0 }.weight
        assertTrue("brightness did not shift the lean", rightLight > rightDark)
    }

    /** It leans, but it never leaks — that would be two changes at once. */
    @Test
    fun `variable error conserves the error at every brightness`() {
        for (i in 0..100) {
            val kernel = Dither.variableKernel(DitherMode.VARIABLE_ERROR, i / 100f)!!
            assertEquals(1f, kernel.sumOf { it.weight.toDouble() }.toFloat(), 1e-4f)
        }
    }
}
