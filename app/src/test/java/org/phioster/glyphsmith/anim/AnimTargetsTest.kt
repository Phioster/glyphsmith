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
        )

        expected.forEach { (target, salt) ->
            assertEquals("${target.name} changed salt", salt, AnimTargets.of(target).salt)
        }
        assertEquals("a target was added without a salt", expected.size, AnimTargets.all.size)
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
