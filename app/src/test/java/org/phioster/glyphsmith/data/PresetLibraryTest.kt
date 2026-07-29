package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimTarget
import org.phioster.glyphsmith.ascii.CharacterSets
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.ascii.DitherMode
import org.phioster.glyphsmith.ascii.Palettes

class PresetLibraryTest {

    private val presets = PresetStore.builtIns

    @Test
    fun `no two presets share a name`() {
        val duplicates = presets.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertTrue("these names appear twice: $duplicates", duplicates.isEmpty())
    }

    /** A preset filed under a category the picker does not draw is a preset nobody can find. */
    @Test
    fun `every preset lands in a category the picker knows`() {
        presets.forEach { preset ->
            assertTrue(
                "${preset.name} is filed under ${preset.category}",
                preset.category in PresetStore.categories,
            )
        }
    }

    /** Referencing a palette or character set that does not exist fails silently at runtime. */
    @Test
    fun `every preset points at something that exists`() {
        presets.forEach { preset ->
            assertTrue(
                "${preset.name} wants the palette '${preset.params.paletteId}'",
                Palettes.all.any { it.id == preset.params.paletteId },
            )
            assertTrue(
                "${preset.name} wants the set '${preset.params.charSetId}'",
                CharacterSets.all.any { it.id == preset.params.charSetId },
            )
        }
    }

    /**
     * Every shelf has to hold something. An empty category draws a header over nothing, and
     * the two added for this round would be exactly that if a rename went wrong.
     */
    @Test
    fun `every category has at least one preset`() {
        PresetStore.categories.filter { it != PresetStore.CATEGORY_CUSTOM }.forEach { category ->
            assertTrue("$category is empty", presets.any { it.category == category })
        }
    }

    // --- motion --------------------------------------------------------------------

    private val motion = presets.filter { it.category == PresetStore.CATEGORY_MOTION }

    /** The category's whole promise is that applying one and pressing play is enough. */
    @Test
    fun `every motion preset arrives with its animation switched on`() {
        assertTrue("there are no motion presets", motion.isNotEmpty())
        motion.forEach { preset ->
            assertTrue("${preset.name} is not animated", preset.params.animation.enabled)
            assertTrue(
                "${preset.name} has no track aimed at anything",
                preset.params.animation.tracks.any { it.enabled },
            )
        }
    }

    @Test
    fun `motion presets run long enough to read as motion`() {
        motion.forEach { preset ->
            assertTrue("${preset.name} has ${preset.params.animation.frames} frames", preset.params.animation.frames >= 24)
        }
    }

    /**
     * A track that ends somewhere other than where it began leaves a visible jump at the loop
     * seam. A non-closing curve is therefore allowed only on a target whose range joins up —
     * an angle, or a modulation phase — and only when it sweeps the whole of it.
     */
    @Test
    fun `every animated track either closes or wraps`() {
        motion.forEach { preset ->
            preset.params.animation.tracks.filter { it.enabled }.forEach { track ->
                if (track.curve.seamless) return@forEach
                assertTrue(
                    "${preset.name} runs a non-closing curve on ${track.target}, which does not wrap",
                    track.target.cyclic,
                )
                assertEquals(
                    "${preset.name} does not sweep the whole of ${track.target}",
                    track.target.min,
                    minOf(track.from, track.to),
                )
                assertEquals(
                    "${preset.name} does not sweep the whole of ${track.target}",
                    track.target.max,
                    maxOf(track.from, track.to),
                )
            }
        }
    }

    /** The point of this round: the new styles have to actually be reachable from a preset. */
    @Test
    fun `the styles added in this round are represented`() {
        val used = presets.map { it.params.ditherMode }.toSet()
        listOf(
            DitherMode.CROSSHATCH, DitherMode.STIPPLING, DitherMode.TOPOGRAPHY,
            DitherMode.LOW_POLY, DitherMode.CAMO, DitherMode.HEXA_POLY,
            DitherMode.OSTROMOUKHOV, DitherMode.SHIAU_FAN, DitherMode.DOT_DIFFUSION,
            DitherMode.PRINT_PATTERN, DitherMode.VORTEX, DitherMode.GLITCH,
            DitherMode.VORTEX_DIFFUSION, DitherMode.CONTRAST_AWARE_Y,
        ).forEach { mode ->
            assertTrue("nothing ships using ${mode.name}", mode in used)
        }
    }

    /** A palette preset with no palette colour mode is a setting that does nothing. */
    @Test
    fun `presets naming a palette actually use one`() {
        presets.filter { it.params.colorMode == ColorMode.PALETTE }.forEach { preset ->
            assertTrue(
                "${preset.name} is in palette mode with under two stops",
                (Palettes.all.first { it.id == preset.params.paletteId }).colors.size >= 2,
            )
        }
    }
}
