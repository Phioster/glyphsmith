package org.phioster.glyphsmith.ascii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DitherTest {

    private val diffusionModes = DitherMode.entries.filter {
        Dither.diffusionKernel(it).isNotEmpty()
    }

    private val orderedModes = DitherMode.entries.filter { Dither.isOrdered(it) }

    /**
     * Every mode has to be exactly one kind of thing.
     *
     * There are now three: a kernel that passes error on, a threshold read off the position,
     * and a region that flattens an area. NONE is the one genuine exception — it is the
     * absence of dithering — and it is named here rather than quietly allowed, so a mode that
     * falls through by accident still fails.
     *
     * Riemersma used to be named alongside it. It no longer needs to be: walking a curve to
     * decide every cell up front is what the region styles do too, and that is a kind rather
     * than an oddity.
     */
    @Test
    fun `every mode is a kernel, a threshold, a region, or the absence of dithering`() {
        DitherMode.entries.forEach { mode ->
            val kinds = listOf(
                Dither.diffusionKernel(mode).isNotEmpty(),
                Dither.isThresholdBased(mode),
                Dither.isPrecomputed(mode),
            ).count { it }
            val expected = if (mode == DitherMode.NONE) 0 else 1
            assertEquals("$mode is $kinds kinds of dither at once", expected, kinds)
        }
    }

    /** The curve must visit every cell of its square exactly once, or cells go unquantised. */
    @Test
    fun `the hilbert curve covers its square exactly once`() {
        listOf(4, 8, 16).forEach { side ->
            val seen = (0 until side * side).map { Riemersma.curvePoint(side, it) }
            assertEquals("side $side revisits a cell", side * side, seen.toSet().size)
            assertTrue(
                "side $side leaves the square",
                seen.all { (x, y) -> x in 0 until side && y in 0 until side },
            )
        }
    }

    /** Successive points on a Hilbert curve are always neighbours — that is its whole value. */
    @Test
    fun `the hilbert curve never jumps`() {
        val side = 16
        for (step in 1 until side * side) {
            val (px, py) = Riemersma.curvePoint(side, step - 1)
            val (x, y) = Riemersma.curvePoint(side, step)
            val distance = kotlin.math.abs(x - px) + kotlin.math.abs(y - py)
            assertEquals("jump at step $step", 1, distance)
        }
    }

    @Test
    fun `riemersma quantises every cell into the ramp`() {
        val cols = 13
        val rows = 7
        val luma = FloatArray(cols * rows) { (it % cols) / (cols - 1f) }
        val out = Riemersma.quantise(luma, cols, rows, 4, 1f)

        assertEquals(cols * rows, out.size)
        assertTrue("an index left the ramp", out.all { it in 0..3 })
        assertTrue("the result is flat", out.toSet().size > 1)
    }

    @Test
    fun `diffusion kernels conserve the error, except Atkinson`() {
        diffusionModes.filter { it != DitherMode.ATKINSON }.forEach { mode ->
            val sum = Dither.diffusionKernel(mode).map { it.weight }.sum()
            assertEquals("$mode should pass on all of the error", 1f, sum, 1e-4f)
        }
    }

    /**
     * Atkinson deliberately drops a quarter of the error — that loss is what gives it its
     * higher contrast. If this ever sums to 1, someone has "fixed" it into a different
     * algorithm.
     */
    @Test
    fun `Atkinson discards a quarter of the error on purpose`() {
        val sum = Dither.diffusionKernel(DitherMode.ATKINSON).map { it.weight }.sum()
        assertEquals(0.75f, sum, 1e-4f)
    }

    @Test
    fun `diffusion only ever writes forwards`() {
        diffusionModes.forEach { mode ->
            Dither.diffusionKernel(mode).forEach { tap ->
                // A tap on an earlier row, or to the left on the current row, would land on a
                // cell that has already been quantised — the error would be silently lost.
                assertTrue("$mode taps backwards", tap.dy > 0 || (tap.dy == 0 && tap.dx > 0))
            }
        }
    }

    /**
     * The axis-dominant kernels exist to make the error travel in a line rather than spread
     * evenly. If most of the weight ever stops sitting on one axis they have quietly become
     * ordinary diffusion kernels with worse noise.
     */
    @Test
    fun `the axis kernels really are axis-dominant`() {
        val down = Dither.diffusionKernel(DitherMode.DIFFUSE_Y)
            .filter { it.dx == 0 && it.dy > 0 }
            .sumOf { it.weight.toDouble() }
        assertTrue("DIFFUSE_Y only sends $down straight down", down > 0.7)

        val across = Dither.diffusionKernel(DitherMode.DIFFUSE_X)
            .filter { it.dy == 0 && it.dx > 0 }
            .sumOf { it.weight.toDouble() }
        assertTrue("DIFFUSE_X only sends $across along the row", across > 0.7)
    }

    @Test
    fun `kernel depth covers the furthest row a kernel reaches`() {
        assertEquals(1, Dither.kernelDepth(DitherMode.NONE))
        assertEquals(2, Dither.kernelDepth(DitherMode.FLOYD_STEINBERG))
        assertEquals(3, Dither.kernelDepth(DitherMode.ATKINSON))
        assertEquals(3, Dither.kernelDepth(DitherMode.JARVIS))
    }

    /**
     * Every threshold matrix — written out, or generated — has to be a complete permutation
     * of 0 until n². A generated one that repeats or skips a value has a bug in its
     * construction, and the symptom is a tone that never appears rather than a crash.
     */
    @Test
    fun `every ordered matrix is a complete permutation`() {
        assertTrue("no ordered modes at all", orderedModes.isNotEmpty())
        orderedModes.forEach { mode ->
            val matrix = requireNotNull(Dither.matrix(mode)) { "$mode claims ordered but has no matrix" }
            val n = matrix.size
            val values = matrix.flatMap { it.asList() }.sorted()
            assertEquals("$mode is not square", n, matrix[0].size)
            assertEquals("$mode is not $n×$n", n * n, values.size)
            assertEquals("$mode is not a permutation", (0 until n * n).toList(), values)
        }
    }

    /** The clustered screen grows a dot from the middle: the centre must threshold first. */
    @Test
    fun `a clustered dot screen puts its lowest threshold in the middle`() {
        val matrix = DitherMatrices.clusteredDot(8)
        val centre = matrix[3][3]
        val corner = matrix[0][0]
        assertTrue("centre $centre is not below corner $corner", centre < corner)
    }

    /**
     * Blue noise must not clump. Bayer's whole character is its regular structure, so if the
     * mask were merely random — or accidentally Bayer-like — neighbouring cells would often
     * hold neighbouring ranks. Measuring the mean rank gap to the right-hand neighbour is a
     * cheap proxy: a well-spread mask keeps it high.
     */
    @Test
    fun `the blue noise mask is well spread`() {
        val n = 16
        val matrix = DitherMatrices.blueNoise(n)
        var total = 0.0
        for (y in 0 until n) {
            for (x in 0 until n) {
                total += kotlin.math.abs(matrix[y][x] - matrix[y][(x + 1) % n])
            }
        }
        val mean = total / (n * n)
        // Uniformly random ranks average n²/3 apart; anything near that is well scattered.
        assertTrue("neighbouring ranks average only $mean apart", mean > n * n / 5.0)
    }

    @Test
    fun `generated matrices are stable across calls`() {
        assertTrue(
            DitherMatrices.blueNoise(16).contentDeepEquals(DitherMatrices.blueNoise(16)),
        )
        assertTrue(
            DitherMatrices.clusteredDot(8).contentDeepEquals(DitherMatrices.clusteredDot(8)),
        )
    }

    @Test
    fun `ordered thresholds stay inside the unit interval and tile`() {
        val mode = DitherMode.BAYER_4
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val t = Dither.orderedThreshold(mode, x, y)
                assertTrue("threshold $t out of range", t > 0f && t < 1f)
                // The matrix must repeat, including at negative coordinates.
                assertEquals(t, Dither.orderedThreshold(mode, x + 4, y + 4), 1e-6f)
                assertEquals(t, Dither.orderedThreshold(mode, x - 4, y - 4), 1e-6f)
            }
        }
    }

    @Test
    fun `ordered modes are the bayer ones`() {
        assertTrue(Dither.isOrdered(DitherMode.BAYER_2))
        assertTrue(Dither.isOrdered(DitherMode.BAYER_8))
        assertTrue(!Dither.isOrdered(DitherMode.FLOYD_STEINBERG))
        assertTrue(!Dither.isOrdered(DitherMode.NONE))
    }

    private val modulationModes = listOf(
        DitherMode.MOD_LINES,
        DitherMode.MOD_WAVE,
        DitherMode.MOD_RINGS,
        DitherMode.MOD_ORB,
        DitherMode.BEEHIVE,
    )

    @Test
    fun `threshold-based covers bayer and modulation but nothing else`() {
        modulationModes.forEach { assertTrue("$it", Dither.isModulation(it)) }
        modulationModes.forEach { assertTrue("$it", Dither.isThresholdBased(it)) }
        assertTrue(Dither.isThresholdBased(DitherMode.BAYER_4))
        assertTrue(!Dither.isThresholdBased(DitherMode.FLOYD_STEINBERG))
        assertTrue(!Dither.isThresholdBased(DitherMode.NONE))
        assertTrue(!Dither.isModulation(DitherMode.BAYER_4))
    }

    @Test
    fun `modulation thresholds stay inside the unit interval`() {
        val options = PatternOptions(period = 7, angle = 37, centerX = 20f, centerY = 12f)
        modulationModes.forEach { mode ->
            for (y in -20..40) {
                for (x in -20..40) {
                    val t = Dither.threshold(mode, x, y, 0.5f, options)
                    assertTrue("$mode gave $t at ($x,$y)", t in 0f..1f)
                }
            }
        }
    }

    /**
     * The pattern scale must be independent of the cell grid: at 200% the same threshold has
     * to turn up twice as far out. If this fails the control has collapsed back into being a
     * second cell-size slider, which is exactly what it exists not to be.
     */
    @Test
    fun `doubling the pattern scale doubles the period`() {
        val normal = PatternOptions(scale = 100, period = 4)
        val doubled = PatternOptions(scale = 200, period = 4)
        (modulationModes + DitherMode.BAYER_4).forEach { mode ->
            for (y in 0 until 12) {
                for (x in 0 until 12) {
                    assertEquals(
                        "$mode at ($x,$y)",
                        Dither.threshold(mode, x, y, 0.5f, normal),
                        Dither.threshold(mode, 2 * x, 2 * y, 0.5f, doubled),
                        1e-5f,
                    )
                }
            }
        }
    }

    /** The honeycomb is a square orb grid with every other row pushed half a cell across. */
    @Test
    fun `beehive offsets odd rows and leaves even ones alone`() {
        val options = PatternOptions(period = 6)
        var differences = 0
        for (x in 0 until 24) {
            // Row 0 sits in the first band of v, row 6 in the second — one shifted, one not.
            assertEquals(
                Dither.threshold(DitherMode.MOD_ORB, x, 0, 0.5f, options),
                Dither.threshold(DitherMode.BEEHIVE, x, 0, 0.5f, options),
                1e-5f,
            )
            val orb = Dither.threshold(DitherMode.MOD_ORB, x, 6, 0.5f, options)
            val hive = Dither.threshold(DitherMode.BEEHIVE, x, 6, 0.5f, options)
            if (kotlin.math.abs(orb - hive) > 1e-3f) differences++
        }
        assertTrue("odd band is not offset at all", differences > 0)
    }

    /**
     * The orb controls were added after the orb modes shipped, so their defaults have to
     * reproduce the old behaviour exactly — a linear ramp from the cell centre to its corner.
     * Anything else silently repaints every saved preset that uses an orb mode.
     */
    @Test
    fun `default orb options reproduce the original orb ramp`() {
        val options = PatternOptions(period = 8)
        for (y in 0 until 24) {
            for (x in 0 until 24) {
                val u = x / 8f
                val v = y / 8f
                val dx = u - kotlin.math.floor(u) - 0.5f
                val dy = v - kotlin.math.floor(v) - 0.5f
                val expected = (kotlin.math.hypot(dx, dy) / 0.70710678f).coerceIn(0f, 1f)
                assertEquals(
                    "orb default drifted at ($x,$y)",
                    expected,
                    Dither.threshold(DitherMode.MOD_ORB, x, y, 0.5f, options),
                    1e-5f,
                )
            }
        }
    }

    @Test
    fun `each orb control changes the field`() {
        val base = PatternOptions(period = 8)
        fun sample(options: PatternOptions) =
            (0 until 24).flatMap { y -> (0 until 24).map { x -> Dither.threshold(DitherMode.MOD_ORB, x, y, 0.5f, options) } }

        val reference = sample(base)
        val variants = mapOf(
            "count" to base.copy(orb = base.orb.copy(count = 3)),
            "size" to base.copy(orb = base.orb.copy(size = 40)),
            "intensity" to base.copy(orb = base.orb.copy(intensity = 100)),
            "random" to base.copy(orb = base.orb.copy(random = 60)),
            "offset" to base.copy(orb = base.orb.copy(offset = 50)),
            "direction" to base.copy(orb = base.orb.copy(direction = 30)),
        )
        variants.forEach { (name, options) ->
            assertTrue("the $name control does nothing", sample(options) != reference)
        }
    }

    /** Beehive is the offset control fixed at half a step, so the two must agree there. */
    @Test
    fun `beehive equals an orb offset by half`() {
        val options = PatternOptions(period = 6)
        for (y in 0 until 18) {
            for (x in 0 until 18) {
                assertEquals(
                    "at ($x,$y)",
                    Dither.threshold(DitherMode.BEEHIVE, x, y, 0.5f, options),
                    Dither.threshold(
                        DitherMode.MOD_ORB,
                        x,
                        y,
                        0.5f,
                        options.copy(orb = options.orb.copy(offset = 50)),
                    ),
                    1e-5f,
                )
            }
        }
    }

    @Test
    fun `modulation phase travels a whole period and comes back`() {
        val options = PatternOptions(period = 5)
        modulationModes.forEach { mode ->
            for (x in 0 until 10) {
                assertEquals(
                    "$mode does not close at ($x)",
                    Dither.threshold(mode, x, 3, 0.5f, options.copy(phase = 0f)),
                    Dither.threshold(mode, x, 3, 0.5f, options.copy(phase = 1f)),
                    1e-4f,
                )
            }
        }
    }
}
