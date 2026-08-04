package org.phioster.glyphsmith.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.data.PlaybackQuality

/**
 * The countable half of playing an animation back.
 *
 * None of it was tested, because all of it sat inside a ViewModel behind a coroutine and a
 * bitmap allocation. It is also the half that goes wrong quietly: a budget that lets sixty
 * frames through at full size, a stride that makes the preview run at double speed, a ceiling
 * division that drops the last frame.
 */
class PlaybackPlanTest {

    private fun animation(frames: Int, fps: Int = 12) =
        AnimationParams(enabled = true, frames = frames, fps = fps)

    // --- which frames -------------------------------------------------------------------

    @Test
    fun `a full run renders every frame in order`() {
        val plan = PlaybackPlan.of(animation(frames = 24), PlaybackQuality.RENDERED)

        assertEquals((0 until 24).toList(), plan.positions)
        assertTrue(!plan.approximate)
    }

    @Test
    fun `a quick run renders every second frame`() {
        val plan = PlaybackPlan.of(animation(frames = 24), PlaybackQuality.QUICK)

        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22), plan.positions)
        assertTrue(plan.approximate)
    }

    /**
     * Ceiling division. An odd frame count with a stride of two must not lose its last frame,
     * which is the one an animation is most often judged on.
     */
    @Test
    fun `an odd frame count keeps its last frame`() {
        val plan = PlaybackPlan.of(animation(frames = 25), PlaybackQuality.QUICK)

        assertEquals(13, plan.renderedCount)
        assertEquals(24, plan.positions.last())
    }

    @Test
    fun `every position is a frame the animation actually has`() {
        listOf(2, 7, 24, 25, 120).forEach { frames ->
            listOf(PlaybackQuality.QUICK, PlaybackQuality.RENDERED).forEach { quality ->
                val plan = PlaybackPlan.of(animation(frames), quality)
                assertTrue("$frames/$quality left nothing to render", plan.positions.isNotEmpty())
                assertTrue(
                    "$frames/$quality points past the end: ${plan.positions.last()}",
                    plan.positions.all { it in 0 until frames },
                )
            }
        }
    }

    // --- how long each is held ----------------------------------------------------------

    /**
     * The rule the quick preview stands on: each rendered frame stands in for `step` of them,
     * so it is held that much longer. Without it the preview runs at several times real speed
     * and the animation gets the blame.
     */
    @Test
    fun `a quick run holds each frame long enough to last the whole loop`() {
        val animation = animation(frames = 24, fps = 12)

        val full = PlaybackPlan.of(animation, PlaybackQuality.RENDERED)
        val quick = PlaybackPlan.of(animation, PlaybackQuality.QUICK)

        assertEquals(
            "the quick preview does not last as long as the loop it approximates",
            full.renderedCount * full.frameDelayMs,
            quick.renderedCount * quick.frameDelayMs,
        )
        assertTrue("a skipped frame is not held longer", quick.frameDelayMs > full.frameDelayMs)
    }

    @Test
    fun `the frame delay follows the frame rate`() {
        assertEquals(100L, PlaybackPlan.of(animation(24, fps = 10), PlaybackQuality.RENDERED).frameDelayMs)
        assertEquals(40L, PlaybackPlan.of(animation(24, fps = 25), PlaybackQuality.RENDERED).frameDelayMs)
    }

    /** A rate of zero would divide by it. The params allow one to be typed in. */
    @Test
    fun `a frame rate of zero does not divide by it`() {
        val plan = PlaybackPlan.of(animation(frames = 24, fps = 0), PlaybackQuality.RENDERED)

        assertEquals(1000L, plan.frameDelayMs)
    }

    // --- how large ----------------------------------------------------------------------

    /**
     * Every frame of a run is held at once, so the size has to fall as the count rises. This is
     * the arithmetic between a long animation playing and the process being killed.
     */
    @Test
    fun `more frames means smaller ones`() {
        val few = PlaybackPlan.of(animation(frames = 4), PlaybackQuality.RENDERED).budgetSide
        val many = PlaybackPlan.of(animation(frames = 120), PlaybackQuality.RENDERED).budgetSide

        assertTrue("$many is not smaller than $few", many < few)
    }

    @Test
    fun `the whole frame set stays inside the budget`() {
        listOf(2, 24, 60, 120).forEach { frames ->
            val plan = PlaybackPlan.of(animation(frames), PlaybackQuality.RENDERED)
            val bytes = plan.renderedCount.toLong() * plan.budgetSide * plan.budgetSide * 4

            // The floor at MIN_SIDE can exceed the budget on purpose — below it there is no
            // preview left to show — so the claim holds wherever the budget is the binding
            // constraint rather than the floor.
            if (plan.budgetSide > PlaybackPlan.MIN_SIDE) {
                assertTrue(
                    "$frames frames at ${plan.budgetSide}px is $bytes bytes",
                    bytes <= PlaybackPlan.MEMORY_BUDGET_BYTES,
                )
            }
        }
    }

    @Test
    fun `a frame is never squeezed below the point of being a preview`() {
        val plan = PlaybackPlan.of(animation(frames = 120), PlaybackQuality.RENDERED, memoryBudgetBytes = 1)

        assertEquals(PlaybackPlan.MIN_SIDE, plan.budgetSide)
    }

    @Test
    fun `a frame is never rendered larger than the quality allows`() {
        listOf(PlaybackQuality.QUICK, PlaybackQuality.RENDERED).forEach { quality ->
            val plan = PlaybackPlan.of(animation(frames = 2), quality, memoryBudgetBytes = Long.MAX_VALUE / 2)
            assertEquals("$quality was allowed past its own ceiling", quality.maxSide, plan.budgetSide)
        }
    }

    /** A stepped run draws fewer frames, so each of them may be larger. */
    @Test
    fun `skipping frames buys size for the ones that are drawn`() {
        val animation = animation(frames = 120)

        val full = PlaybackPlan.of(animation, PlaybackQuality.RENDERED, memoryBudgetBytes = 8L * 1024 * 1024)
        val quick = PlaybackPlan.of(animation, PlaybackQuality.QUICK, memoryBudgetBytes = 8L * 1024 * 1024)

        assertTrue("half the frames did not buy any room", quick.budgetSide > full.budgetSide)
    }

    // --- what it says -------------------------------------------------------------------

    @Test
    fun `a full run reports the length of the loop`() {
        val animation = animation(frames = 24, fps = 12)
        val plan = PlaybackPlan.of(animation, PlaybackQuality.RENDERED)

        assertEquals("24 frames · 2.0s loop", plan.status(animation.durationSeconds))
    }

    /** A preview that quietly differs from the export is worse than a slow one. */
    @Test
    fun `an approximate run says that it is one`() {
        val animation = animation(frames = 24)
        val plan = PlaybackPlan.of(animation, PlaybackQuality.QUICK)

        assertEquals("12 of 24 frames · approximate preview", plan.status(animation.durationSeconds))
    }

    // --- exporting ----------------------------------------------------------------------

    @Test
    fun `an export renders every frame and never calls itself approximate`() {
        val plan = PlaybackPlan.everyFrame(animation(frames = 25), maxSide = 1080)

        assertEquals((0 until 25).toList(), plan.positions)
        assertTrue("an export is the thing a preview approximates", !plan.approximate)
    }

    @Test
    fun `an export is held to the budget too`() {
        val plan = PlaybackPlan.everyFrame(animation(frames = 120), maxSide = 1080)

        assertTrue("an export of 120 frames was allowed full size", plan.budgetSide < 1080)
        assertTrue(plan.budgetSide >= PlaybackPlan.MIN_SIDE)
    }
}
