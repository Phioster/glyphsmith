package org.phioster.glyphsmith.state

import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.data.PlaybackQuality
import kotlin.math.sqrt

/**
 * What a playback run will do, worked out before a single pixel moves.
 *
 * Fourth slice of splitting the ViewModel, and deliberately not a controller. The animation
 * code is half arithmetic and half coroutines-and-bitmaps; only the first half is worth moving,
 * because only the first half can be tested. Playing frames back is a loop over a job with a
 * delay in it, and wrapping that in a class would buy a smaller ViewModel and nothing else.
 *
 * The arithmetic is what was untested and what actually goes wrong:
 *
 * - **The budget.** Every rendered frame is held in memory at once, so their size has to fall
 *   as their number rises. Sixty frames at full preview size is the allocation that does not
 *   come back.
 * - **The stride.** A quick preview renders every other frame, which is only an approximation
 *   of the loop if each frame is then held twice as long. Miss that and the preview runs at
 *   double speed, and looks like a bug in the animation rather than in the preview.
 * - **The count.** Ceiling division, so a stepped run over an odd number of frames does not
 *   quietly drop the last one.
 */
class PlaybackPlan private constructor(
    /** The frames of the animation that will actually be rendered, in order. */
    val positions: List<Int>,
    /** Longest side any one frame may be rendered at. */
    val budgetSide: Int,
    /** How long each rendered frame is shown, already scaled by the stride. */
    val frameDelayMs: Long,
    private val step: Int,
    private val totalFrames: Int,
) {

    /** True when frames are being skipped, so the preview is not what an export would give. */
    val approximate: Boolean get() = step > 1

    val renderedCount: Int get() = positions.size

    /**
     * What to say once the frames are up.
     *
     * An approximate preview says so. A preview that quietly differs from the export is worse
     * than a slow one — the difference gets blamed on the animation.
     */
    fun status(durationSeconds: Float): String = if (approximate) {
        "$renderedCount of $totalFrames frames · approximate preview"
    } else {
        "$renderedCount frames · ${"%.1f".format(durationSeconds)}s loop"
    }

    companion object {

        /** Smallest a frame may be squeezed to. Below this the preview stops being one. */
        const val MIN_SIDE = 120

        /** Every frame of a run is held at once, so this is the ceiling for the whole set. */
        const val MEMORY_BUDGET_BYTES = 96L * 1024 * 1024

        private const val BYTES_PER_PIXEL = 4

        fun of(
            animation: AnimationParams,
            quality: PlaybackQuality,
            memoryBudgetBytes: Long = MEMORY_BUDGET_BYTES,
        ): PlaybackPlan {
            val step = quality.step.coerceAtLeast(1)
            val total = animation.frames.coerceAtLeast(1)
            val positions = (0 until total step step).toList()

            return PlaybackPlan(
                positions = positions,
                budgetSide = budgetFor(positions.size, quality.maxSide, memoryBudgetBytes),
                // Each rendered frame stands in for `step` of them, so it is held that much
                // longer. Without this a quick preview runs at several times real speed.
                frameDelayMs = MILLIS_PER_SECOND * step / animation.fps.coerceAtLeast(1),
                step = step,
                totalFrames = total,
            )
        }

        /**
         * Every frame, at whatever size the budget allows.
         *
         * What an export does: no stride, because an export is the thing a preview is an
         * approximation *of*. The budget still applies — the frames are all held at once here
         * too, and an export is the run most likely to be long.
         */
        fun everyFrame(animation: AnimationParams, maxSide: Int): PlaybackPlan {
            val total = animation.frames.coerceAtLeast(1)
            return PlaybackPlan(
                positions = (0 until total).toList(),
                budgetSide = budgetFor(total, maxSide, MEMORY_BUDGET_BYTES),
                frameDelayMs = MILLIS_PER_SECOND / animation.fps.coerceAtLeast(1),
                step = 1,
                totalFrames = total,
            )
        }

        /**
         * The size that keeps [frames] of them inside the budget.
         *
         * Only the frames actually rendered count, so a stepped run gets more room per frame
         * rather than the same room spread over frames it never draws.
         */
        private fun budgetFor(frames: Int, maxSide: Int, budget: Long): Int {
            val perFrame = budget / frames.coerceAtLeast(1) / BYTES_PER_PIXEL
            return sqrt(perFrame.toDouble()).toInt().coerceIn(MIN_SIDE, maxSide.coerceAtLeast(MIN_SIDE))
        }

        private const val MILLIS_PER_SECOND = 1000L
    }
}
