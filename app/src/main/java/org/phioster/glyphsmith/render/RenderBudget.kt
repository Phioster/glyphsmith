package org.phioster.glyphsmith.render

import kotlin.math.ceil
import kotlin.math.max

/**
 * How large a render may get, and how coarse it has to be to stay inside that.
 *
 * All three render modules answer the same two questions before they do anything else — how
 * many source pixels one cell covers, and how many output pixels one cell becomes — and they
 * have to answer them identically, because a preview and an export that disagreed about either
 * would not be the same picture at two sizes. The answers were private to the pipeline and the
 * ceiling was a constant on the glyph renderer, which meant the pixel path read its output
 * limit out of the glyph module. Neither of those facts was about glyphs.
 */
object RenderBudget {

    /** Hard ceiling on either output dimension — beyond this a bitmap allocation fails. */
    const val MAX_OUTPUT_SIDE = 8192

    /**
     * The cell size actually used, which may be coarser than asked for.
     *
     * A cell size of one on a 4000-pixel source would mean a 4000-cell grid, and no preview
     * budget can hold that. Coarsening the cell is the only lever — the grid *is* the output
     * resolution in this mode — so the requested size is a floor, not a promise.
     */
    fun cellFor(width: Int, height: Int, requested: Int, maxSide: Int): Int {
        val budget = maxSide.coerceAtLeast(1)
        val minimum = ceil(max(width, height).toFloat() / budget).toInt().coerceAtLeast(1)
        return max(requested.coerceAtLeast(1), minimum)
    }

    /**
     * How many output pixels one dithered cell becomes.
     *
     * Zero — the default, and what every stored preset has — keeps the original behaviour: blocks
     * grow to fill the budget, the same way glyphs do in the other mode, so both modes produce
     * comparably sized output for the same settings. A pinned value separates *how coarsely the
     * image was sampled* from *how large that is drawn*, which used to be one number: a chunky
     * dither could only ever be a small image. Still clamped to the budget, because a preview that
     * ignored it would stop being a preview.
     */
    fun blockFor(params: RenderSettings, cols: Int, rows: Int, maxSide: Int): Int {
        val fill = (maxSide / max(cols, rows)).coerceAtLeast(1)
        val pinned = params.pixelBlock
        return if (pinned <= 0) fill else pinned.coerceIn(1, fill.coerceAtLeast(1))
    }
}
