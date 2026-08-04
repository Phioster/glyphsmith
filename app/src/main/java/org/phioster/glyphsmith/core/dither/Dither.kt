package org.phioster.glyphsmith.core.dither

import kotlin.math.floor
import kotlin.math.roundToInt

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
     * The orb family's own controls, mirroring the six sliders the reference app's orb styles
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
 * What the render loop asks a style about, applied to the *cell grid* rather than to pixels —
 * the cell is the smallest thing this app can draw, so that is the resolution a threshold is
 * read at and an error is spread across.
 *
 * The algorithms themselves no longer live here. Each is declared beside its own explanation —
 * in [DiffusionKernels], [OrderedScreens] and [ModulationSurfaces] — and reached through the
 * provider that carries it. What is left is the set of questions the loop needs answered, which
 * is a much smaller thing than the twelve `when (mode)` dispatches it replaced.
 */
object Dither {

    /**
     * A normalised value to a level index, before any offset.
     *
     * Lives here rather than in a renderer because every algorithm in this package needs it and
     * none of them should have to reach into a render path to get it — that was the import that
     * used to tie the dither core to the glyph engine.
     */
    fun quantise(value: Float, levels: Int): Int {
        if (levels <= 1) return 0
        return (value.coerceIn(0f, 1f) * (levels - 1)).roundToInt()
    }

    /**
     * The algorithm behind [mode].
     *
     * Everything below that used to switch over the enum asks this instead. The lookup is an
     * array index and the declarations are shared, so a per-cell caller pays nothing for going
     * through the provider — see [DitherProviders.of].
     */
    private fun algorithmOf(mode: DitherMode): DitherAlgorithm =
        DitherProviders.of(mode).algorithm

    /**
     * Matrix-driven modes: the threshold comes from a fixed tile.
     *
     * Deliberately the declared kind rather than `matrix(mode) != null`: the picker asks this
     * for every mode as it draws, and the generated matrices are built on first use. Going
     * through [matrix] would generate every blue-noise mask just to open a dropdown.
     */
    fun isOrdered(mode: DitherMode): Boolean = algorithmOf(mode) is OrderedMatrix

    /** Modes whose threshold is a continuous function of position rather than a tile. */
    fun isModulation(mode: DitherMode): Boolean = algorithmOf(mode) is Modulation

    /** Styles that read the cell's brightness as well as its position. */
    fun isContentAware(mode: DitherMode): Boolean =
        (algorithmOf(mode) as? Modulation)?.readsContent == true

    /** What the pattern-size slider is actually called for this style. */
    fun periodLabel(mode: DitherMode): String = algorithmOf(mode).periodLabel

    /** Styles that use the second axis, and what it means there. */
    fun densityLabel(mode: DitherMode): String? = algorithmOf(mode).densityLabel

    /**
     * Every mode that picks its glyph from a threshold at ([x], [y]) instead of by passing
     * an error on to its neighbours. Bayer and the modulation family differ only in where
     * that threshold comes from, so the engine treats them the same way.
     */
    fun isThresholdBased(mode: DitherMode): Boolean = isOrdered(mode) || isModulation(mode)

    /** The tile [mode] reads its threshold off, or null when it is not a matrix style at all. */
    fun matrix(mode: DitherMode): Array<IntArray>? = (algorithmOf(mode) as? OrderedMatrix)?.matrix

    /** Normalised threshold in 0..1 for the cell at ([x], [y]). */
    fun orderedThreshold(mode: DitherMode, x: Int, y: Int): Float =
        (algorithmOf(mode) as? OrderedMatrix)?.thresholdAt(x, y) ?: 0.5f

    /**
     * Threshold in 0..1 for any threshold-based style, with [options] applied.
     *
     * Both families go through here so that [PatternOptions.scale] — the pattern-size control
     * that is deliberately independent of the cell size — reaches an ordered screen too.
     *
     * [value] is the cell's own brightness in 0..1, and only the styles that declared themselves
     * content-aware do anything with it. What each family does with the position is the
     * algorithm's own business: a screen tiles it, a surface is a function of it.
     */
    fun threshold(
        mode: DitherMode,
        x: Int,
        y: Int,
        value: Float,
        options: PatternOptions,
    ): Float {
        val factor = (options.scale / 100f).coerceAtLeast(0.01f)
        return when (val algorithm = algorithmOf(mode)) {
            // Floored rather than rounded: rounding would make the tile stutter by a cell every
            // time the scaled coordinate crosses a half-step.
            is OrderedMatrix ->
                algorithm.thresholdAt(floor(x / factor).toInt(), floor(y / factor).toInt())

            is Modulation ->
                algorithm.thresholdAt(ModulationCell(x / factor, y / factor, value, options, factor))

            else -> 0.5f
        }
    }

    /**
     * Where a cell's error goes, or nothing at all for a style that does not diffuse.
     *
     * The kernels themselves are declared per style in [DiffusionKernels]. A style that resolves
     * the whole grid up front carries none even where it plainly diffuses — there are no taps
     * for the row loop to read, because the row loop never runs.
     */
    fun diffusionKernel(mode: DitherMode): List<DiffusionTap> =
        (algorithmOf(mode) as? ErrorDiffusion)?.taps ?: emptyList()

    /** How many rows below the current one a kernel reaches — the error buffer's depth. */
    fun kernelDepth(mode: DitherMode): Int =
        (algorithmOf(mode) as? ErrorDiffusion)?.depth ?: 1

    /**
     * Whether this mode's weights change with the value being quantised.
     *
     * Every classic kernel is a constant: Floyd–Steinberg spreads 7/16 to the right whether
     * the cell is nearly white or nearly black. Ostromoukhov's does not — his whole result
     * comes from picking different weights per input level, so his kernel cannot be hoisted
     * out of the loop the way the others are.
     */
    fun hasVariableKernel(mode: DitherMode): Boolean =
        (algorithmOf(mode) as? ErrorDiffusion)?.varies == true

    /** The kernel for a cell of brightness [value] in 0..1, or null if [mode] uses a constant. */
    fun variableKernel(mode: DitherMode, value: Float): List<DiffusionTap>? =
        (algorithmOf(mode) as? ErrorDiffusion)?.kernelFor(value)

    /**
     * Whether this mode decides every cell up front instead of along the rows.
     *
     * Some algorithms simply do not visit pixels in reading order. Riemersma follows a
     * space-filling curve; dot diffusion goes by class number, so it may touch the bottom
     * right corner before the top left. Rather than bend the main loop around them, they
     * hand back a finished grid of glyph indices and the loop just reads it.
     */
    fun isPrecomputed(mode: DitherMode): Boolean = algorithmOf(mode) is Precomputed

    /**
     * Styles that flatten an area rather than threshold a cell.
     *
     * They average a tile's brightness and give every cell in it the same glyph, so what
     * comes out is a coarser picture rather than a texture laid over the original one. See
     * [Regions].
     */
    fun isRegion(mode: DitherMode): Boolean =
        (algorithmOf(mode) as? Precomputed)?.flattensRegions == true
}
