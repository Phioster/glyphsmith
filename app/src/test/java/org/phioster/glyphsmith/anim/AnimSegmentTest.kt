package org.phioster.glyphsmith.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.ascii.RenderSettings

class AnimSegmentTest {

    private val base = RenderSettings(depth = 12)

    private fun animation(vararg segments: AnimSegment) = AnimationParams(
        enabled = true,
        frames = 20,
        segments = segments.toList(),
        // Tracks off, so these tests measure only what the segments do.
        tracks = AnimTarget.entries.map { AnimTrack(it, enabled = false) },
    )

    /** Segments arrived in an app full of saved animations; an empty list must change nothing. */
    @Test
    fun `no segments behaves exactly as before`() {
        val without = AnimationParams(enabled = true, frames = 20)
        for (frame in 0 until 20) {
            assertEquals(
                Animator.paramsAt(base, without, frame),
                Animator.paramsAt(base, without.copy(segments = emptyList()), frame),
            )
        }
    }

    @Test
    fun `a segment drives its property only inside its range`() {
        val anim = animation(
            AnimSegment(AnimTarget.DEPTH, from = 4, to = 40, start = 25, end = 75, curve = AnimCurve.SAWTOOTH),
        )
        // Before and after the range the base value stands.
        assertEquals(12, Animator.paramsAt(base, anim, 0).depth)
        assertEquals(12, Animator.paramsAt(base, anim, 19).depth)
        // Inside it, the segment decides.
        assertEquals(4, Animator.paramsAt(base, anim, 5).depth)
        assertEquals(40, Animator.paramsAt(base, anim, 15).depth)
    }

    /**
     * The reason segments exist at all: a rise, a hold and a fall on one property. The middle
     * stretch must be genuinely constant, not merely slow.
     */
    @Test
    fun `rise, hold and fall gives a constant middle`() {
        val anim = animation(
            AnimSegment(AnimTarget.DITHER_STRENGTH, 0, 100, 0, 30, AnimCurve.SAWTOOTH),
            AnimSegment(AnimTarget.DITHER_STRENGTH, 100, 100, 30, 70, AnimCurve.SAWTOOTH),
            AnimSegment(AnimTarget.DITHER_STRENGTH, 100, 0, 70, 100, AnimCurve.SAWTOOTH),
        )
        val values = (0 until 20).map { Animator.paramsAt(base, anim, it).ditherStrength }

        val middle = values.subList(7, 14)
        assertEquals("the hold is not constant: $middle", 1, middle.toSet().size)
        assertEquals(100, middle.first())
        assertTrue("it never rises", values.first() < 50)
        assertTrue("it never falls", values.last() < 50)
    }

    @Test
    fun `overlapping segments on one property are refused`() {
        val first = AnimSegment(AnimTarget.DEPTH, start = 0, end = 50)
        val anim = animation(first)

        assertFalse(anim.canPlace(AnimSegment(AnimTarget.DEPTH, start = 25, end = 75)))
        assertFalse(anim.canPlace(AnimSegment(AnimTarget.DEPTH, start = 10, end = 20)))
        // A different property may sit anywhere.
        assertTrue(anim.canPlace(AnimSegment(AnimTarget.EDGE_THRESHOLD, start = 25, end = 75)))
    }

    /** Butting one segment against the next is how a sequence is built, so it must be allowed. */
    @Test
    fun `segments may touch end to end`() {
        val anim = animation(AnimSegment(AnimTarget.DEPTH, start = 0, end = 50))
        assertTrue(anim.canPlace(AnimSegment(AnimTarget.DEPTH, start = 50, end = 100)))
    }

    @Test
    fun `editing a segment ignores its own current position`() {
        val anim = animation(
            AnimSegment(AnimTarget.DEPTH, start = 0, end = 50),
            AnimSegment(AnimTarget.DEPTH, start = 60, end = 100),
        )
        // Widening the first towards the second is fine until they actually intersect.
        assertTrue(anim.canPlace(AnimSegment(AnimTarget.DEPTH, start = 0, end = 60), ignoring = 0))
        assertFalse(anim.canPlace(AnimSegment(AnimTarget.DEPTH, start = 0, end = 80), ignoring = 0))
    }

    @Test
    fun `a reversed range is read the way it was meant`() {
        val forward = AnimSegment(AnimTarget.DEPTH, 4, 40, start = 20, end = 80)
        val backward = AnimSegment(AnimTarget.DEPTH, 4, 40, start = 80, end = 20)
        assertEquals(forward.span, backward.span)
    }

    @Test
    fun `a value outside the range is not reported`() {
        val segment = AnimSegment(AnimTarget.DEPTH, 4, 40, start = 40, end = 60)
        assertNull(Animator.valueIn(segment, 0.1f))
        assertNull(Animator.valueIn(segment, 0.9f))
        assertEquals(4, Animator.valueIn(segment, 0.4f))
        assertEquals(40, Animator.valueIn(segment, 0.6f))
    }

    @Test
    fun `a zero width segment reports its end value instead of dividing by nothing`() {
        val segment = AnimSegment(AnimTarget.DEPTH, 4, 40, start = 50, end = 50)
        assertEquals(40, Animator.valueIn(segment, 0.5f))
    }

    @Test
    fun `a segment stays inside its own two ends`() {
        AnimCurve.entries.forEach { curve ->
            val segment = AnimSegment(AnimTarget.DITHER_STRENGTH, 30, 70, 0, 100, curve)
            for (i in 0..100) {
                val v = Animator.valueIn(segment, i / 100f)
                assertTrue("$curve left its range with $v", v != null && v in 30..70)
            }
        }
    }

    /** Segments are an explicit statement about a stretch of time; a track is a standing rule. */
    @Test
    fun `a segment overrides a track on the same property`() {
        val anim = AnimationParams(
            enabled = true,
            frames = 20,
            tracks = AnimTarget.entries.map {
                if (it == AnimTarget.DEPTH) {
                    AnimTrack(it, enabled = true, from = 60, to = 60)
                } else {
                    AnimTrack(it, enabled = false)
                }
            },
            segments = listOf(AnimSegment(AnimTarget.DEPTH, 5, 5, 0, 100, AnimCurve.SAWTOOTH)),
        )
        assertEquals(5, Animator.paramsAt(base, anim, 10).depth)
    }
}
