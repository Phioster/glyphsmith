package org.phioster.glyphsmith.core.dither

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
 */
class OrderedMatrix : DitherAlgorithm(DEFAULT_PERIOD_LABEL, null)

/**
 * A threshold that is a continuous function of position rather than a tile.
 *
 * [readsContent] marks the styles that read the cell's brightness as well as its position. The
 * distinction is worth naming rather than leaving implicit: a pattern that ignores the picture
 * tiles the same way over a face and over a blank wall; one that reads it puts its ink where the
 * image is dark, which is what a pen does. Everything else must keep ignoring it, and a test
 * enforces exactly this split.
 */
class Modulation(
    periodLabel: String = DEFAULT_PERIOD_LABEL,
    densityLabel: String? = null,
    val readsContent: Boolean = false,
) : DitherAlgorithm(periodLabel, densityLabel)

/** A cell's quantisation error, passed on to the neighbours that have not been decided yet. */
class ErrorDiffusion : DitherAlgorithm(DEFAULT_PERIOD_LABEL, null)

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
