package org.phioster.glyphsmith.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.ascii.AsciiParams

class AnimationTest {

    private fun track(target: AnimTarget, curve: AnimCurve, from: Int, to: Int, cycles: Int = 1) =
        AnimTrack(target = target, enabled = true, curve = curve, from = from, to = to, cycles = cycles)

    @Test
    fun `sine starts low, peaks in the middle and returns`() {
        assertEquals(0f, Animator.sample(AnimCurve.SINE, 0f, 0, 1), 1e-4f)
        assertEquals(1f, Animator.sample(AnimCurve.SINE, 0.5f, 0, 1), 1e-4f)
        assertEquals(0f, Animator.sample(AnimCurve.SINE, 1f, 0, 1), 1e-4f)
    }

    @Test
    fun `curves stay inside the unit interval`() {
        AnimCurve.entries.forEach { curve ->
            for (step in 0..40) {
                val value = Animator.sample(curve, step / 20f, step, 3)
                assertTrue("$curve left 0..1 at $step: $value", value in 0f..1f)
            }
        }
    }

    /** A loop that doesn't land back where it started shows a visible jump when it repeats. */
    @Test
    fun `whole cycles bring every curve back to its starting value`() {
        listOf(AnimCurve.SINE, AnimCurve.TRIANGLE, AnimCurve.SAWTOOTH).forEach { curve ->
            val t = track(AnimTarget.GLOW_DIRECTION, curve, 0, 359, cycles = 2)
            assertEquals(
                "$curve does not close its loop",
                Animator.valueAt(t, 0, 24),
                Animator.valueAt(t, 24, 24),
            )
        }
    }

    @Test
    fun `random is stable for a given frame`() {
        val t = track(AnimTarget.GLITCH_SEED, AnimCurve.RANDOM, 1, 9999)
        assertEquals(Animator.valueAt(t, 7, 24), Animator.valueAt(t, 7, 24))
        assertNotEquals(Animator.valueAt(t, 7, 24), Animator.valueAt(t, 8, 24))
    }

    @Test
    fun `values stay within the track's ends, whichever way round they are`() {
        val ascending = track(AnimTarget.DITHER_STRENGTH, AnimCurve.SINE, 20, 80)
        val descending = track(AnimTarget.DITHER_STRENGTH, AnimCurve.SINE, 80, 20)
        for (frame in 0..24) {
            assertTrue(Animator.valueAt(ascending, frame, 24) in 20..80)
            assertTrue(Animator.valueAt(descending, frame, 24) in 20..80)
        }
    }

    @Test
    fun `phase shifts a track against an identical one`() {
        val plain = track(AnimTarget.DEPTH, AnimCurve.SINE, 1, 64)
        val shifted = plain.copy(phase = 50)
        assertNotEquals(Animator.valueAt(plain, 0, 24), Animator.valueAt(shifted, 0, 24))
    }

    @Test
    fun `a disabled animation leaves the parameters alone`() {
        val base = AsciiParams(depth = 12)
        val animation = AnimationParams(
            enabled = false,
            tracks = listOf(track(AnimTarget.DEPTH, AnimCurve.SAWTOOTH, 1, 64)),
        )
        assertEquals(base, Animator.paramsAt(base, animation, 5))
    }

    @Test
    fun `enabled tracks drive their parameter, including nested effect ones`() {
        val base = AsciiParams(depth = 12)
        val animation = AnimationParams(
            enabled = true,
            frames = 24,
            tracks = listOf(
                track(AnimTarget.DEPTH, AnimCurve.SINE, 4, 40),
                track(AnimTarget.GLOW_DIRECTION, AnimCurve.SAWTOOTH, 0, 359),
                track(AnimTarget.GLITCH_SEED, AnimCurve.RANDOM, 1, 9999),
            ),
        )
        val mid = Animator.paramsAt(base, animation, 12)

        assertEquals(40, mid.depth) // sine peaks halfway through the loop
        assertTrue(mid.effects.glow.direction > 0)
        assertNotEquals(base.effects.jpegGlitch.seed, mid.effects.jpegGlitch.seed)
        // Untouched parameters must survive untouched.
        assertEquals(base.cellSize, mid.cellSize)
        assertEquals(base.charSetId, mid.charSetId)
    }

    @Test
    fun `track lookup falls back to a sensible default`() {
        val animation = AnimationParams(tracks = emptyList())
        val fallback = animation.track(AnimTarget.DEPTH)
        assertEquals(AnimTarget.DEPTH, fallback.target)
        assertTrue(!fallback.enabled)
    }

    @Test
    fun `duration follows frames and fps`() {
        assertEquals(2f, AnimationParams(frames = 24, fps = 12).durationSeconds, 1e-4f)
    }
}
