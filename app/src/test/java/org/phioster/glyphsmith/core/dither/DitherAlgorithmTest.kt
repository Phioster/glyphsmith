package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the algorithm declarations have to be true about.
 *
 * The traits used to be a dozen hand-kept lists in [Dither]; they are now one declaration per
 * style. The lists could disagree with each other and still compile, and so can a declaration —
 * a style can be filed as modulation, given a label for a control it never reads, and look
 * perfectly reasonable in the table. These are the properties that catch that.
 */
class DitherAlgorithmTest {

    private val cols = 16
    private val rows = 16
    private val levels = 6

    /** A gradient, so a region style has something to average that is not flat. */
    private val ramp = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }

    /**
     * The declarations are shared, never rebuilt.
     *
     * Asking a style for its threshold now goes through the provider, once per cell of every
     * frame. A declaration built per call would put an allocation on that path and nothing else
     * would notice — the pictures would be identical and only the frame rate would say so.
     */
    @Test
    fun `a style's algorithm is declared once rather than rebuilt per call`() {
        DitherMode.entries.forEach { mode ->
            assertSame(
                "${mode.name} builds a new algorithm on every lookup",
                DitherProviders.of(mode).algorithm,
                DitherProviders.of(mode).algorithm,
            )
        }
    }

    @Test
    fun `every style names its pattern control`() {
        DitherMode.entries.forEach { mode ->
            val algorithm = DitherProviders.of(mode).algorithm
            assertTrue("${mode.name} has a blank period label", algorithm.periodLabel.isNotBlank())
            algorithm.densityLabel?.let {
                assertTrue("${mode.name} has a blank density label", it.isNotBlank())
            }
        }
    }

    /**
     * The second axis is labelled exactly where it does something.
     *
     * One field carries a dozen different meanings, and the panel only shows the slider when a
     * label exists. A style that reads the axis without declaring a label has a control the user
     * cannot reach; one that declares a label without reading it has a slider that does nothing.
     * Both are invisible in a screenshot and obvious here.
     */
    @Test
    fun `the second axis is labelled exactly where it changes the picture`() {
        DitherMode.entries.forEach { mode ->
            assertEquals(
                "${mode.name} labels its second axis \"${Dither.densityLabel(mode)}\"" +
                    " but reacts to it: ${reactsToDensity(mode)}",
                Dither.densityLabel(mode) != null,
                reactsToDensity(mode),
            )
        }
    }

    /**
     * Brightness is deliberately away from mid-grey: several styles push the threshold by
     * `value - 0.5`, and at exactly 0.5 the second axis would multiply zero and look inert.
     */
    private fun reactsToDensity(mode: DitherMode): Boolean {
        val low = PatternOptions(period = 6, density = 0)
        val high = PatternOptions(period = 6, density = 100)

        if (Dither.isRegion(mode)) {
            return !Regions.quantise(mode, ramp, cols, rows, levels, low)
                .contentEquals(Regions.quantise(mode, ramp, cols, rows, levels, high))
        }

        return (0 until 24).any { y ->
            (0 until 24).any { x ->
                Dither.threshold(mode, x, y, 0.4f, low) != Dither.threshold(mode, x, y, 0.4f, high)
            }
        }
    }
}
