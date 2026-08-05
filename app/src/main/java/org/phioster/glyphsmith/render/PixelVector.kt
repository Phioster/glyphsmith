package org.phioster.glyphsmith.render

/**
 * A dithered grid as vector rectangles.
 *
 * The naive export is one shape per cell, and it does not survive contact with anything: a
 * 640-wide image at cell size 2 is over a hundred thousand cells, so a circle each is a file no
 * plotter will open and no editor will draw. What makes a vector export of a dither useful is
 * therefore not the writing — it is the *merging* that happens first.
 *
 * Adjacent cells that landed on the same colour become one rectangle. That is not only a size
 * trick: a cutting plotter, a laser or an embroidery machine wants connected regions, and a
 * hundred thousand separate squares is a hundred thousand separate cuts.
 *
 * Greedy and rectangular rather than a true contour trace. A run is taken as far right as the
 * colour holds, then extended downwards for as long as the whole run below matches — the classic
 * two-pass merge. It cannot describe an L-shaped region as one shape, which a contour tracer
 * could; what it gives instead is an exact tiling with no overlaps, produced in one pass over
 * the grid, and an SVG that any tool can read.
 */
object PixelVector {

    /** One merged block, measured in *cells*. The caller scales to whatever a cell is worth. */
    data class Block(val col: Int, val row: Int, val cols: Int, val rows: Int, val colour: Int)

    /**
     * [colours], one per cell, merged into as few blocks as this scheme can manage.
     *
     * The result tiles the grid exactly: every cell is covered by one block and no two blocks
     * overlap. A test says so, because an export that quietly drops a row is an export nobody
     * notices is wrong until it has been cut into something.
     */
    fun merge(colours: IntArray, cols: Int, rows: Int): List<Block> {
        require(colours.size == cols * rows) { "expected ${cols * rows} cells, got ${colours.size}" }
        if (cols <= 0 || rows <= 0) return emptyList()

        val taken = BooleanArray(cols * rows)
        val blocks = ArrayList<Block>()

        for (row in 0 until rows) {
            var col = 0
            while (col < cols) {
                val at = row * cols + col
                if (taken[at]) {
                    col++
                    continue
                }
                val colour = colours[at]

                // As far right as the colour holds.
                var width = 1
                while (col + width < cols &&
                    !taken[at + width] &&
                    colours[at + width] == colour
                ) {
                    width++
                }

                // Then down, but only while the *whole* run matches — a partial match would
                // leave a ragged edge that the next row has to pick up anyway.
                var height = 1
                while (row + height < rows) {
                    val below = (row + height) * cols + col
                    var matches = true
                    for (i in 0 until width) {
                        if (taken[below + i] || colours[below + i] != colour) {
                            matches = false
                            break
                        }
                    }
                    if (!matches) break
                    height++
                }

                for (r in 0 until height) {
                    val start = (row + r) * cols + col
                    for (i in 0 until width) taken[start + i] = true
                }
                blocks += Block(col, row, width, height, colour)
                col += width
            }
        }
        return blocks
    }

    /**
     * [blocks] as an SVG document, at [cell] pixels per cell.
     *
     * Three things keep the file to a size a tool will open, and they were all found by
     * measuring rather than by guessing:
     *
     * The **merge** above, which is most of it.
     *
     * **Grouping by colour**, so the fill is written once per colour rather than once per
     * rectangle — on a two-level dither the colour attribute *was* the file.
     *
     * And the **ground**: the colour that covers the most cells is painted once across the whole
     * canvas and its blocks are then not drawn at all. On a two-level dither that is half the
     * rectangles for one extra shape. It also produces the better document for a cutter or a
     * plotter, where one colour is the material and the others are what is done to it.
     *
     * The blocks themselves stay an exact tiling; this is only what the writer chooses to draw.
     *
     * The `shape-rendering` hint stops a viewer antialiasing the seams between neighbours into
     * visible hairlines — the one thing that makes a correct tiling look broken on screen.
     */
    fun svg(blocks: List<Block>, cols: Int, rows: Int, cell: Int): String {
        val width = cols * cell
        val height = rows * cell
        val ground = blocks
            .groupBy { it.colour }
            .maxByOrNull { (_, group) -> group.sumOf { it.cols * it.rows } }
            ?.key
        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
            append(width).append("\" height=\"").append(height)
            append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
            append("\" shape-rendering=\"crispEdges\">\n")
            if (ground != null) {
                append("<rect width=\"").append(width).append("\" height=\"").append(height)
                append("\" fill=\"").append(hexOf(ground)).append("\"/>\n")
            }
            blocks.filterNot { it.colour == ground }.groupBy { it.colour }.forEach { (colour, group) ->
                append("<g fill=\"").append(hexOf(colour)).append("\">")
                group.forEach { block ->
                    append("<rect x=\"").append(block.col * cell)
                    append("\" y=\"").append(block.row * cell)
                    append("\" width=\"").append(block.cols * cell)
                    append("\" height=\"").append(block.rows * cell)
                    append("\"/>")
                }
                append("</g>\n")
            }
            append("</svg>\n")
        }
    }

    /** `#rrggbb`. Alpha is dropped: a cut path has no opacity, and neither does an ink. */
    private fun hexOf(colour: Int): String =
        "#%06X".format(colour and 0xFFFFFF)
}
