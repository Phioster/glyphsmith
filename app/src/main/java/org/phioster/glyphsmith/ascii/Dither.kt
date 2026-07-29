package org.phioster.glyphsmith.ascii

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Everything the position-dependent modes need beyond a cell's coordinates.
 *
 * [scale] is a percentage that stretches the *pattern* without touching the cell size. The
 * two being separate is the point: shrinking cells until the pattern no longer resolves is
 * a documented way to make these algorithms fail on purpose, and it only works if the
 * pattern doesn't shrink along with them.
 */
data class PatternOptions(
    val scale: Int = 100,
    /** Cells per period of a modulation pattern. */
    val period: Int = 8,
    val angle: Int = 0,
    /** 0..1 of a period; animating this makes the pattern travel. */
    val phase: Float = 0f,
    /** Grid centre in cells — only [DitherMode.MOD_RINGS] cares. */
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    /**
     * The orb family's own controls, mirroring the six sliders Dither Boy's orb styles
     * carry. They only reach [DitherMode.MOD_ORB] and [DitherMode.BEEHIVE]; the other
     * modulation modes have no orbs to shape.
     */
    /**
     * 0..100 — the second axis, whose meaning is the style's own.
     *
     * One field rather than a slider per style, because a dozen new fields would each be
     * dead for eleven of the twelve. What it means is spelled out where it is read, and the
     * panel relabels the slider so nobody has to guess.
     */
    val density: Int = 50,
    val orb: OrbOptions = OrbOptions(),
)

/**
 * What an orb looks like, separately from where the grid puts it.
 *
 * [count] and [size] pull in opposite directions on purpose: count sets how many orbs fit
 * across a period, size how much of its own cell each one fills. Turning the count up and
 * the size down gives a fine stipple; the reverse gives fat overlapping blobs.
 */
data class OrbOptions(
    /** Orbs per period, 1..20. */
    val count: Int = 1,
    /**
     * 0..100 — how much of its cell an orb fills before it is clipped.
     *
     * Defaulted to 100 together with an [intensity] of 0 because those two values reproduce
     * exactly what the orb modes did before these controls existed: a linear ramp from the
     * cell centre to its corner. Anything else would silently repaint every saved preset
     * that uses an orb mode.
     */
    val size: Int = 100,
    /** 0..100 — how hard the orb's edge is. 0 is a soft gradient, 100 a flat disc. */
    val intensity: Int = 0,
    /** 0..100 — per-orb jitter of position and radius. */
    val random: Int = 0,
    /** -100..100 — shifts alternate rows sideways, the way BEEHIVE does at 50. */
    val offset: Int = 0,
    /** Rotates the orb lattice independently of the pattern angle. */
    val direction: Int = 0,
    val seed: Int = 1,
)

/** One neighbour that receives a share of a cell's quantisation error. */
data class DiffusionTap(val dx: Int, val dy: Int, val weight: Float)

/**
 * Dither matrices and error-diffusion kernels, applied to the *cell grid* rather than to
 * pixels — the cell is the smallest thing this app can draw, so that's the resolution the
 * error has to be spread across.
 */
object Dither {

    /** Matrix-driven modes: the threshold comes from a fixed tile. */
    /**
     * Deliberately a plain `when` rather than `matrix(mode) != null`: the picker asks this
     * for every mode as it draws, and the generated matrices are built on first use. Going
     * through [matrix] would generate every blue-noise mask just to open a dropdown.
     */
    fun isOrdered(mode: DitherMode): Boolean = when (mode) {
        DitherMode.BAYER_2, DitherMode.BAYER_4, DitherMode.BAYER_8, DitherMode.BAYER_16,
        DitherMode.CLUSTER_4, DitherMode.CLUSTER_8,
        DitherMode.BLUE_NOISE_16, DitherMode.BLUE_NOISE_32,
        -> true

        else -> false
    }

    /** Modes whose threshold is a continuous function of position rather than a tile. */
    fun isModulation(mode: DitherMode): Boolean = when (mode) {
        DitherMode.MOD_LINES, DitherMode.MOD_WAVE, DitherMode.MOD_RINGS,
        DitherMode.MOD_ORB, DitherMode.BEEHIVE, DitherMode.UNIFORM_MODULATION,
        DitherMode.HEART_GRID, DitherMode.POP_TONE,
        DitherMode.CHECKERS, DitherMode.DIAMOND, DitherMode.CROSSHATCH,
        DitherMode.STIPPLING, DitherMode.BIT_TONE, DitherMode.BLOCK_TONE,
        DitherMode.PRINT_PATTERN, DitherMode.GRIDLOCK, DitherMode.RANDOM_ORDERED,
        DitherMode.NOISE, DitherMode.WAVE, DitherMode.RADIAL_BURST,
        DitherMode.SINE_DISTORT,
        -> true

        else -> false
    }

    /**
     * Styles that read the cell's brightness as well as its position.
     *
     * The distinction is worth naming rather than leaving implicit. A pattern that ignores
     * the picture tiles the same way over a face and over a blank wall; one that reads it
     * puts its ink where the image is dark, which is what a pen does. Everything else must
     * keep ignoring it, and a test enforces exactly this split.
     */
    fun isContentAware(mode: DitherMode): Boolean = when (mode) {
        DitherMode.CROSSHATCH, DitherMode.STIPPLING -> true
        else -> false
    }

    /**
     * What the pattern-size slider is actually called for this style.
     *
     * The storage is one field; the meaning is not. Labelling it "period" while it sets a
     * dot size is the kind of small dishonesty that makes a panel feel arbitrary.
     */
    fun periodLabel(mode: DitherMode): String = when (mode) {
        DitherMode.CHECKERS -> "block size"
        DitherMode.CROSSHATCH -> "line spacing"
        DitherMode.STIPPLING -> "dot spacing"
        DitherMode.BIT_TONE, DitherMode.BLOCK_TONE -> "dot size"
        DitherMode.PRINT_PATTERN -> "dot scale"
        DitherMode.GRIDLOCK -> "grid size"
        DitherMode.RANDOM_ORDERED -> "block size"
        DitherMode.NOISE -> "grain"
        DitherMode.WAVE, DitherMode.SINE_DISTORT -> "frequency"
        DitherMode.RADIAL_BURST -> "ring spacing"
        else -> "period"
    }

    /** Styles that use the second axis, and what it means there. */
    fun densityLabel(mode: DitherMode): String? = when (mode) {
        DitherMode.SINE_DISTORT -> "second frequency"
        DitherMode.RADIAL_BURST -> "spokes"
        DitherMode.CROSSHATCH -> "line weight"
        DitherMode.STIPPLING -> "dot size"
        else -> null
    }

    /**
     * Every mode that picks its glyph from a threshold at ([x], [y]) instead of by passing
     * an error on to its neighbours. Bayer and the modulation family differ only in where
     * that threshold comes from, so the engine treats them the same way.
     */
    fun isThresholdBased(mode: DitherMode): Boolean = isOrdered(mode) || isModulation(mode)

    private val BAYER_2X2 = arrayOf(
        intArrayOf(0, 2),
        intArrayOf(3, 1),
    )

    /** `M₂ₙ = [[4M+0, 4M+2], [4M+3, 4M+1]]` — the standard recursive construction. */
    private fun grow(base: Array<IntArray>): Array<IntArray> {
        val n = base.size
        val out = Array(n * 2) { IntArray(n * 2) }
        for (y in 0 until n) {
            for (x in 0 until n) {
                val v = base[y][x] * 4
                out[y][x] = v
                out[y][x + n] = v + 2
                out[y + n][x] = v + 3
                out[y + n][x + n] = v + 1
            }
        }
        return out
    }

    private val BAYER_4X4 = grow(BAYER_2X2)
    private val BAYER_8X8 = grow(BAYER_4X4)
    private val BAYER_16X16 = grow(BAYER_8X8)

    fun matrix(mode: DitherMode): Array<IntArray>? = when (mode) {
        DitherMode.BAYER_2 -> BAYER_2X2
        DitherMode.BAYER_4 -> BAYER_4X4
        DitherMode.BAYER_8 -> BAYER_8X8
        DitherMode.BAYER_16 -> BAYER_16X16
        DitherMode.CLUSTER_4 -> DitherMatrices.clusteredDot(4)
        DitherMode.CLUSTER_8 -> DitherMatrices.clusteredDot(8)
        DitherMode.BLUE_NOISE_16 -> DitherMatrices.blueNoise(16)
        DitherMode.BLUE_NOISE_32 -> DitherMatrices.blueNoise(32)
        else -> null
    }

    /** Normalised threshold in 0..1 for the cell at ([x], [y]). */
    fun orderedThreshold(mode: DitherMode, x: Int, y: Int): Float {
        val m = matrix(mode) ?: return 0.5f
        val n = m.size
        val value = m[Math.floorMod(y, n)][Math.floorMod(x, n)]
        return (value + 0.5f) / (n * n)
    }

    private const val TAU = 2.0 * PI

    /** Fractional part, always in 0..1 — `x % 1` would go negative on the left half. */
    private fun frac(x: Float): Float = x - floor(x)

    /**
     * Threshold in 0..1 for any threshold-based mode, with [options] applied.
     *
     * Both families go through here so that [PatternOptions.scale] — the pattern-size
     * control that is deliberately independent of the cell size — reaches Bayer too.
     *
     * [value] is the cell's own brightness in 0..1. Every mode that existed before the
     * catalogue arrived ignores it, and a test holds them to that. It is here for the styles
     * that cannot work without it: a crosshatch whose lines thicken in the shadows, a stipple
     * whose dots crowd where the image is dark. Those read the picture as well as the
     * position, which is precisely what separates them from a repeating tile.
     */
    fun threshold(
        mode: DitherMode,
        x: Int,
        y: Int,
        value: Float,
        options: PatternOptions,
    ): Float {
        val factor = (options.scale / 100f).coerceAtLeast(0.01f)
        if (isOrdered(mode)) {
            // Floored rather than rounded: rounding would make the tile stutter by a cell
            // every time the scaled coordinate crosses a half-step.
            return orderedThreshold(mode, floor(x / factor).toInt(), floor(y / factor).toInt())
        }
        if (!isModulation(mode)) return 0.5f
        // The centre is given in unscaled cells, so it has to travel with the coordinates —
        // otherwise the rings would crawl away from the image centre as the scale changes.
        return modulatedThreshold(
            mode,
            x / factor,
            y / factor,
            value,
            options.copy(centerX = options.centerX / factor, centerY = options.centerY / factor),
        )
    }

    /**
     * The modulation family: a threshold surface sampled at a scaled cell coordinate.
     *
     * Studio AAA publishes neither the maths nor the names behind Dither Boy's modulation
     * category — only that "Modulation Lines", "Waveform" and "Beehive" exist. These five
     * are this app's own reading of that category, not a reconstruction of theirs.
     */
    @Suppress("UNUSED_PARAMETER")
    fun modulatedThreshold(
        mode: DitherMode,
        x: Float,
        y: Float,
        value: Float,
        options: PatternOptions,
    ): Float {
        val period = options.period.coerceAtLeast(1).toFloat()
        val radians = options.angle * PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        // Rotate into the pattern's own frame, then measure in periods rather than cells.
        val u = (x * c + y * s) / period
        val v = (-x * s + y * c) / period
        val phase = options.phase

        return when (mode) {
            DitherMode.MOD_LINES -> frac(u + phase)

            // A sine band whose phase is dragged along the perpendicular axis — straight
            // stripes would be MOD_LINES with a softer edge, which is not worth a mode.
            DitherMode.MOD_WAVE -> {
                val warp = 0.25f * sin(TAU * (v * 0.5f + phase)).toFloat()
                0.5f + 0.5f * sin(TAU * (u + warp + phase)).toFloat()
            }

            DitherMode.MOD_RINGS -> {
                val r = hypot(x - options.centerX, y - options.centerY) / period
                0.5f + 0.5f * sin(TAU * (r + phase)).toFloat()
            }

            // Square wave instead of a sine: bands of equal width with a hard step between
            // them, where MOD_LINES ramps and MOD_WAVE eases. "Uniform" is theirs and it is
            // a fair description — every band gets the same weight.
            DitherMode.UNIFORM_MODULATION -> if (frac(u + phase) < 0.5f) 0.25f else 0.75f

            // The implicit heart curve (x²+y²−1)³ − x²y³, evaluated per cell of the lattice.
            // Inside the curve the value is negative, so the sign becomes the threshold and
            // the shape falls out of the arithmetic rather than being drawn.
            DitherMode.HEART_GRID -> {
                val hx = (frac(u + phase) - 0.5f) * 2.6f
                val hy = -(frac(v) - 0.5f) * 2.6f
                val t = hx * hx + hy * hy - 1f
                val inside = t * t * t - hx * hx * hy * hy * hy
                if (inside <= 0f) 0f else (inside * 0.9f).coerceIn(0f, 1f)
            }

            // Pop Tone. Named in their tutorials but never demonstrated, so this is a guess:
            // a coarse dot with a very hard rim, which is the pop-art screen look and the
            // only reading of the name I can defend. Marked as such rather than presented
            // as a reconstruction.
            DitherMode.POP_TONE -> {
                val dx = frac(u + phase) - 0.5f
                val dy = frac(v) - 0.5f
                val d = hypot(dx, dy) / 0.70710678f
                if (d < 0.62f) 0.08f else 0.92f
            }

            // --- geometry -----------------------------------------------------------
            // These read the position and nothing else, so they tile a face exactly as they
            // tile a wall. That is not a shortcoming: a screen is supposed to be indifferent
            // to what is printed through it.

            DitherMode.CHECKERS ->
                if ((floor(u + phase).toInt() + floor(v).toInt()) % 2 == 0) 0.25f else 0.75f

            // Manhattan distance rather than Euclidean, which is the whole difference between
            // a diamond and a circle.
            DitherMode.DIAMOND ->
                (abs(frac(u + phase) - 0.5f) + abs(frac(v) - 0.5f)).coerceIn(0f, 1f)

            /*
             * Crosshatch. Four stroke directions, and a direction only starts drawing once
             * the cell is dark enough to need it — which is what a pen actually does, and the
             * reason this style cannot work from position alone. Light areas get one set of
             * strokes, shadows get all four crossing.
             */
            DitherMode.CROSSHATCH -> {
                val darkness = 1f - value
                val weight = 0.15f + options.density / 100f * 0.45f
                val strokes = floatArrayOf(
                    v + phase,
                    u + phase,
                    (u + v) * 0.70710678f,
                    (u - v) * 0.70710678f,
                )
                var inked = false
                for (k in strokes.indices) {
                    if (darkness * strokes.size < k) break
                    if (frac(strokes[k]) < weight) {
                        inked = true
                        break
                    }
                }
                if (inked) 0.1f else 0.9f
            }

            /*
             * Stippling. One dot per lattice cell, jittered off the grid so the eye reads
             * scatter rather than rows, and its radius grows as the cell darkens. The jitter
             * is keyed to the cell so a dot stays put between frames instead of boiling.
             */
            DitherMode.STIPPLING -> {
                val cu = floor(u + phase).toInt()
                val cv = floor(v).toInt()
                val dx = frac(u + phase) - hash(cu, cv, 7)
                val dy = frac(v) - hash(cv, cu, 13)
                val radius = (1f - value) * (0.2f + options.density / 100f * 0.6f)
                if (hypot(dx, dy) < radius) 0.1f else 0.9f
            }

            // Concentric rings quantised to four steps. The quantisation is the style: a
            // smooth dot is Block Tone, a stepped one reads as low-bit.
            DitherMode.BIT_TONE -> {
                val dx = frac(u + phase) - 0.5f
                val dy = frac(v) - 0.5f
                val d = (hypot(dx, dy) / 0.70710678f).coerceIn(0f, 1f)
                floor(d * 4f) / 4f
            }

            // The printer's screen sits at 45°, where the lattice is least visible to the
            // eye. Rotating it here rather than asking for an angle is the difference between
            // a halftone and a grid of circles.
            DitherMode.BLOCK_TONE -> {
                val ru = (u + v) * 0.70710678f + phase
                val rv = (v - u) * 0.70710678f
                val dx = frac(ru) - 0.5f
                val dy = frac(rv) - 0.5f
                (hypot(dx, dy) / 0.70710678f).coerceIn(0f, 1f)
            }

            // Process colour uses four screens at 15°, 75°, 0° and 45° — angles chosen so
            // that their overlap makes a rosette instead of a moiré. Overlaying all four is
            // what makes this read as print rather than as one halftone.
            DitherMode.PRINT_PATTERN -> {
                var nearest = 1f
                for (degrees in SCREEN_ANGLES) {
                    val r = degrees * PI / 180.0
                    val c2 = cos(r).toFloat()
                    val s2 = sin(r).toFloat()
                    val du = frac(u * c2 + v * s2 + phase) - 0.5f
                    val dv = frac(-u * s2 + v * c2) - 0.5f
                    nearest = min(nearest, hypot(du, dv) / 0.70710678f)
                }
                nearest.coerceIn(0f, 1f)
            }

            // Two diagonal rules crossing. The threshold is the distance to whichever rule is
            // nearer, so the lines themselves stay dark while the fields between them open up.
            DitherMode.GRIDLOCK -> {
                val a = abs(frac((u + v) * 0.70710678f + phase) - 0.5f)
                val b = abs(frac((u - v) * 0.70710678f) - 0.5f)
                (min(a, b) * 2f).coerceIn(0f, 1f)
            }

            // A threshold rolled once per block. Bigger blocks read as torn paper, single
            // cells as film grain — which is why Noise below rolls per cell instead.
            DitherMode.RANDOM_ORDERED -> hash(floor(u + phase).toInt(), floor(v).toInt(), 3)

            DitherMode.NOISE -> hash(
                floor((u + phase) * period).toInt(),
                floor(v * period).toInt(),
                5,
            )

            DitherMode.WAVE -> 0.5f + 0.5f * sin(TAU * (u + phase)).toFloat()

            // Rings and spokes at once: the radius gives the concentric waves, the angle
            // gives the burst, and adding them before the sine is what curves the spokes.
            DitherMode.RADIAL_BURST -> {
                val dx = x - options.centerX
                val dy = y - options.centerY
                val spokes = 1f + options.density / 100f * 15f
                val ring = hypot(dx, dy) / period
                val spoke = (atan2(dy, dx) / TAU).toFloat() * spokes
                0.5f + 0.5f * sin(TAU * (ring + spoke + phase)).toFloat()
            }

            // Two sines at different frequencies laid over each other. Where their crests
            // nearly agree they beat against one another, and that beat is the moiré.
            DitherMode.SINE_DISTORT -> {
                val second = 0.2f + options.density / 100f * 3f
                val a = sin(TAU * (u + phase)).toFloat()
                val b = sin(TAU * (v * second)).toFloat()
                (0.5f + 0.25f * (a + b)).coerceIn(0f, 1f)
            }

            DitherMode.MOD_ORB -> orbField(u + phase, v, options.orb, 0f)

            // Offsetting every other row by half a cell is what turns a square grid of orbs
            // into a honeycomb — the cells pack against each other instead of lining up.
            // It is the same machinery as the orb offset control, fixed at a half step.
            DitherMode.BEEHIVE -> orbField(u + phase, v, options.orb, 0.5f)

            else -> 0.5f
        }
    }

    /**
     * The orb lattice: distance from the centre of the cell containing ([u], [v]), shaped by
     * [orb]. Low in the middle, high at the edges — dark glyphs therefore clump into dots,
     * which is the halftone look.
     *
     * [rowShift] is applied on top of [OrbOptions.offset] and is what BEEHIVE uses to stagger
     * its rows; keeping it a parameter means the honeycomb and the offset slider are the same
     * mechanism rather than two that can disagree.
     */
    private fun orbField(u: Float, v: Float, orb: OrbOptions, rowShift: Float): Float {
        val count = orb.count.coerceIn(1, 20).toFloat()
        val radians = orb.direction * PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        // Rotated and multiplied up, so the lattice can turn and repeat independently of the
        // pattern that placed it.
        val lu = (u * c + v * s) * count
        val lv = (-u * s + v * c) * count

        val row = floor(lv).toInt()
        val stagger = rowShift + orb.offset / 100f
        val shifted = lu + if (Math.floorMod(row, 2) == 0) 0f else stagger

        var dx = frac(shifted) - 0.5f
        var dy = frac(lv) - 0.5f
        if (orb.random > 0) {
            // Jitter is keyed to the cell, not to the pixel, so a whole orb moves as one
            // rather than dissolving into noise.
            val jitter = orb.random / 100f * 0.5f
            dx += (hash(floor(shifted).toInt(), row, orb.seed) - 0.5f) * jitter
            dy += (hash(row, floor(shifted).toInt(), orb.seed + 71) - 0.5f) * jitter
        }

        val radius = (orb.size / 100f).coerceIn(0.02f, 1f)
        val distance = hypot(dx, dy) / 0.70710678f / radius
        // Intensity is the hardness of the edge: at 100 the disc is flat and its rim abrupt,
        // at 0 the orb is a smooth gradient with no rim at all.
        val hardness = (orb.intensity / 100f).coerceIn(0f, 1f)
        val soft = distance.coerceIn(0f, 1f)
        val hard = if (distance < 1f) 0f else 1f
        return (soft * (1f - hardness) + hard * hardness).coerceIn(0f, 1f)
    }

    /** The trade's process-colour screen angles, in the order cyan, magenta, yellow, black. */
    private val SCREEN_ANGLES = intArrayOf(15, 75, 0, 45)

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }

    /**
     * Where a cell's error goes. Weights sum to 1 for every kernel **except Atkinson**,
     * which deliberately throws away a quarter of the error — that loss is exactly what
     * gives Atkinson its higher-contrast, less muddy look, so it is not normalised away.
     */
    fun diffusionKernel(mode: DitherMode): List<DiffusionTap> = when (mode) {
        DitherMode.FLOYD_STEINBERG -> listOf(
            DiffusionTap(1, 0, 7 / 16f),
            DiffusionTap(-1, 1, 3 / 16f),
            DiffusionTap(0, 1, 5 / 16f),
            DiffusionTap(1, 1, 1 / 16f),
        )

        DitherMode.ATKINSON -> listOf(
            DiffusionTap(1, 0, 1 / 8f),
            DiffusionTap(2, 0, 1 / 8f),
            DiffusionTap(-1, 1, 1 / 8f),
            DiffusionTap(0, 1, 1 / 8f),
            DiffusionTap(1, 1, 1 / 8f),
            DiffusionTap(0, 2, 1 / 8f),
        )

        DitherMode.JARVIS -> listOf(
            DiffusionTap(1, 0, 7 / 48f),
            DiffusionTap(2, 0, 5 / 48f),
            DiffusionTap(-2, 1, 3 / 48f),
            DiffusionTap(-1, 1, 5 / 48f),
            DiffusionTap(0, 1, 7 / 48f),
            DiffusionTap(1, 1, 5 / 48f),
            DiffusionTap(2, 1, 3 / 48f),
            DiffusionTap(-2, 2, 1 / 48f),
            DiffusionTap(-1, 2, 3 / 48f),
            DiffusionTap(0, 2, 5 / 48f),
            DiffusionTap(1, 2, 3 / 48f),
            DiffusionTap(2, 2, 1 / 48f),
        )

        DitherMode.SIERRA_LITE -> listOf(
            DiffusionTap(1, 0, 2 / 4f),
            DiffusionTap(-1, 1, 1 / 4f),
            DiffusionTap(0, 1, 1 / 4f),
        )

        // The published kernels below are transcribed from the halftoning literature, where
        // each is named after whoever proposed it. Their weights are the whole algorithm —
        // what differs between them is only how far and how evenly the error is spread, and
        // that is exactly what makes their grain look different.
        DitherMode.STUCKI -> listOf(
            DiffusionTap(1, 0, 8 / 42f),
            DiffusionTap(2, 0, 4 / 42f),
            DiffusionTap(-2, 1, 2 / 42f),
            DiffusionTap(-1, 1, 4 / 42f),
            DiffusionTap(0, 1, 8 / 42f),
            DiffusionTap(1, 1, 4 / 42f),
            DiffusionTap(2, 1, 2 / 42f),
            DiffusionTap(-2, 2, 1 / 42f),
            DiffusionTap(-1, 2, 2 / 42f),
            DiffusionTap(0, 2, 4 / 42f),
            DiffusionTap(1, 2, 2 / 42f),
            DiffusionTap(2, 2, 1 / 42f),
        )

        // Stucki with the bottom row dropped — faster, and a touch sharper for it.
        DitherMode.BURKES -> listOf(
            DiffusionTap(1, 0, 8 / 32f),
            DiffusionTap(2, 0, 4 / 32f),
            DiffusionTap(-2, 1, 2 / 32f),
            DiffusionTap(-1, 1, 4 / 32f),
            DiffusionTap(0, 1, 8 / 32f),
            DiffusionTap(1, 1, 4 / 32f),
            DiffusionTap(2, 1, 2 / 32f),
        )

        DitherMode.SIERRA -> listOf(
            DiffusionTap(1, 0, 5 / 32f),
            DiffusionTap(2, 0, 3 / 32f),
            DiffusionTap(-2, 1, 2 / 32f),
            DiffusionTap(-1, 1, 4 / 32f),
            DiffusionTap(0, 1, 5 / 32f),
            DiffusionTap(1, 1, 4 / 32f),
            DiffusionTap(2, 1, 2 / 32f),
            DiffusionTap(-1, 2, 2 / 32f),
            DiffusionTap(0, 2, 3 / 32f),
            DiffusionTap(1, 2, 2 / 32f),
        )

        DitherMode.SIERRA_TWO_ROW -> listOf(
            DiffusionTap(1, 0, 4 / 16f),
            DiffusionTap(2, 0, 3 / 16f),
            DiffusionTap(-2, 1, 1 / 16f),
            DiffusionTap(-1, 1, 2 / 16f),
            DiffusionTap(0, 1, 3 / 16f),
            DiffusionTap(1, 1, 2 / 16f),
            DiffusionTap(2, 1, 1 / 16f),
        )

        // A widely copied mistranscription of Floyd-Steinberg that took on a life of its own.
        // It is included because it genuinely looks different — coarser, more directional —
        // not because it is correct. The name says so.
        DitherMode.FALSE_FLOYD -> listOf(
            DiffusionTap(1, 0, 3 / 8f),
            DiffusionTap(0, 1, 3 / 8f),
            DiffusionTap(1, 1, 2 / 8f),
        )

        /*
         * Stevenson-Arce, 1985. The weights sit on a hexagonal lattice — every second
         * column of the 7x4 window is empty — which is why it reaches three cells sideways
         * yet costs about what a 5x3 kernel does. The staggering is the point: a hexagonal
         * neighbourhood has no axis for artefacts to line up along.
         */
        DitherMode.STEVENSON_ARCE -> listOf(
            DiffusionTap(2, 0, 32 / 200f),
            DiffusionTap(-3, 1, 12 / 200f),
            DiffusionTap(-1, 1, 26 / 200f),
            DiffusionTap(1, 1, 30 / 200f),
            DiffusionTap(3, 1, 16 / 200f),
            DiffusionTap(-2, 2, 12 / 200f),
            DiffusionTap(0, 2, 26 / 200f),
            DiffusionTap(2, 2, 12 / 200f),
            DiffusionTap(-3, 3, 5 / 200f),
            DiffusionTap(-1, 3, 12 / 200f),
            DiffusionTap(1, 3, 12 / 200f),
            DiffusionTap(3, 3, 5 / 200f),
        )

        /*
         * Smooth Diffuse. Named in their tutorials; the weights are this app's own reading.
         *
         * The classic kernels concentrate most of the error on the two or three cells
         * nearest the current one, which is what gives their grain its bite. Spreading it
         * thinly and evenly over a wider neighbourhood instead trades that bite for a much
         * finer, more even texture — the same total error, simply shared further.
         */
        DitherMode.SMOOTH_DIFFUSE -> listOf(
            DiffusionTap(1, 0, 4 / 32f),
            DiffusionTap(2, 0, 3 / 32f),
            DiffusionTap(3, 0, 2 / 32f),
            DiffusionTap(-3, 1, 1 / 32f),
            DiffusionTap(-2, 1, 2 / 32f),
            DiffusionTap(-1, 1, 3 / 32f),
            DiffusionTap(0, 1, 4 / 32f),
            DiffusionTap(1, 1, 3 / 32f),
            DiffusionTap(2, 1, 2 / 32f),
            DiffusionTap(3, 1, 1 / 32f),
            DiffusionTap(-2, 2, 1 / 32f),
            DiffusionTap(-1, 2, 2 / 32f),
            DiffusionTap(0, 2, 2 / 32f),
            DiffusionTap(1, 2, 2 / 32f),
            DiffusionTap(2, 2, 0 / 32f),
        )

        // Axis-dominant diffusion. The four classic kernels all spread the error roughly
        // evenly forward and down, which is what makes their noise look isotropic. Pushing
        // almost all of it along one axis instead makes the error travel in a line, and the
        // result reads as streaks rather than as grain — the "diffuse along Y" look.
        DitherMode.DIFFUSE_Y -> listOf(
            DiffusionTap(0, 1, 11 / 20f),
            DiffusionTap(0, 2, 5 / 20f),
            DiffusionTap(1, 0, 2 / 20f),
            DiffusionTap(-1, 1, 1 / 20f),
            DiffusionTap(1, 1, 1 / 20f),
        )

        DitherMode.DIFFUSE_X -> listOf(
            DiffusionTap(1, 0, 12 / 20f),
            DiffusionTap(2, 0, 5 / 20f),
            DiffusionTap(0, 1, 2 / 20f),
            DiffusionTap(1, 1, 1 / 20f),
        )

        else -> emptyList()
    }

    /** How many rows below the current one a kernel reaches — the error buffer's depth. */
    fun kernelDepth(mode: DitherMode): Int =
        (diffusionKernel(mode).maxOfOrNull { it.dy } ?: 0) + 1

    /**
     * Whether this mode's weights change with the value being quantised.
     *
     * Every classic kernel is a constant: Floyd–Steinberg spreads 7/16 to the right whether
     * the cell is nearly white or nearly black. Ostromoukhov's does not — his whole result
     * comes from picking different weights per input level, so his kernel cannot be hoisted
     * out of the loop the way the others are.
     */
    fun hasVariableKernel(mode: DitherMode): Boolean = false

    /**
     * The kernel for a cell of brightness [value] in 0..1, or null if [mode] uses a constant.
     *
     * **Implementations must return a cached list, never build one.** This is asked once per
     * cell, and a megapixel image is a million allocations if it is answered carelessly.
     *
     * A mode with a variable kernel must still declare a representative one in
     * [diffusionKernel] — that is what [kernelDepth] measures to size the error buffer, and
     * what the loop falls back on.
     */
    @Suppress("UNUSED_PARAMETER")
    fun variableKernel(mode: DitherMode, value: Float): List<DiffusionTap>? = null

    /**
     * Whether this mode decides every cell up front instead of along the rows.
     *
     * Some algorithms simply do not visit pixels in reading order. Riemersma follows a
     * space-filling curve; dot diffusion goes by class number, so it may touch the bottom
     * right corner before the top left. Rather than bend the main loop around them, they
     * hand back a finished grid of glyph indices and the loop just reads it.
     */
    fun isPrecomputed(mode: DitherMode): Boolean = mode == DitherMode.RIEMERSMA
}
