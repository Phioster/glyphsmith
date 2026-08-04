package org.phioster.glyphsmith.state

import org.phioster.glyphsmith.data.Source

/**
 * The loaded image or video, and the position the preview is looking at.
 *
 * Fifth slice of splitting the view model, and the one the others left behind: [Source] is the
 * only thing in the application that has to be *released*. A still costs nothing to drop, but a
 * video holds a `MediaMetadataRetriever`, and the rules for handing that back were followed by
 * hand at four call sites — the three loaders, and `onCleared`. Nothing checked them, and
 * nothing could: a leaked decoder does not fail a test, it just never comes back.
 *
 * ## The invariants
 *
 * - **Adopting closes what it replaces, exactly once.** Not the source it is handed, even when
 *   that is the one already held — closing the live source under a running render is the one
 *   way this class could make things worse than the field it replaces.
 * - **A new source starts at the beginning.** The position is a property of *this* source; the
 *   three loaders each reset it themselves before, which is the arrangement where the fourth
 *   loader forgets and a photograph opens at wherever a video was left.
 * - **Only a video can be scrubbed.** [seek] answers whether the position moved, so the caller
 *   knows whether a re-render is owed rather than deciding that for itself.
 * - **Pixels and their size are taken together.** [frame] is one call rather than three reads,
 *   because a render that took the buffer from one source and the dimensions from another is a
 *   crash inside the sampler — and the window for it is real: a render is dispatched to another
 *   thread, and a new image can be loaded while it is in flight.
 *
 * What deliberately stays with the view model: decoding a `Uri` into a source, which needs a
 * `Context` and belongs to whoever has the Android plumbing — the same line [PresetController]
 * draws — and the rendered output, which belongs to the last render rather than to the source.
 */
class SourceController {

    /** The source itself, for the callers that walk it frame by frame. Null when none loaded. */
    var source: Source? = null
        private set

    /** Where in the source the preview sits, 0..1. Meaningless for a still, which ignores it. */
    var position: Float = 0f
        private set

    /** True when the position selects something — that is, when a video is loaded. */
    val isMoving: Boolean get() = source?.isMoving == true

    /** Swaps in a new source and releases whatever the old one was holding. */
    fun adopt(next: Source) {
        val previous = source
        if (previous === next) return
        source = next
        position = 0f
        previous?.close()
    }

    /**
     * Moves the preview position, clamped to 0..1.
     *
     * Returns false when there is nothing to scrub — no source, or a still, which returns the
     * same frame at every position and would only cost a render to say so.
     */
    fun seek(to: Float): Boolean {
        if (!isMoving) return false
        position = to.coerceIn(0f, 1f)
        return true
    }

    /**
     * What to render: the pixels at the current position, and the size they are in. Null when
     * nothing is loaded.
     */
    fun frame(): Frame? = source?.let { Frame(it.pixelsAt(position), it.width, it.height) }

    /** One source's pixels and its dimensions, taken together and therefore consistent. */
    class Frame(val pixels: IntArray, val width: Int, val height: Int)

    /** Closes the source and forgets it. Safe to call twice — `onCleared` may follow a release. */
    fun release() {
        source?.close()
        source = null
    }
}
