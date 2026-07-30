package org.phioster.glyphsmith.core.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The bands have to cover the image exactly once.
 *
 * A gap leaves uninitialised pixels — and because a pooled buffer holds whatever the last render
 * left in it, that shows up as fragments of a *previous frame*, which reads as a glitch effect
 * rather than as a bug. An overlap is worse: two workers writing one row is the corruption the pool
 * documentation warns about, and it would only appear under load.
 */
class RowParallelTest {

    @Test
    fun `bands cover every row exactly once`() {
        // Heights either side of the fork threshold, and deliberately ones that do not divide
        // evenly by any plausible worker count.
        for (height in listOf(1, 7, 255, 256, 257, 999, 1024, 1031)) {
            val visits = IntArray(height)
            RowParallel.rows(height) { band ->
                for (y in band) synchronized(visits) { visits[y]++ }
            }
            assertTrue(
                "height $height: some row was not visited exactly once — ${visits.toList()}",
                visits.all { it == 1 },
            )
        }
    }

    @Test
    fun `bands are contiguous and in range`() {
        val height = 1000
        val seen = ArrayList<IntRange>()
        RowParallel.rows(height) { band -> synchronized(seen) { seen.add(band) } }

        val sorted = seen.sortedBy { it.first }
        assertEquals(0, sorted.first().first)
        assertEquals(height - 1, sorted.last().last)
        for (i in 1 until sorted.size) {
            assertEquals(
                "band ${sorted[i]} does not continue from ${sorted[i - 1]}",
                sorted[i - 1].last + 1,
                sorted[i].first,
            )
        }
    }

    @Test
    fun `a zero or negative height does nothing`() {
        val calls = AtomicInteger()
        RowParallel.rows(0) { calls.incrementAndGet() }
        RowParallel.rows(-5) { calls.incrementAndGet() }
        assertEquals(0, calls.get())
    }

    /** Below the threshold there must be exactly one band, or the point of the threshold is lost. */
    @Test
    fun `a small image is not split at all`() {
        val bands = ArrayList<IntRange>()
        RowParallel.rows(64) { bands.add(it) }
        assertEquals(1, bands.size)
        assertEquals(0..63, bands.first())
    }

    /**
     * The whole reason the pipeline can trust this: every worker is finished before the call
     * returns. If it were not, [NodePipeline] would recycle a buffer still being written.
     */
    @Test
    fun `every band has finished before the call returns`() {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        RowParallel.rows(2048) { band ->
            val now = running.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            // Enough work that the bands genuinely overlap in time on a multi-core device.
            var sink = 0
            for (y in band) sink += y
            assertTrue(sink >= 0)
            running.decrementAndGet()
        }
        assertEquals("a worker outlived the call", 0, running.get())
        assertTrue("nothing ran at all", peak.get() >= 1)
    }

    @Test
    fun `an exception in a band reaches the caller`() {
        var thrown = false
        try {
            RowParallel.rows(1024) { band ->
                if (band.first == 0) error("band failed")
            }
        } catch (expected: Throwable) {
            thrown = true
        }
        assertTrue("a failing band must not be swallowed", thrown)
        assertFalse(false)
    }
}
