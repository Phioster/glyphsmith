package org.phioster.glyphsmith.core.pipeline

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Splits a pixel loop into horizontal bands and runs them at the same time.
 *
 * Three rules make this safe against the rest of the pipeline, and a node that breaks any of them
 * will produce a corrupt image that no test here can catch:
 *
 * 1. **Take every buffer before forking.** [BufferPool] is a bare `HashMap` and is documented as
 *    single-threaded; two workers calling `Pixels.buffer()` at once can be handed the same array.
 *    Ask for the output buffer on the calling thread, then give workers disjoint row ranges of it.
 * 2. **Randomness must be positional.** A shared `Random(seed)` drawn in scan order — which is
 *    what every older effect does — makes the result depend on which worker got there first. Use
 *    a hash of `(x, y, seed)` instead.
 * 3. **Every worker is joined before this returns.** [NodePipeline] recycles a node's input buffer
 *    the moment the node hands back a different one, so a worker that outlived `process` would be
 *    writing into a buffer already reissued to someone else. [rows] blocks until all bands are
 *    done, which is what enforces this.
 *
 * Deliberately *not* built on `Dispatchers.Default`. The render already runs on that dispatcher,
 * so blocking one of its threads while the bands need the rest of them is how a busy device
 * deadlocks or crawls. A small pool of its own costs a handful of daemon threads and cannot
 * starve the caller.
 */
object RowParallel {

    /**
     * Below this, a single-threaded loop wins. Forking costs a task submission and a join per
     * band, which a preview-sized image cannot earn back — and preset thumbnails at 160px would
     * pay it dozens of times per rebuild.
     */
    private const val MIN_ROWS_TO_FORK = 256

    private val workers by lazy { Runtime.getRuntime().availableProcessors().coerceIn(1, 8) }

    private val pool by lazy {
        Executors.newFixedThreadPool(
            workers,
            ThreadFactory { runnable ->
                // Daemon, so a pool that is idle at shutdown never holds the process open.
                Thread(runnable, "glyphsmith-rows").apply { isDaemon = true }
            },
        )
    }

    /**
     * Runs [block] over bands covering `0 until height`, in parallel when that is worth it.
     *
     * [block] is called with disjoint, contiguous row ranges whose union is exactly
     * `0 until height`, so a caller writing one output row per input row needs no coordination at
     * all. Bands are handed out in order, but nothing may depend on that — the order they *finish*
     * in is undefined.
     */
    fun rows(height: Int, block: (IntRange) -> Unit) {
        if (height <= 0) return
        if (height < MIN_ROWS_TO_FORK || workers == 1) {
            block(0 until height)
            return
        }

        val bandHeight = (height + workers - 1) / workers
        val tasks = ArrayList<Callable<Unit>>(workers)
        var start = 0
        while (start < height) {
            val from = start
            val until = minOf(start + bandHeight, height)
            tasks.add(Callable { block(from until until) })
            start = until
        }

        // invokeAll blocks until every band has finished, which is rule 3. It also surfaces a
        // worker's exception when the future is read, rather than losing it on another thread.
        pool.invokeAll(tasks).forEach { it.get() }
    }
}
