package org.phioster.glyphsmith.core.dither

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The third mechanism, and the rule the render loop depends on.
 *
 * A style used to be decided *either* by a threshold *or* by a diffused error. These two are
 * both, and the danger in adding them is not that they look wrong — it is that a predicate
 * widened to include them quietly changes what eighty other styles do.
 */
class ModulatedDiffusionTest {

    private val combined = listOf(DitherMode.WAVE_DIFFUSE, DitherMode.ORB_DIFFUSE)

    private val options = PatternOptions(
        scale = 100,
        period = 9,
        angle = 0,
        phase = 0f,
        centerX = 32f,
        centerY = 32f,
        density = 40,
        orb = OrbOptions(),
    )

    /**
     * The invariant `QuantisePass` is written against.
     *
     * Its `when` takes the threshold branch first, so a style that answered yes to both would
     * silently stop diffusing — it would still render, and it would render the wrong thing. The
     * new kind is deliberately *not* threshold-based for exactly this reason.
     */
    @Test
    fun `no style is both decided by a threshold and diffusing`() {
        DitherMode.entries.forEach { mode ->
            assertTrue(
                "${mode.name} is threshold-based and carries a kernel",
                !(Dither.isThresholdBased(mode) && Dither.diffusionKernel(mode).isNotEmpty()),
            )
        }
    }

    @Test
    fun `a modulated diffusion is not threshold-based, so it keeps diffusing`() {
        combined.forEach { mode ->
            assertTrue("${mode.name} took the threshold branch", !Dither.isThresholdBased(mode))
            assertTrue("${mode.name} lost its kernel", Dither.diffusionKernel(mode).isNotEmpty())
        }
    }

    /**
     * The four diffusion accessors used to test `as? ErrorDiffusion`, which a composed style is
     * not. Missing one is not a compile error and not a visible one either: the style quantises
     * correctly and throws its error away.
     */
    @Test
    fun `every diffusion accessor sees through the composition`() {
        combined.forEach { mode ->
            assertEquals(
                "${mode.name} has the wrong kernel",
                Dither.diffusionKernel(DitherMode.DIFFUSE_Y),
                Dither.diffusionKernel(mode),
            )
            assertEquals(
                "${mode.name} has the wrong buffer depth",
                Dither.kernelDepth(DitherMode.DIFFUSE_Y),
                Dither.kernelDepth(mode),
            )
            assertEquals(
                "${mode.name} disagrees about variable weights",
                Dither.hasVariableKernel(DitherMode.DIFFUSE_Y),
                Dither.hasVariableKernel(mode),
            )
        }
    }

    /** And the surface half: the threshold has to actually move with position. */
    @Test
    fun `the surface reaches the threshold`() {
        combined.forEach { mode ->
            val here = Dither.threshold(mode, 0, 0, 0.5f, options)
            val elsewhere = (1..40).map { Dither.threshold(mode, it, it * 2, 0.5f, options) }

            assertTrue("${mode.name} has a flat threshold", elsewhere.any { it != here })
            assertTrue("${mode.name} left 0..1", (elsewhere + here).all { it in 0f..1f })
        }
    }

    /** It uses the surface its declaration names, not some other one. */
    @Test
    fun `each combination thresholds like the surface it was built from`() {
        val positions = (0..30).map { it to it * 3 }

        assertEquals(
            positions.map { (x, y) -> Dither.threshold(DitherMode.MOD_WAVE, x, y, 0.5f, options) },
            positions.map { (x, y) -> Dither.threshold(DitherMode.WAVE_DIFFUSE, x, y, 0.5f, options) },
        )
        assertEquals(
            positions.map { (x, y) -> Dither.threshold(DitherMode.MOD_ORB, x, y, 0.5f, options) },
            positions.map { (x, y) -> Dither.threshold(DitherMode.ORB_DIFFUSE, x, y, 0.5f, options) },
        )
    }

    /**
     * The pattern controls have to follow it.
     *
     * `isThresholdBased` used to be what the mapping panel asked, and the answer for these is
     * no — so the panel now asks `usesPattern`, and a style with a surface but no pattern
     * sliders would be a style nobody can steer.
     */
    @Test
    fun `the pattern controls apply to it`() {
        combined.forEach { mode ->
            assertTrue("${mode.name} has no pattern controls", Dither.usesPattern(mode))
            assertTrue("${mode.name} is not seen as a modulation", Dither.isModulation(mode))
            assertTrue("${mode.name} is not recognised", Dither.isModulatedDiffusion(mode))
        }
    }

    /** `usesPattern` must not have widened to cover a plain diffusion. */
    @Test
    fun `a plain diffusion still has no pattern controls`() {
        assertTrue(!Dither.usesPattern(DitherMode.FLOYD_STEINBERG))
        assertTrue(!Dither.usesPattern(DitherMode.DIFFUSE_Y))
        assertTrue(!Dither.isModulation(DitherMode.DIFFUSE_Y))
    }

    /** And the labels come from the surface, since that is what the sliders steer. */
    @Test
    fun `the labels are the surface's own`() {
        assertEquals(Dither.periodLabel(DitherMode.MOD_WAVE), Dither.periodLabel(DitherMode.WAVE_DIFFUSE))
        assertEquals(Dither.densityLabel(DitherMode.MOD_ORB), Dither.densityLabel(DitherMode.ORB_DIFFUSE))
    }

    @Test
    fun `the two combinations are distinct styles`() {
        assertNotEquals(
            Dither.threshold(DitherMode.WAVE_DIFFUSE, 7, 11, 0.5f, options),
            Dither.threshold(DitherMode.ORB_DIFFUSE, 7, 11, 0.5f, options),
        )
    }
}
