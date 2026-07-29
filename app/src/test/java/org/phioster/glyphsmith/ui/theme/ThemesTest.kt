package org.phioster.glyphsmith.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemesTest {

    private fun luminance(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    @Test
    fun `every theme has a distinct id and name`() {
        val ids = TermThemes.all.map { it.id }
        val names = TermThemes.all.map { it.name }
        assertEquals("two themes share an id", ids.size, ids.toSet().size)
        assertEquals("two themes share a name", names.size, names.toSet().size)
    }

    @Test
    fun `an unknown id falls back rather than failing`() {
        assertEquals(TermThemes.MATRIX, TermThemes.byId("no such theme"))
    }

    @Test
    fun `every theme resolves by its own id`() {
        TermThemes.all.forEach { assertEquals(it, TermThemes.byId(it.id)) }
    }

    /**
     * Ink has to stand off its background, whichever way round the theme runs.
     *
     * The threshold is deliberately modest rather than a contrast-ratio standard: two of these
     * themes are built on a narrow tonal range on purpose — aged paper and flat steel are both
     * characterised by *not* being high contrast — and holding them to a bright-white
     * benchmark would mean designing that character out.
     */
    @Test
    fun `ink is legible against its background in every theme`() {
        TermThemes.all.forEach { theme ->
            val gap = kotlin.math.abs(luminance(theme.ink) - luminance(theme.background))
            assertTrue("${theme.name} puts ink at only $gap from its background", gap > 0.3f)
        }
    }

    /** And the graded inks have to actually grade, or three tiers are one tier. */
    @Test
    fun `ink, dim and faint step away from the ink in order`() {
        TermThemes.all.forEach { theme ->
            val toward = { c: Color -> kotlin.math.abs(luminance(c) - luminance(theme.background)) }
            assertTrue(
                "${theme.name} does not fade ink to dim to faint",
                toward(theme.ink) > toward(theme.inkDim) && toward(theme.inkDim) > toward(theme.inkFaint),
            )
        }
    }

    /** A light theme's page must be light, and a dark one's dark — the flag drives real logic. */
    @Test
    fun `the light flag agrees with the background`() {
        TermThemes.all.forEach { theme ->
            val lum = luminance(theme.background)
            if (theme.light) {
                assertTrue("${theme.name} claims light with a background at $lum", lum > 0.5f)
            } else {
                assertTrue("${theme.name} claims dark with a background at $lum", lum < 0.5f)
            }
        }
    }
}
