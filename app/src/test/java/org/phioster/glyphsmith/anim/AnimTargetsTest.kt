package org.phioster.glyphsmith.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.render.RenderSettings

/**
 * The animation targets, as a registry rather than as a `when`.
 *
 * What each target writes used to be a branch in `Animator.apply`, eleven of them, a long way
 * from the parameter each one names — the same shape `state/RandomLook` had for effects. These
 * are the properties that have to hold now that the branch travels with the target instead.
 */
class AnimTargetsTest {

    private val base = RenderSettings()

    @Test
    fun `every target is registered exactly once, in the order the panel shows`() {
        assertEquals(AnimTarget.entries.toList(), AnimTargets.all.map { it.target })
    }

    /**
     * The salt of the RANDOM curve, frozen at the values the enum ordinals used to produce.
     *
     * This is not a tidiness test. `Animator.sample` hashes the frame against this number, so a
     * salt that moves is a *saved animation that renders differently* — and the only symptom is
     * an exported GIF nobody thinks to compare against last week's. The ordinal was doing this
     * job implicitly; writing it down is what lets targets stop being an enum without every
     * random track quietly changing.
     */
    @Test
    fun `the historic targets keep the salt their ordinal gave them`() {
        val expected = mapOf(
            AnimTarget.DEPTH to 1,
            AnimTarget.CHARACTER_OFFSET to 2,
            AnimTarget.DITHER_STRENGTH to 3,
            AnimTarget.MOD_PHASE to 4,
            AnimTarget.PATTERN_DENSITY to 5,
            AnimTarget.EDGE_THRESHOLD to 6,
            AnimTarget.GLITCH_SEED to 7,
            AnimTarget.CHROMATIC_OFFSET to 8,
            AnimTarget.GLOW_DIRECTION to 9,
            AnimTarget.STARS_ANGLE to 10,
            AnimTarget.MODULATION_PHASE to 11,
            AnimTarget.INTERLACE_SHIFT to 12,
            AnimTarget.SORT_BAND to 13,
            AnimTarget.SLICE_OFFSET to 14,
            AnimTarget.WARP_CURVATURE to 15,
            AnimTarget.HALFTONE_ANGLE to 16,
            AnimTarget.SOURCE_BRIGHTNESS to 17,
        )

        expected.forEach { (target, salt) ->
            assertEquals("${target.name} changed salt", salt, AnimTargets.of(target).salt)
        }
        assertEquals("a target was added without a salt", expected.size, AnimTargets.all.size)
    }

    /**
     * The five effects that shipped after the animation system did, and could not be animated.
     *
     * Each names a parameter that *moves* rather than merely changes — a shift, a band edge, an
     * offset, a curvature, a screen angle. A parameter whose animation reads as flicker rather
     * than motion is not worth a track.
     */
    @Test
    fun `the effects that shipped after the animation system can now be driven`() {
        val ids = AnimTargets.all.map { it.target.wireId }

        listOf(
            "anim.interlace-shift",
            "anim.pixel-sort-band",
            "anim.slice-offset",
            "anim.warp-curvature",
            "anim.halftone-angle",
        ).forEach { assertTrue("$it is not registered", it in ids) }
    }

    /** Each of the five reaches its own effect's parameter, and only that one. */
    @Test
    fun `each new target writes the parameter it names`() {
        assertEquals(70, AnimTargets.of(AnimTarget.INTERLACE_SHIFT).write(base, 70).effects.interlace.shift)
        assertEquals(70, AnimTargets.of(AnimTarget.SORT_BAND).write(base, 70).effects.pixelSort.thresholdHigh)
        assertEquals(70, AnimTargets.of(AnimTarget.SLICE_OFFSET).write(base, 70).effects.sliceShift.maxOffset)
        assertEquals(70, AnimTargets.of(AnimTarget.WARP_CURVATURE).write(base, 70).effects.crtWarp.warpCurvature)
        assertEquals(70, AnimTargets.of(AnimTarget.HALFTONE_ANGLE).write(base, 70).effects.cmyk.angle)
    }

    /**
     * A new track is off until somebody switches it on.
     *
     * This is what makes adding a target safe for the shipped library: every preset carries an
     * entry for every target, so five new ones appear in eighty-nine presets at once. Enabled by
     * default, that would have been five effects switching themselves on across the whole
     * library.
     */
    @Test
    fun `a target added to the enum arrives disabled in a default animation`() {
        val animation = AnimationParams()

        assertEquals(AnimTargets.all.size, animation.tracks.size)
        assertTrue("a default track is enabled", animation.tracks.none { it.enabled })
        assertEquals(0, animation.activeCount)
    }

    @Test
    fun `no two targets share a salt`() {
        val salts = AnimTargets.all.map { it.salt }

        assertEquals("two targets hash alike", salts.size, salts.distinct().size)
    }

    /** A target that writes nothing is a track the user can move with no effect at all. */
    @Test
    fun `every target moves something`() {
        AnimTargets.all.forEach { provider ->
            val atMin = provider.write(base, provider.target.min)
            val atMax = provider.write(base, provider.target.max)

            assertTrue(
                "${provider.target.name} writes nothing at either end of its range",
                atMin != base || atMax != base,
            )
        }
    }

    /**
     * Eleven targets, eleven fields.
     *
     * The value is the same for all of them and inside every range, so two targets writing the
     * *same* field land on the same settings object and are caught here. That is the mistake
     * moving eleven branches out of one `when` invites: a copied line that still names the
     * field it was copied from.
     */
    @Test
    fun `no two targets write the same field`() {
        val written = AnimTargets.all.associateWith { it.write(base, 30) }

        val shared = written.entries
            .groupBy { it.value }
            .filterValues { it.size > 1 }
            .values
            .map { group -> group.map { it.key.target.name } }

        assertTrue("these targets write the same field: $shared", shared.isEmpty())
    }

    /**
     * The first target that drives something the dither *reads* rather than something it draws.
     *
     * Everything else moves a setting the renderer or an effect consumes. This one moves the
     * source's own brightness, before the algorithm sees it — which is what makes a transition
     * possible where the picture dissolves into pure dither and re-forms, instead of simply
     * fading to black.
     *
     * The mapping is stated here because a target carries `Int` bounds and the field is a
     * `Float` on -1..1: hundredths, the same percentage convention the rest of the app uses.
     */
    @Test
    fun `source brightness maps hundredths onto the field's own range`() {
        val target = AnimTargets.of(AnimTarget.SOURCE_BRIGHTNESS)

        assertEquals(-1f, target.write(base, -100).brightness, 1e-6f)
        assertEquals(0f, target.write(base, 0).brightness, 1e-6f)
        assertEquals(1f, target.write(base, 100).brightness, 1e-6f)
        assertEquals(0.35f, target.write(base, 35).brightness, 1e-6f)
    }

    /** Past the ends it holds at the ends: the field has no meaning outside -1..1. */
    @Test
    fun `source brightness clamps at the ends of its field`() {
        val target = AnimTargets.of(AnimTarget.SOURCE_BRIGHTNESS)

        assertEquals(-1f, target.write(base, -400).brightness, 1e-6f)
        assertEquals(1f, target.write(base, 400).brightness, 1e-6f)
    }

    /** The clamps come across with the branches; two targets deliberately have none. */
    @Test
    fun `a target clamps to its own range where it said it did`() {
        assertEquals(64, AnimTargets.of(AnimTarget.DEPTH).write(base, 9999).depth)
        assertEquals(1, AnimTargets.of(AnimTarget.DEPTH).write(base, -5).depth)
        assertEquals(100, AnimTargets.of(AnimTarget.DITHER_STRENGTH).write(base, 400).ditherStrength)
        assertEquals(
            50,
            AnimTargets.of(AnimTarget.CHROMATIC_OFFSET).write(base, 400).effects.chromatic.maxDisplace,
        )
    }

    /**
     * The two that are unclamped on purpose, and were before this moved.
     *
     * The character offset wraps against the ramp length and the pattern phase wraps inside the
     * pattern, so a track running past the end of its range keeps travelling instead of stalling
     * there. Clamping either would be a visible behaviour change disguised as tidying.
     */
    @Test
    fun `the two deliberately unclamped targets are still unclamped`() {
        assertEquals(400, AnimTargets.of(AnimTarget.CHARACTER_OFFSET).write(base, 400).offset)
        assertEquals(400, AnimTargets.of(AnimTarget.MOD_PHASE).write(base, 400).modPhase)
    }

    /** A target that names an effect parameter reaches it, and leaves the rest of the stack. */
    @Test
    fun `an effect target writes into the effect stack and nothing else`() {
        val moved = AnimTargets.of(AnimTarget.GLOW_DIRECTION).write(base, 200)

        assertEquals(200, moved.effects.glow.direction)
        assertEquals(base.effects.copy(glow = moved.effects.glow), moved.effects)
        assertNotEquals(base.effects.glow, moved.effects.glow)
        assertEquals(base.copy(effects = moved.effects), moved)
    }
}
