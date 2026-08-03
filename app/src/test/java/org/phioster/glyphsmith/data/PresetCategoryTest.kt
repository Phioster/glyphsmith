package org.phioster.glyphsmith.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.anim.TemporalParams
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.render.Layer
import org.junit.Test
import org.phioster.glyphsmith.core.dither.DitherCategory
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.render.RenderMode

class PresetCategoryTest {

    /** A pixel dither with nothing else switched on — the plain case the rules read from. */
    private fun params(mode: DitherMode) =
        RenderSettings(renderMode = RenderMode.PurePixel, ditherMode = mode)

    // --- where a new save lands --------------------------------------------------------

    @Test
    fun `every algorithm resolves to a shipped category`() {
        DitherMode.entries.forEach { mode ->
            val family = PresetStore.familyOf(params(mode))
            assertTrue(
                "$mode resolves to $family, which the picker never shows",
                family in PresetStore.categories,
            )
        }
    }

    /**
     * Every dither family has to name a shelf, and it has to be a curated one.
     *
     * The retired categories are still legal *stored* values — someone's library carries them
     * — but nothing new may be filed onto a shelf the shipped library no longer stocks.
     */
    @Test
    fun `no new save is filed onto a retired shelf`() {
        val allowed = PresetStore.curatedCategories + PresetStore.CATEGORY_CUSTOM
        DitherMode.entries.forEach { mode ->
            val family = PresetStore.familyOf(params(mode))
            assertTrue(
                "$mode is filed under $family, which nothing ships in any more",
                family in allowed,
            )
        }
    }

    @Test
    fun `a preset with no dithering is not filed under a dither family`() {
        assertEquals(PresetStore.CATEGORY_CUSTOM, PresetStore.familyOf(params(DitherMode.NONE)))
    }

    /**
     * The taxonomy, family by family. Each dither family names one shelf and every algorithm
     * in it lands there, which is what makes the shelf mean its own name.
     */
    @Test
    fun `each dither family files onto its own shelf`() {
        val expected = mapOf(
            DitherCategory.ERROR_DIFFUSION to PresetStore.CATEGORY_DIFFUSION,
            DitherCategory.ORDERED to PresetStore.CATEGORY_ORDERED,
            DitherCategory.PATTERNED to PresetStore.CATEGORY_PATTERN,
            DitherCategory.SPECIAL to PresetStore.CATEGORY_PATTERN,
            DitherCategory.POLYGON to PresetStore.CATEGORY_GEOMETRY,
            DitherCategory.GLITCH to PresetStore.CATEGORY_GLITCH,
            DitherCategory.BASIC to PresetStore.CATEGORY_CUSTOM,
        )

        assertEquals(
            "a dither family has no shelf",
            DitherCategory.entries.toSet(),
            expected.keys,
        )
        DitherMode.entries.forEach { mode ->
            assertEquals(
                "$mode is a ${mode.category} algorithm",
                expected.getValue(mode.category),
                PresetStore.familyOf(params(mode)),
            )
        }
    }

    /** Named cases, so a change to the mapping shows up as the algorithms people know. */
    @Test
    fun `the well-known algorithms land where they are looked for`() {
        assertEquals(
            PresetStore.CATEGORY_DIFFUSION,
            PresetStore.familyOf(params(DitherMode.FLOYD_STEINBERG)),
        )
        assertEquals(PresetStore.CATEGORY_ORDERED, PresetStore.familyOf(params(DitherMode.BAYER_4)))
        assertEquals(
            PresetStore.CATEGORY_ORDERED,
            PresetStore.familyOf(params(DitherMode.BLUE_NOISE_16)),
        )
        assertEquals(
            PresetStore.CATEGORY_PATTERN,
            PresetStore.familyOf(params(DitherMode.HEART_GRID)),
        )
        assertEquals(PresetStore.CATEGORY_PATTERN, PresetStore.familyOf(params(DitherMode.MOD_ORB)))
        assertEquals(
            PresetStore.CATEGORY_GEOMETRY,
            PresetStore.familyOf(params(DitherMode.HEXA_POLY)),
        )
        assertEquals(PresetStore.CATEGORY_GLITCH, PresetStore.familyOf(params(DitherMode.GLITCH)))
    }

    // --- what outranks the algorithm ---------------------------------------------------

    /** Whatever produced the levels, a render that ends in characters is glyph art. */
    @Test
    fun `a glyph render is filed as glyph art`() {
        RenderMode.entries.filter { it.isGlyph }.forEach { mode ->
            assertEquals(
                "$mode produces characters",
                PresetStore.CATEGORY_GLYPH,
                PresetStore.familyOf(
                    RenderSettings(renderMode = mode, ditherMode = DitherMode.FLOYD_STEINBERG),
                ),
            )
        }
    }

    @Test
    fun `a stack of layers is filed as layered`() {
        val stacked = params(DitherMode.FLOYD_STEINBERG).copy(
            layers = listOf(Layer(name = "over", params = RenderSettings(renderMode = RenderMode.PurePixel))),
        )

        assertEquals(PresetStore.CATEGORY_LAYERED, PresetStore.familyOf(stacked))
    }

    @Test
    fun `an animated preset is filed as motion`() {
        val animated = params(DitherMode.FLOYD_STEINBERG)
            .copy(animation = AnimationParams(enabled = true))

        assertEquals(PresetStore.CATEGORY_MOTION, PresetStore.familyOf(animated))
    }

    /** Temporal noise animates the dither itself, so it is motion just as much as a track is. */
    @Test
    fun `temporal variation is filed as motion`() {
        val temporal = params(DitherMode.FLOYD_STEINBERG)
            .copy(temporal = TemporalParams(enabled = true))

        assertEquals(PresetStore.CATEGORY_MOTION, PresetStore.familyOf(temporal))
    }

    /**
     * The order the questions are asked in, pinned by the case where every answer is yes.
     * Glyph art first, then structure, then the kernel.
     */
    @Test
    fun `the render mode outranks structure and structure outranks the algorithm`() {
        val everything = RenderSettings(
            renderMode = RenderMode.GlyphMatrix,
            ditherMode = DitherMode.BAYER_4,
            layers = listOf(Layer(name = "over", params = RenderSettings(renderMode = RenderMode.PurePixel))),
            animation = AnimationParams(enabled = true),
            temporal = TemporalParams(enabled = true),
        )

        assertEquals(PresetStore.CATEGORY_GLYPH, PresetStore.familyOf(everything))
        assertEquals(
            PresetStore.CATEGORY_LAYERED,
            PresetStore.familyOf(everything.copy(renderMode = RenderMode.PurePixel)),
        )
        assertEquals(
            PresetStore.CATEGORY_MOTION,
            PresetStore.familyOf(
                everything.copy(renderMode = RenderMode.PurePixel, layers = emptyList()),
            ),
        )
        assertEquals(
            PresetStore.CATEGORY_ORDERED,
            PresetStore.familyOf(
                everything.copy(
                    renderMode = RenderMode.PurePixel,
                    layers = emptyList(),
                    animation = AnimationParams(enabled = false),
                    temporal = TemporalParams(enabled = false),
                ),
            ),
        )
    }

    /** Nothing to go on is exactly the case CUSTOM exists for. */
    @Test
    fun `a preset with nothing distinctive falls back to custom`() {
        assertEquals(
            PresetStore.CATEGORY_CUSTOM,
            PresetStore.familyOf(RenderSettings(renderMode = RenderMode.PurePixel)),
        )
    }

    // --- what the categories are called ------------------------------------------------

    @Test
    fun `every category has a name worth showing`() {
        PresetStore.categories.forEach { category ->
            val label = PresetStore.label(category)
            assertTrue("$category has no name at all", label.isNotBlank())
            assertNotEquals("$category is shown as its own stored token", category, label)
            assertNotEquals(
                "$category is shown as its own stored token, lowercased",
                category.lowercase(),
                label,
            )
            assertNotEquals(
                "$category is drawn as an identifier rather than as words",
                label.uppercase(),
                label,
            )
        }
    }

    /** The names the product asks for, spelled out — a label is a decision, not a derivation. */
    @Test
    fun `the shelves are named the way the product names them`() {
        assertEquals("Classic Dither", PresetStore.label(PresetStore.CATEGORY_CLASSIC))
        assertEquals("Error Diffusion", PresetStore.label(PresetStore.CATEGORY_DIFFUSION))
        assertEquals("Ordered Dither", PresetStore.label(PresetStore.CATEGORY_ORDERED))
        assertEquals("Pattern", PresetStore.label(PresetStore.CATEGORY_PATTERN))
        assertEquals("Print", PresetStore.label(PresetStore.CATEGORY_PRINT))
        assertEquals("Geometry", PresetStore.label(PresetStore.CATEGORY_GEOMETRY))
        assertEquals("Color", PresetStore.label(PresetStore.CATEGORY_COLOR))
        assertEquals("Glitch", PresetStore.label(PresetStore.CATEGORY_GLITCH))
        assertEquals("Motion", PresetStore.label(PresetStore.CATEGORY_MOTION))
        assertEquals("Layered", PresetStore.label(PresetStore.CATEGORY_LAYERED))
        assertEquals("Glyph Art", PresetStore.label(PresetStore.CATEGORY_GLYPH))
        assertEquals("Custom", PresetStore.label(PresetStore.CATEGORY_CUSTOM))
    }

    /**
     * A category nobody declared still has to read as words. This is what someone's own save
     * from another version, or from a later one, arrives as.
     */
    @Test
    fun `an unknown category is tidied rather than shown raw`() {
        assertEquals("Hand Made", PresetStore.label("HAND_MADE"))
        assertEquals("Whatever", PresetStore.label("WHATEVER"))
        assertEquals("", PresetStore.label(""))
    }

    /** A label is presentation. Changing one must never move what is written to disk. */
    @Test
    fun `naming a category does not rename it`() {
        PresetStore.categories.forEach { category ->
            assertEquals(category, category.uppercase())
            assertTrue("$category is not a stable token", category.all { it.isLetter() || it == '_' })
        }
    }

    /** Quick playback has to be an approximation of the whole loop, not a shorter loop. */
    @Test
    fun `quick playback covers the loop with fewer frames`() {
        assertTrue(PlaybackQuality.QUICK.step > PlaybackQuality.RENDERED.step)
        assertTrue(PlaybackQuality.QUICK.maxSide < PlaybackQuality.RENDERED.maxSide)

        val frames = 60
        val quick = (frames + PlaybackQuality.QUICK.step - 1) / PlaybackQuality.QUICK.step
        val full = (frames + PlaybackQuality.RENDERED.step - 1) / PlaybackQuality.RENDERED.step

        assertTrue("quick renders no fewer frames", quick < full)
        // Each quick frame stands in for `step` of them, so the loop still lasts as long.
        assertEquals(frames, quick * PlaybackQuality.QUICK.step)
    }

    @Test
    fun `every playback quality is named and distinct`() {
        val labels = PlaybackQuality.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        labels.forEach { assertTrue(it.isNotBlank()) }
    }
}
