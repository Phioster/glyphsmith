package org.phioster.glyphsmith.core.dither

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The modulation family: a threshold surface sampled at a scaled cell coordinate.
 *
 * Each style is a function of where the cell is — and, for the ones that say so, of how bright
 * it is. They were one 400-line `when (mode)`; here each surface is a declaration of its own,
 * with the two things the panel needs to label its sliders sitting next to the maths that reads
 * them, so a control cannot drift away from what it controls.
 *
 * The vendor publishes neither the maths nor the names behind the reference app's modulation
 * category — only that "Modulation Lines", "Waveform" and "Beehive" exist. These are this app's
 * own reading of that category, not a reconstruction of theirs, and where a name is all that is
 * published the declaration says so.
 */
@Suppress("LargeClass")
internal object ModulationSurfaces {

    val MOD_LINES = Modulation { frac(u + phase) }

    // A sine band whose phase is dragged along the perpendicular axis — straight stripes would
    // be MOD_LINES with a softer edge, which is not worth a mode.
    val MOD_WAVE = Modulation {
        val warp = 0.25f * sin(TAU * (v * 0.5f + phase)).toFloat()
        0.5f + 0.5f * sin(TAU * (u + warp + phase)).toFloat()
    }

    val MOD_RINGS = Modulation {
        val r = hypot(x - centerX, y - centerY) / period
        0.5f + 0.5f * sin(TAU * (r + phase)).toFloat()
    }

    // Square wave instead of a sine: bands of equal width with a hard step between them, where
    // MOD_LINES ramps and MOD_WAVE eases. "Uniform" is theirs and it is a fair description —
    // every band gets the same weight.
    val UNIFORM_MODULATION = Modulation { if (frac(u + phase) < 0.5f) 0.25f else 0.75f }

    // The implicit heart curve (x²+y²−1)³ − x²y³, evaluated per cell of the lattice. Inside the
    // curve the value is negative, so the sign becomes the threshold and the shape falls out of
    // the arithmetic rather than being drawn.
    val HEART_GRID = Modulation {
        val hx = (frac(u + phase) - 0.5f) * 2.6f
        val hy = -(frac(v) - 0.5f) * 2.6f
        val t = hx * hx + hy * hy - 1f
        val inside = t * t * t - hx * hx * hy * hy * hy
        if (inside <= 0f) 0f else (inside * 0.9f).coerceIn(0f, 1f)
    }

    // Pop Tone. Named in their tutorials but never demonstrated, so this is a guess: a coarse
    // dot with a very hard rim, which is the pop-art screen look and the only reading of the
    // name I can defend. Marked as such rather than presented as a reconstruction.
    val POP_TONE = Modulation {
        val dx = frac(u + phase) - 0.5f
        val dy = frac(v) - 0.5f
        val d = hypot(dx, dy) / ROOT_HALF
        if (d < 0.62f) 0.08f else 0.92f
    }

    // --- geometry ---------------------------------------------------------------------------
    // These read the position and nothing else, so they tile a face exactly as they tile a wall.
    // That is not a shortcoming: a screen is supposed to be indifferent to what is printed
    // through it.

    val CHECKERS = Modulation(periodLabel = "block size") {
        if ((floor(u + phase).toInt() + floor(v).toInt()) % 2 == 0) 0.25f else 0.75f
    }

    // Manhattan distance rather than Euclidean, which is the whole difference between a diamond
    // and a circle.
    val DIAMOND = Modulation {
        (abs(frac(u + phase) - 0.5f) + abs(frac(v) - 0.5f)).coerceIn(0f, 1f)
    }

    /*
     * Crosshatch. Four stroke directions, and a direction only starts drawing once the cell is
     * dark enough to need it — which is what a pen actually does, and the reason this style
     * cannot work from position alone. Light areas get one set of strokes, shadows get all four
     * crossing.
     */
    val CROSSHATCH = Modulation(
        periodLabel = "line spacing",
        densityLabel = "line weight",
        readsContent = true,
    ) {
        val darkness = 1f - value
        val weight = 0.15f + density / 100f * 0.45f
        val strokes = floatArrayOf(
            v + phase,
            u + phase,
            (u + v) * ROOT_HALF,
            (u - v) * ROOT_HALF,
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
     * Stippling. One dot per lattice cell, jittered off the grid so the eye reads scatter rather
     * than rows, and its radius grows as the cell darkens. The jitter is keyed to the cell so a
     * dot stays put between frames instead of boiling.
     */
    val STIPPLING = Modulation(
        periodLabel = "dot spacing",
        densityLabel = "dot size",
        readsContent = true,
    ) {
        val cu = floor(u + phase).toInt()
        val cv = floor(v).toInt()
        val dx = frac(u + phase) - hash(cu, cv, 7)
        val dy = frac(v) - hash(cv, cu, 13)
        val radius = (1f - value) * (0.2f + density / 100f * 0.6f)
        if (hypot(dx, dy) < radius) 0.1f else 0.9f
    }

    // Concentric rings quantised to four steps. The quantisation is the style: a smooth dot is
    // Block Tone, a stepped one reads as low-bit.
    val BIT_TONE = Modulation(periodLabel = "dot size") {
        val dx = frac(u + phase) - 0.5f
        val dy = frac(v) - 0.5f
        val d = (hypot(dx, dy) / ROOT_HALF).coerceIn(0f, 1f)
        floor(d * 4f) / 4f
    }

    // The printer's screen sits at 45°, where the lattice is least visible to the eye. Rotating
    // it here rather than asking for an angle is the difference between a halftone and a grid of
    // circles.
    val BLOCK_TONE = Modulation(periodLabel = "dot size") {
        val ru = (u + v) * ROOT_HALF + phase
        val rv = (v - u) * ROOT_HALF
        val dx = frac(ru) - 0.5f
        val dy = frac(rv) - 0.5f
        (hypot(dx, dy) / ROOT_HALF).coerceIn(0f, 1f)
    }

    // Process colour uses four screens at 15°, 75°, 0° and 45° — angles chosen so that their
    // overlap makes a rosette instead of a moiré. Overlaying all four is what makes this read as
    // print rather than as one halftone.
    val PRINT_PATTERN = Modulation(periodLabel = "dot scale") {
        var nearest = 1f
        for (degrees in SCREEN_ANGLES) {
            val r = degrees * PI / 180.0
            val c2 = cos(r).toFloat()
            val s2 = sin(r).toFloat()
            val du = frac(u * c2 + v * s2 + phase) - 0.5f
            val dv = frac(-u * s2 + v * c2) - 0.5f
            nearest = min(nearest, hypot(du, dv) / ROOT_HALF)
        }
        nearest.coerceIn(0f, 1f)
    }

    // Two diagonal rules crossing. The threshold is the distance to whichever rule is nearer, so
    // the lines themselves stay dark while the fields between them open up.
    val GRIDLOCK = Modulation(periodLabel = "grid size") {
        val a = abs(frac((u + v) * ROOT_HALF + phase) - 0.5f)
        val b = abs(frac((u - v) * ROOT_HALF) - 0.5f)
        (min(a, b) * 2f).coerceIn(0f, 1f)
    }

    // A threshold rolled once per block. Bigger blocks read as torn paper, single cells as film
    // grain — which is why Noise below rolls per cell instead.
    val RANDOM_ORDERED = Modulation(periodLabel = "block size") {
        hash(floor(u + phase).toInt(), floor(v).toInt(), 3)
    }

    val NOISE = Modulation(periodLabel = "grain") {
        hash(floor((u + phase) * period).toInt(), floor(v * period).toInt(), 5)
    }

    val WAVE = Modulation(periodLabel = "frequency") {
        0.5f + 0.5f * sin(TAU * (u + phase)).toFloat()
    }

    // Rings and spokes at once: the radius gives the concentric waves, the angle gives the
    // burst, and adding them before the sine is what curves the spokes.
    val RADIAL_BURST = Modulation(periodLabel = "ring spacing", densityLabel = "spokes") {
        val dx = x - centerX
        val dy = y - centerY
        val spokes = 1f + density / 100f * 15f
        val ring = hypot(dx, dy) / period
        val spoke = (atan2(dy, dx) / TAU).toFloat() * spokes
        0.5f + 0.5f * sin(TAU * (ring + spoke + phase)).toFloat()
    }

    // Two sines at different frequencies laid over each other. Where their crests nearly agree
    // they beat against one another, and that beat is the moiré.
    val SINE_DISTORT = Modulation(periodLabel = "frequency", densityLabel = "second frequency") {
        val second = 0.2f + density / 100f * 3f
        val a = sin(TAU * (u + phase)).toFloat()
        val b = sin(TAU * (v * second)).toFloat()
        (0.5f + 0.25f * (a + b)).coerceIn(0f, 1f)
    }

    // --- glitch and special -------------------------------------------------------------
    /*
     * The rest of the catalogue. The vendor names these and describes them in a line; the
     * mathematics is this app's own, and that is stated here rather than left for someone to
     * assume otherwise. What their controls are called told us what each one is *for*, which
     * turned out to be most of the work.
     */

    // Waveform. The wave's frequency follows the picture, so detail crowds the crests together
    // and flat areas stretch them out — a wave that reports what it crosses.
    val WAVEFORM = Modulation(
        periodLabel = "wavelength",
        densityLabel = "frequency range",
        readsContent = true,
    ) {
        val rate = 0.5f + (1f - value) * (1f + density / 100f * 3f)
        0.5f + 0.5f * sin(TAU * (u * rate + phase)).toFloat()
    }

    // The same idea with the wave itself bent by brightness rather than sped up, so the crests
    // bow around what is bright instead of bunching against it.
    val WAVEFORM_ALT = Modulation(
        periodLabel = "wavelength",
        densityLabel = "bend",
        readsContent = true,
    ) {
        val bend = (value - 0.5f) * (density / 100f * 4f)
        0.5f + 0.5f * sin(TAU * (u + v * bend + phase)).toFloat()
    }

    // Three sines at unrelated frequencies, summed and then pushed by the cell's own brightness.
    // Unrelated on purpose: rational ratios would beat into a visible repeat, and the point of
    // this one is a threshold with no period at all.
    val THRESHOLDER = Modulation(densityLabel = "content weight", readsContent = true) {
        val a = sin(TAU * (u + phase)).toFloat()
        val b = sin(TAU * (v * 1.6180339f)).toFloat()
        val c2 = sin(TAU * ((u + v) * 0.7548776f)).toFloat()
        val field = (a + b + c2) / 6f
        (0.5f + field + (value - 0.5f) * (density / 100f)).coerceIn(0f, 1f)
    }

    // Two sines crossed into a lattice of hollows, with brightness raising or lowering the water
    // level so the pools open and close across the picture.
    val SINE_WAVE_MOD = Modulation(periodLabel = "wavelength", readsContent = true) {
        val a = sin(TAU * (u + phase)).toFloat()
        val b = sin(TAU * v).toFloat()
        (0.5f + 0.25f * a * b + (1f - value - 0.5f) * 0.4f).coerceIn(0f, 1f)
    }

    /*
     * Topography. Contour lines at fixed heights through the brightness, exactly as a map draws
     * them, with the height field warped a little so the lines wander the way a coastline does
     * instead of sitting in perfect rings.
     */
    val TOPOGRAPHY = Modulation(
        periodLabel = "contour spacing",
        densityLabel = "warp",
        readsContent = true,
    ) {
        val warp = density / 100f * 0.25f *
            sin(TAU * (u * 0.5f + phase)).toFloat() * sin(TAU * (v * 0.5f)).toFloat()
        val height = (value + warp) * (period * 0.75f)
        val band = abs(frac(height) - 0.5f) * 2f
        band.coerceIn(0f, 1f)
    }

    // A polar spiral: turning the angle into the radius is what makes a circle into a coil, and
    // the second axis says how tightly it winds.
    val VORTEX = Modulation(periodLabel = "ring spacing", densityLabel = "twist") {
        val dx = x - centerX
        val dy = y - centerY
        val r = hypot(dx, dy) / period
        val twist = 0.5f + density / 100f * 4f
        val a = (atan2(dy, dx) / TAU).toFloat()
        0.5f + 0.5f * sin(TAU * (r + a * twist + phase)).toFloat()
    }

    /*
     * Rings spreading from bright cores. A cell's brightness offsets the ring phase, so light
     * areas push their rings outwards while dark ones hold them back, and where two fields meet
     * the rings interfere the way ripples do.
     */
    val RADIAL_PEAKS = Modulation(
        periodLabel = "ring spacing",
        densityLabel = "interference",
        readsContent = true,
    ) {
        val dx = x - centerX
        val dy = y - centerY
        val r = hypot(dx, dy) / period
        val push = value * (density / 100f * 2f)
        0.5f + 0.5f * sin(TAU * (r - push + phase)).toFloat()
    }

    // Flowing lines broken into dashes. The line is one sine and the dashing another across it,
    // so the dashes travel along the line rather than sitting on a grid.
    val DOTTED_LINES = Modulation(periodLabel = "line spacing", densityLabel = "dotting") {
        val line = 0.5f + 0.5f * sin(TAU * (v + phase)).toFloat()
        val dash = 0.5f + 0.5f * sin(TAU * (u * (1f + density / 100f * 6f))).toFloat()
        (line * 0.6f + dash * 0.4f).coerceIn(0f, 1f)
    }

    /*
     * Contours displaced by the picture. Same banding as Topography, but the bands are pushed
     * sideways by brightness rather than warped in place, so the lines bunch where the image
     * changes fast — thick in flat areas, tight over detail.
     */
    val DISPLACE_CONTOUR = Modulation(
        periodLabel = "contour spacing",
        densityLabel = "displacement",
        readsContent = true,
    ) {
        val shift = (value - 0.5f) * (1f + density / 100f * 5f)
        val band = abs(frac(u + shift + phase) - 0.5f) * 2f
        band.coerceIn(0f, 1f)
    }

    // Orbs and a Bayer tile at once. Neither alone gives both a shape and a texture: the orb
    // decides where ink gathers, the matrix decides how it breaks up.
    val ORB_MATRIX = Modulation {
        val dot = orbField(u + phase, v, orb, 0f)
        val matrix = OrderedScreens.BAYER_8.thresholdAt(floor(x).toInt(), floor(y).toInt())
        (dot * 0.6f + matrix * 0.4f).coerceIn(0f, 1f)
    }

    // A one-dimensional Bayer sequence read along a wavy line rather than a straight one, so the
    // ordered texture keeps its evenness while the rows themselves ripple.
    val ORDERED_MODULATION = Modulation(periodLabel = "sequence length") {
        val ripple = 0.35f * sin(TAU * (v * 0.5f + phase)).toFloat()
        val along = floor((u + ripple) * period).toInt()
        BAYER_1D[Math.floorMod(along, BAYER_1D.size)]
    }

    /*
     * Glitch. Whole bands of rows torn sideways by a random amount, with the tear rolled per
     * band rather than per cell — damage arrives in blocks, and a per-cell roll would be noise,
     * which is a different style entirely.
     */
    val GLITCH = Modulation(periodLabel = "band height") {
        val band = floor(v * 2f).toInt()
        val roll = hash(band, 0, 17)
        val torn = if (roll < 0.35f) hash(band, 1, 23) * 2f - 1f else 0f
        frac(u + torn + phase)
    }

    val MOD_ORB = Modulation { orbField(u + phase, v, orb, 0f) }

    // Offsetting every other row by half a cell is what turns a square grid of orbs into a
    // honeycomb — the cells pack against each other instead of lining up. It is the same
    // machinery as the orb offset control, fixed at a half step.
    val BEEHIVE = Modulation { orbField(u + phase, v, orb, 0.5f) }
}

private const val TAU = 2.0 * PI

/**
 * 1/√2, which turns up below in both of its meanings: the distance from the centre of a unit
 * cell to its corner, and the sine and cosine of 45°. Naming it twice would suggest the styles
 * that rotate a lattice and the styles that normalise a radius disagree about the number.
 */
private const val ROOT_HALF = 0.70710678f

/**
 * A Bayer sequence in one dimension — the same recursive interleave, along a line.
 *
 * Eight steps, each as far as possible from the ones already placed, which is what gives an
 * ordered dither its evenness. Written out because a 1×8 case is not worth generating.
 */
private val BAYER_1D = floatArrayOf(
    0.0625f, 0.5625f, 0.3125f, 0.8125f, 0.1875f, 0.6875f, 0.4375f, 0.9375f,
)

/** The trade's process-colour screen angles, in the order cyan, magenta, yellow, black. */
private val SCREEN_ANGLES = intArrayOf(15, 75, 0, 45)

/** Fractional part, always in 0..1 — `x % 1` would go negative on the left half. */
private fun frac(x: Float): Float = x - floor(x)

private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1274126177
    h = (h xor (h shr 13)) * 1274126177
    return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
}

/**
 * The orb lattice: distance from the centre of the cell containing ([u], [v]), shaped by [orb].
 * Low in the middle, high at the edges — dark glyphs therefore clump into dots, which is the
 * halftone look.
 *
 * [rowShift] is applied on top of [OrbOptions.offset] and is what BEEHIVE uses to stagger its
 * rows; keeping it a parameter means the honeycomb and the offset slider are the same mechanism
 * rather than two that can disagree.
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
        // Jitter is keyed to the cell, not to the pixel, so a whole orb moves as one rather than
        // dissolving into noise.
        val jitter = orb.random / 100f * 0.5f
        dx += (hash(floor(shifted).toInt(), row, orb.seed) - 0.5f) * jitter
        dy += (hash(row, floor(shifted).toInt(), orb.seed + 71) - 0.5f) * jitter
    }

    val radius = (orb.size / 100f).coerceIn(0.02f, 1f)
    val distance = hypot(dx, dy) / ROOT_HALF / radius
    // Intensity is the hardness of the edge: at 100 the disc is flat and its rim abrupt, at 0
    // the orb is a smooth gradient with no rim at all.
    val hardness = (orb.intensity / 100f).coerceIn(0f, 1f)
    val soft = distance.coerceIn(0f, 1f)
    val hard = if (distance < 1f) 0f else 1f
    return (soft * (1f - hardness) + hard * hardness).coerceIn(0f, 1f)
}
