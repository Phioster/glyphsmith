package org.phioster.glyphsmith.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TemporalTest {

    private val patterns = TemporalPattern.entries

    @Test
    fun `there are nine patterns`() {
        assertEquals(9, patterns.size)
    }

    @Test
    fun `every pattern stays inside minus one to one`() {
        patterns.forEach { pattern ->
            for (step in 0 until 40) {
                val time = step / 40f
                for (y in -8..8) {
                    for (x in -8..8) {
                        val v = Temporal.sample(pattern, x / 3f, y / 3f, time, 7)
                        assertTrue("$pattern gave $v at ($x,$y,$time)", v in -1f..1f)
                    }
                }
            }
        }
    }

    /**
     * The one failure mode that shows up on every single playthrough of an exported GIF: a
     * loop that doesn't close. It has to be a property of the functions, not something the
     * caller remembers to arrange.
     */
    @Test
    fun `every pattern lands back where it started after one loop`() {
        patterns.forEach { pattern ->
            for (y in -6..6) {
                for (x in -6..6) {
                    assertEquals(
                        "$pattern does not close at ($x,$y)",
                        Temporal.sample(pattern, x / 2f, y / 2f, 0f, 3),
                        Temporal.sample(pattern, x / 2f, y / 2f, 1f, 3),
                        1e-5f,
                    )
                }
            }
        }
    }

    /** Whole speeds only, so a faster pattern still comes back at the end of the loop. */
    @Test
    fun `the loop still closes at every speed`() {
        TemporalParams.SPEED_RANGE.forEach { speed ->
            patterns.forEach { pattern ->
                val params = TemporalParams(
                    enabled = true,
                    pattern = pattern,
                    speed = speed,
                    scale = 4,
                )
                assertEquals(
                    "$pattern at ${speed}x",
                    Temporal.offset(params.copy(time = 0f), 5, 9),
                    Temporal.offset(params.copy(time = 1f), 5, 9),
                    1e-5f,
                )
            }
        }
    }

    @Test
    fun `every pattern actually moves over the loop`() {
        patterns.forEach { pattern ->
            val moved = (0 until 16).any { step ->
                val a = Temporal.sample(pattern, 1.3f, 2.7f, 0f, 5)
                val b = Temporal.sample(pattern, 1.3f, 2.7f, step / 16f, 5)
                abs(a - b) > 1e-4f
            }
            assertTrue("$pattern never changes over time", moved)
        }
    }

    @Test
    fun `a disabled or silent pattern contributes nothing`() {
        val live = TemporalParams(enabled = true, amount = 100, time = 0.3f)
        assertEquals(0f, Temporal.offset(live.copy(enabled = false), 3, 4), 0f)
        assertEquals(0f, Temporal.offset(live.copy(amount = 0), 3, 4), 0f)
    }

    @Test
    fun `amount scales the offset and bounds it`() {
        val params = TemporalParams(enabled = true, amount = 25, time = 0.4f, scale = 3)
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                assertTrue(abs(Temporal.offset(params, x, y)) <= 0.25f + 1e-5f)
            }
        }
    }

    @Test
    fun `the same cell and frame always gives the same value`() {
        val params = TemporalParams(enabled = true, pattern = TemporalPattern.WHITE_NOISE, time = 0.6f)
        val first = Temporal.offset(params, 11, 17)
        repeat(5) { assertEquals(first, Temporal.offset(params, 11, 17), 0f) }
    }

    /** The hash-driven patterns must respond to the seed; the analytic ones must not. */
    @Test
    fun `the seed rerolls the noise but leaves the analytic patterns alone`() {
        listOf(
            TemporalPattern.WHITE_NOISE,
            TemporalPattern.BLUE_NOISE,
            TemporalPattern.VALUE_NOISE,
        ).forEach { pattern ->
            val a = Temporal.sample(pattern, 2.4f, 3.1f, 0.25f, 1)
            val b = Temporal.sample(pattern, 2.4f, 3.1f, 0.25f, 2)
            assertTrue("seed had no effect on $pattern", abs(a - b) > 1e-6f)
        }

        listOf(TemporalPattern.RIPPLE, TemporalPattern.PLASMA, TemporalPattern.VORTEX)
            .forEach { pattern ->
                assertEquals(
                    "$pattern must ignore the seed",
                    Temporal.sample(pattern, 2.4f, 3.1f, 0.25f, 1),
                    Temporal.sample(pattern, 2.4f, 3.1f, 0.25f, 2),
                    0f,
                )
            }
    }

    @Test
    fun `the eased curves are one-way and the rest are seamless`() {
        val oneWay = AnimCurve.entries.filterNot { it.seamless }
        assertTrue(AnimCurve.EASE_IN in oneWay)
        assertTrue(AnimCurve.EASE_OUT in oneWay)
        assertTrue(AnimCurve.EASE_IN_OUT in oneWay)
        assertTrue(AnimCurve.SAWTOOTH in oneWay)
        assertTrue(AnimCurve.SINE.seamless)
        assertTrue(AnimCurve.TRIANGLE.seamless)
    }

    @Test
    fun `the eased curves run the full range in the right direction`() {
        listOf(AnimCurve.EASE_IN, AnimCurve.EASE_OUT, AnimCurve.EASE_IN_OUT).forEach { curve ->
            assertEquals("$curve start", 0f, Animator.sample(curve, 0f, 0, 1), 1e-5f)
            // Just short of the end, since 1.0 wraps back to the start of the next cycle.
            assertTrue("$curve end", Animator.sample(curve, 0.999f, 0, 1) > 0.99f)
        }
        // Ease-in is slow first, ease-out is slow last — they must straddle the midpoint.
        assertTrue(Animator.sample(AnimCurve.EASE_IN, 0.5f, 0, 1) < 0.5f)
        assertTrue(Animator.sample(AnimCurve.EASE_OUT, 0.5f, 0, 1) > 0.5f)
        assertEquals(0.5f, Animator.sample(AnimCurve.EASE_IN_OUT, 0.5f, 0, 1), 1e-5f)
    }
}
