package org.phioster.glyphsmith.core.dither

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The name the pattern-size control carries unless a style renames it.
 *
 * Top level rather than tucked into the base class so that the declarations below can use it as
 * a default argument without qualifying it, which is where it is read eighty times.
 */
const val DEFAULT_PERIOD_LABEL = "period"

/**
 * What one dither algorithm is, mechanically.
 *
 * Until now the answer was spread across a dozen `when (mode)` dispatches in [Dither], each
 * naming its own subset of the eighty styles. Adding a style meant remembering all twelve, and
 * a style could belong to two of them at once — or to none — and still compile. Here it is
 * declared once, as one kind of thing, and the kind is what the rest of the app asks about.
 *
 * The kinds are mechanisms rather than looks. Two styles that draw nothing alike are the same
 * kind if they decide a cell the same way, and that is the distinction the render loop actually
 * needs: whether to compare a threshold, to pass an error on, or to stand aside and let the
 * algorithm resolve the whole grid by itself.
 *
 * This is deliberately not the same axis as [DitherCategory]. That one is the shelf a style is
 * filed under for someone browsing; this one is how it runs. A glitch and a halftone can share
 * a mechanism, and two ordered matrices can sit on different shelves.
 */
sealed class DitherAlgorithm(
    /**
     * What the pattern-size control is called for this style.
     *
     * The storage is one field; the meaning is not. Labelling it "period" while it sets a dot
     * size is the kind of small dishonesty that makes a panel feel arbitrary.
     */
    val periodLabel: String,
    /** What the second axis means here, or null when the style has no use for one. */
    val densityLabel: String?,
)

/** The absence of dithering: a cell's level is whatever its brightness rounds to. */
object NoDither : DitherAlgorithm(DEFAULT_PERIOD_LABEL, null)

/**
 * A threshold read off a fixed tile, repeating across the grid.
 *
 * Bayer, the clustered-dot screens and the blue-noise masks differ only in what is in the tile.
 *
 * The tile is built on first use rather than at declaration: a 32×32 blue-noise mask costs real
 * time to generate, and a style is asked what kind of thing it is far more often than it is
 * asked to draw — every time the picker lists it, for one.
 */
class OrderedMatrix(build: () -> Array<IntArray>) : DitherAlgorithm(DEFAULT_PERIOD_LABEL, null) {

    val matrix: Array<IntArray> by lazy(build)

    /** Normalised threshold in 0..1 for the cell at ([x], [y]), tiling in both directions. */
    fun thresholdAt(x: Int, y: Int): Float {
        val n = matrix.size
        val value = matrix[Math.floorMod(y, n)][Math.floorMod(x, n)]
        return (value + 0.5f) / (n * n)
    }
}

/**
 * A threshold that is a continuous function of position rather than a tile.
 *
 * [readsContent] marks the styles that read the cell's brightness as well as its position. The
 * distinction is worth naming rather than leaving implicit: a pattern that ignores the picture
 * tiles the same way over a face and over a blank wall; one that reads it puts its ink where the
 * image is dark, which is what a pen does. Everything else must keep ignoring it, and a test
 * enforces exactly this split.
 *
 * The surface is written against [ModulationCell] as its receiver, so a style reads `u`, `phase`
 * and `density` directly and never has to unpack a parameter list it mostly ignores.
 */
class Modulation(
    periodLabel: String = DEFAULT_PERIOD_LABEL,
    densityLabel: String? = null,
    val readsContent: Boolean = false,
    private val surface: ModulationCell.() -> Float,
) : DitherAlgorithm(periodLabel, densityLabel) {

    fun thresholdAt(cell: ModulationCell): Float = cell.surface()
}

/**
 * A threshold that is a function of position, *and* an error passed on to the neighbours.
 *
 * The two families it is made of answer different questions. A [Modulation] decides a cell by
 * where it is; an [ErrorDiffusion] decides it by what the cells before it could not represent.
 * Doing both is not the average of the two looks, which is why it is worth its own kind: the
 * surface alone lays its pattern over the picture and the picture loses, while the diffused
 * residue keeps loosening the pattern's grip just enough that the image survives inside it. The
 * lines bend around what is in the frame instead of running straight through it.
 *
 * Composed rather than reimplemented. It holds a real [Modulation] and a real [ErrorDiffusion]
 * and asks them, so a surface behaves identically whether it is used alone or here, and a
 * kernel keeps its declared depth and its variable weights.
 *
 * The labels come from the surface, because that is what the pattern controls are steering.
 */
class ModulatedDiffusion(
    val surface: Modulation,
    val diffusion: ErrorDiffusion,
) : DitherAlgorithm(surface.periodLabel, surface.densityLabel)

/**
 * Everything a modulation surface is allowed to know about the cell it is thresholding.
 *
 * The rotation and the scaling happen once, here, rather than in each of the thirty-odd styles:
 * they all measure position the same way and differ only in what they do with it. What a style
 * may *not* see is the whole of [PatternOptions] — the panel's storage is not the algorithm's
 * input, and keeping the two apart is what stops a style quietly reading a control that its
 * declaration never claimed.
 */
class ModulationCell(
    /** The cell's coordinate, already divided by the pattern scale. */
    val x: Float,
    val y: Float,
    /**
     * The cell's own brightness in 0..1.
     *
     * Every style that existed before the catalogue arrived ignores it, and a test holds them to
     * that. It is here for the ones that cannot work without it: a crosshatch whose lines
     * thicken in the shadows, a stipple whose dots crowd where the image is dark.
     */
    val value: Float,
    options: PatternOptions,
    scale: Float,
) {
    /** Cells per period of the pattern. */
    val period: Float = options.period.coerceAtLeast(1).toFloat()

    /** 0..1 of a period; animating this makes the pattern travel. */
    val phase: Float = options.phase

    /** The second axis in 0..100, whose meaning is the style's own — see its density label. */
    val density: Int = options.density

    val orb: OrbOptions = options.orb

    /** An imported screen, or empty. Only `CUSTOM_SCREEN` reads it. */
    val screen: List<Int> = options.screen

    /**
     * The grid centre, scaled along with the coordinates.
     *
     * It is given in unscaled cells, so it has to travel with them — otherwise the rings would
     * crawl away from the image centre as the pattern scale changed.
     */
    val centerX: Float = options.centerX / scale
    val centerY: Float = options.centerY / scale

    /** The position rotated into the pattern's own frame, measured in periods rather than cells. */
    val u: Float
    val v: Float

    init {
        val radians = options.angle * PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        u = (x * c + y * s) / period
        v = (-x * s + y * c) / period
    }
}

/**
 * A cell's quantisation error, passed on to the neighbours that have not been decided yet.
 *
 * [taps] is the whole algorithm for almost all of them: what differs between the published
 * kernels is only how far and how evenly the error is spread. The few whose weights change with
 * the value being quantised declare [perValue] as well — Ostromoukhov's entire result comes from
 * picking different weights per input level, so his kernel cannot be hoisted out of the loop the
 * way the others are.
 */
class ErrorDiffusion(
    val taps: List<DiffusionTap>,
    private val perValue: ((Float) -> List<DiffusionTap>)? = null,
) : DitherAlgorithm(DEFAULT_PERIOD_LABEL, null) {

    /** Whether the weights change with the value being quantised. */
    val varies: Boolean = perValue != null

    /**
     * How many rows below the current one the kernel reaches — the error buffer's depth.
     *
     * Measured off [taps], which is why a mode with a [perValue] kernel must still declare a
     * representative one: the buffer is sized once, before any cell is known.
     */
    val depth: Int = (taps.maxOfOrNull { it.dy } ?: 0) + 1

    /**
     * The kernel for a cell of brightness [value] in 0..1, or null when the weights are constant.
     *
     * **Returns a cached list, never a fresh one.** This is asked once per cell, and a megapixel
     * image is a million allocations if it is answered carelessly.
     */
    fun kernelFor(value: Float): List<DiffusionTap>? = perValue?.invoke(value)
}

/**
 * A style that decides every cell up front instead of along the rows.
 *
 * Some algorithms simply do not visit cells in reading order. Riemersma follows a space-filling
 * curve; dot diffusion goes by class number, so it may touch the bottom right corner before the
 * top left. Rather than bend the main loop around them, they hand back a finished grid and the
 * loop just reads it.
 *
 * [flattensRegions] separates the ones that get there by averaging an area — a mosaic, a polygon
 * lattice — from the ones that walk an order of their own. Both refuse the row loop; only the
 * first produces a coarser picture rather than a texture laid over the original.
 */
class Precomputed(
    periodLabel: String = DEFAULT_PERIOD_LABEL,
    densityLabel: String? = null,
    val flattensRegions: Boolean = false,
) : DitherAlgorithm(periodLabel, densityLabel)
