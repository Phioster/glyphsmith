package org.phioster.glyphsmith.core.pipeline

/**
 * Reuses pixel buffers within one render pass.
 *
 * The effect chain is the heaviest allocator in the app: every step that cannot work in place
 * takes a whole new `IntArray` the size of the image, and at a live-camera frame of 480×480 that
 * is 0.9 MB each. Four active effects churn several megabytes per frame, which is the GC jank the
 * preview stutters on.
 *
 * The rule that makes this safe is narrow and worth stating: a buffer goes back into the pool
 * **only** when the chain can prove nothing holds it — that is, when a step returned a *different*
 * buffer than it was given, so the one it was given is unreachable. Nothing else is ever
 * recycled. In particular the buffer behind a bitmap that has reached the UI is never touched;
 * handing a live buffer out twice would corrupt an image in a way no test would catch.
 *
 * Not thread-safe, and does not need to be: a pool belongs to one [RenderContext], and a render
 * pass runs on one thread.
 */
class BufferPool {

    private val free = HashMap<Int, ArrayDeque<IntArray>>()

    /**
     * A buffer of exactly [size] ints. Recycled if one is spare, freshly allocated otherwise.
     *
     * Contents are undefined — callers overwrite every element, which every current caller does.
     * Zeroing here would give back most of what pooling saves.
     */
    fun take(size: Int): IntArray {
        val bucket = free[size] ?: return IntArray(size)
        return bucket.removeLastOrNull() ?: IntArray(size)
    }

    /** Offers a buffer back. Only ever called with a buffer the caller has proven is dead. */
    fun give(buffer: IntArray) {
        if (buffer.isEmpty() || totalHeld >= MAX_HELD) return
        free.getOrPut(buffer.size) { ArrayDeque() }.addLast(buffer)
        totalHeld++
    }

    private var totalHeld = 0

    private companion object {
        /**
         * A render only ever has a handful of buffers alive at once, so holding more than this
         * would be keeping memory against a demand that never comes.
         */
        const val MAX_HELD = 8
    }
}
