package org.phioster.glyphsmith.ascii

import kotlin.math.exp
import kotlin.math.min
import kotlin.random.Random

/**
 * Threshold matrices that are *generated* rather than transcribed.
 *
 * Bayer is a published table small enough to write out, but a clustered-dot screen and a
 * blue-noise mask are defined by their construction rules, and building them from those
 * rules is both shorter and safer than copying numbers out of a book — a mistyped entry in
 * a 1024-value table is invisible until someone stares at an artefact for an hour.
 *
 * Everything here is computed once and cached: these are pure functions of their size.
 */
object DitherMatrices {

    // Guarded because the preview and the preset thumbnails render concurrently on the
    // default dispatcher, and a HashMap resized from two threads at once corrupts silently.
    private val clusteredCache = HashMap<Int, Array<IntArray>>()
    private val blueNoiseCache = HashMap<Int, Array<IntArray>>()
    private val lock = Any()

    /**
     * A clustered-dot halftone screen: thresholds increase with distance from the tile
     * centre, so ink appears in the middle first and the dot grows outward as the tone
     * darkens. This is what a printing screen does, and what Bayer deliberately does not.
     *
     * Ties are broken by index so the result is deterministic — two cells the same distance
     * out must still get different thresholds, or the dot grows in visible jumps.
     */
    fun clusteredDot(n: Int): Array<IntArray> = synchronized(lock) {
        clusteredCache.getOrPut(n) { buildClusteredDot(n) }
    }

    private fun buildClusteredDot(n: Int): Array<IntArray> {
        val centre = (n - 1) / 2.0
        val ranked = (0 until n * n).sortedWith(
            compareBy(
                {
                    val dx = it % n - centre
                    val dy = it / n - centre
                    dx * dx + dy * dy
                },
                { it },
            ),
        )
        val out = Array(n) { IntArray(n) }
        ranked.forEachIndexed { rank, index -> out[index / n][index % n] = rank }
        return out
    }

    /**
     * A blue-noise mask by Ulichney's void-and-cluster method.
     *
     * The point of blue noise is that its energy sits at high frequencies: the pattern looks
     * evenly scattered with no clumps and no visible grid, so a dither built on it reads as
     * texture rather than as a screen. Bayer is the opposite — its structure is the artefact.
     *
     * The construction, in the published three phases: start from a random binary pattern
     * and relax it by repeatedly moving the pixel in the tightest cluster into the largest
     * void; then rank that prototype downward by removing tight clusters, and upward by
     * filling large voids.
     *
     * Energy is kept incrementally. Recomputing the whole filtered field after every one of
     * the n² swaps would be O(n⁴) per pass and hopeless at 32×32.
     *
     * Ulichney's third phase — reversing the roles once the pattern passes half density —
     * is simplified here into continuing to fill the largest void all the way up. The mask
     * stays a valid permutation and stays blue at the frequencies that matter; the top end
     * is a little less even than the published method would make it.
     */
    fun blueNoise(n: Int, seed: Int = 1): Array<IntArray> = synchronized(lock) {
        blueNoiseCache.getOrPut(n) { buildBlueNoise(n, seed) }
    }

    private fun buildBlueNoise(n: Int, seed: Int): Array<IntArray> {
        val size = n * n
        val binary = BooleanArray(size)
        val energy = DoubleArray(size)
        val kernel = gaussianFootprint(n)

        fun toggle(index: Int, on: Boolean) {
            binary[index] = on
            val sign = if (on) 1.0 else -1.0
            val x0 = index % n
            val y0 = index / n
            for (y in 0 until n) {
                for (x in 0 until n) {
                    energy[y * n + x] += sign * kernel[wrap(x - x0, n) * n + wrap(y - y0, n)]
                }
            }
        }

        /** Densest spot among the set pixels — the tightest cluster. */
        fun tightestCluster(): Int {
            var best = -1
            for (i in 0 until size) {
                if (binary[i] && (best < 0 || energy[i] > energy[best])) best = i
            }
            return best
        }

        /** Emptiest spot among the unset pixels — the largest void. */
        fun largestVoid(): Int {
            var best = -1
            for (i in 0 until size) {
                if (!binary[i] && (best < 0 || energy[i] < energy[best])) best = i
            }
            return best
        }

        // A tenth of the tile, which is the density Ulichney's method relaxes best from.
        val initial = (size / 10).coerceAtLeast(1)
        val random = Random(seed)
        val start = (0 until size).shuffled(random).take(initial)
        start.forEach { toggle(it, true) }

        // Phase 0 — relax until moving the tightest cluster into the largest void is a
        // no-op. The cap is insurance: the relaxation is supposed to converge, and a mask
        // that is merely well-relaxed still works, whereas a hung generator does not.
        var guard = size * 4
        while (guard-- > 0) {
            val cluster = tightestCluster()
            toggle(cluster, false)
            val void = largestVoid()
            if (void == cluster) {
                toggle(cluster, true)
                break
            }
            toggle(void, true)
        }

        val rank = IntArray(size)
        val prototype = binary.copyOf()

        // Phase 1 — rank the prototype's pixels downward by pulling clusters apart.
        var index = initial - 1
        while (index >= 0) {
            val cluster = tightestCluster()
            toggle(cluster, false)
            rank[cluster] = index
            index--
        }

        // Phase 2 — from the prototype back up, filling the largest void each time.
        prototype.forEachIndexed { i, on -> if (on != binary[i]) toggle(i, on) }
        index = initial
        while (index < size) {
            val void = largestVoid()
            toggle(void, true)
            rank[void] = index
            index++
        }

        return Array(n) { y -> IntArray(n) { x -> rank[y * n + x] } }
    }

    /** Toroidal offset: the mask has to tile, so distance wraps at the edges. */
    private fun wrap(delta: Int, n: Int): Int {
        val m = Math.floorMod(delta, n)
        return min(m, n - m)
    }

    /**
     * Gaussian weights by wrapped separation. σ = 1.5 is the value Ulichney uses; larger
     * blurs the distinction between neighbouring candidates until the relaxation stalls.
     */
    private fun gaussianFootprint(n: Int): DoubleArray {
        val sigma = 1.5
        val out = DoubleArray(n * n)
        for (dx in 0 until n) {
            for (dy in 0 until n) {
                val d2 = (dx * dx + dy * dy).toDouble()
                out[dx * n + dy] = exp(-d2 / (2.0 * sigma * sigma))
            }
        }
        return out
    }
}
