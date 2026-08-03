package org.phioster.glyphsmith.core.pipeline

import org.phioster.glyphsmith.core.image.Pixels

/**
 * One step in the render chain.
 *
 * The interface trades in [Pixels] rather than `Bitmap` for two reasons. The twelve effects
 * already had exactly this shape — `(Pixels, Params) -> Pixels` — so wrapping them costs
 * nothing. And a node that never touches an Android type is testable on the JVM, which a
 * `Bitmap` step can never be in this project: unit tests run with
 * `unitTests.isReturnDefaultValues = true`, so a real bitmap is simply not available.
 *
 * A node owns its own parameters. It is built per render from whatever holds them, which is
 * why there is no params argument here — the chain does not need to know what any given step
 * is configured with, only whether to run it.
 */
interface ImageProcessorNode {

    /** Stable identity, for ordering and for naming the step in a failure. */
    val id: String

    /**
     * False when the step would be a no-op. The chain skips it entirely rather than letting
     * it early-return, so a disabled node costs nothing at all — not even a call.
     */
    val enabled: Boolean

    fun process(input: Pixels, ctx: RenderContext): Pixels
}

/**
 * What every node is allowed to know about the render it is part of.
 *
 * Deliberately free of [org.phioster.glyphsmith.ascii.RenderSettings]: the settings object lives
 * in the glyph package, and a core abstraction that reached into it would invert the
 * dependency this refactor is trying to establish. Nodes carry their own settings; the context
 * carries only what is true of the whole pass.
 */
class RenderContext(
    /** Longest output edge this pass is allowed to produce. */
    val maxSide: Int,
    /**
     * True while a slider is being dragged. A node may trade quality for latency when it is
     * set; nothing is required to.
     */
    val isScrubbing: Boolean = false,
    /** Where a node gets a working buffer from, so a repeated render stops churning the heap. */
    val pool: BufferPool = BufferPool(),
    /**
     * Normalised loop position, 0..1. Zero for a still.
     *
     * A node that wants to move over an animation reads this rather than counting its own calls:
     * frames may be rendered out of order, and a preview deliberately skips some, so a call
     * counter would drift out of step with the frame it is drawing. Zero on a still means a
     * time-driven node still has a defined look — the first frame of its cycle.
     */
    val time: Float = 0f,
)
