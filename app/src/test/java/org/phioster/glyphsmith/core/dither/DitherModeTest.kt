package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.core.dither.DitherCategory
import org.phioster.glyphsmith.core.dither.Dither

class DitherModeTest {

    @Test
    fun `every label is unique`() {
        val labels = DitherMode.entries.map { it.label }
        val duplicates = labels.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("two styles share a label: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `no label is blank`() {
        DitherMode.entries.forEach { assertTrue("${it.name} has no label", it.label.isNotBlank()) }
    }

    /**
     * The category is what the picker sorts by, and the picker is the only way to reach a
     * style. A style whose category disagrees with what it actually does is filed where
     * nobody will look for it.
     */
    @Test
    fun `the ordered category holds exactly the matrix-driven modes`() {
        DitherMode.entries.forEach { mode ->
            assertEquals(
                "${mode.name} is ${mode.category} but isOrdered says otherwise",
                Dither.isOrdered(mode),
                mode.category == DitherCategory.ORDERED,
            )
        }
    }

    /**
     * A modulation style drives its threshold from a surface, and there are three honest
     * shelves for that: a repeating pattern, a special effect, or a deliberate fault. What it
     * may never be is error diffusion or an ordered matrix, which are different mechanisms
     * entirely — and that is what this guards.
     */
    @Test
    fun `modulation modes are not filed as diffusion or as a matrix`() {
        DitherMode.entries.filter { Dither.isModulation(it) }.forEach { mode ->
            assertTrue(
                "${mode.name} modulates but is filed under ${mode.category}",
                mode.category in setOf(
                    DitherCategory.PATTERNED,
                    DitherCategory.SPECIAL,
                    DitherCategory.GLITCH,
                ),
            )
        }
    }

    @Test
    fun `only NONE is basic`() {
        assertEquals(
            listOf(DitherMode.NONE),
            DitherMode.entries.filter { it.category == DitherCategory.BASIC },
        )
    }

    /** An empty section would draw a header with nothing under it. The picker skips those. */
    @Test
    fun `every category the picker would draw has members`() {
        val used = DitherMode.entries.map { it.category }.toSet()
        assertEquals(DitherCategory.entries.toSet(), used)
    }

    /** Region styles never threshold, so nothing may claim both. */
    @Test
    fun `no style both flattens regions and thresholds cells`() {
        DitherMode.entries.filter { Dither.isRegion(it) }.forEach { mode ->
            assertTrue(
                "${mode.name} flattens regions and yet claims a threshold",
                !Dither.isThresholdBased(mode),
            )
            assertTrue("${mode.name} must be precomputed", Dither.isPrecomputed(mode))
        }
    }

    @Test
    fun `category labels are distinct and readable`() {
        val labels = DitherCategory.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        labels.forEach { assertTrue(it.isNotBlank()) }
    }
}
