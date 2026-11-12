package org.phioster.glyphsmith.render

import kotlin.math.abs

/**
 * The oscilloscope as real lines: one path per visible stretch of a trace.
 *
 * What a plotter or a cutter wants, and what a print at any size wants. The light is left out —
 * a vector has no glow — so this is the drawing under the tube: the traces, with the ones in
 * front hiding the ones behind exactly as on screen.
 */
object ScopeVector {

    private const val RGB = 0xFFFFFF
    private const val TUBE = 0x030806
    private const val STRAIGHT = 0.08f

    /** The SVG and how many paths it holds. One SVG unit is one cell of the working picture. */
    fun svg(grid: CellGrid, params: RenderSettings): Pair<String, Int> {
        val runs = Oscilloscope.runs(grid, params)
        val unit = maxOf(grid.cols, grid.rows) / Oscilloscope.WORK_SIDE.toFloat()
        val text = buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(grid.cols)
            append("\" height=\"").append(grid.rows)
            append("\" viewBox=\"0 0 ").append(grid.cols).append(' ').append(grid.rows).append("\">\n")
            if (!params.transparentBackground) {
                append("<rect width=\"").append(grid.cols).append("\" height=\"").append(grid.rows)
                append("\" fill=\"").append(hex(TUBE)).append("\"/>\n")
            }
            append("<g fill=\"none\" stroke=\"").append(hex(params.oscilloscope.phosphor.rgb))
            append("\" stroke-width=\"").append(round(0.9f * unit))
            append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n")
            for (run in runs) {
                append("<path d=\"M")
                var kept = 0
                for (i in 0 until run.size / 2) {
                    // A point on the straight line between its neighbours says nothing.
                    if (i > 0 && i < run.size / 2 - 1) {
                        val before = run[2 * kept - 1]
                        val after = run[2 * i + 3]
                        if (abs(run[2 * i + 1] - before) < STRAIGHT && abs(after - run[2 * i + 1]) < STRAIGHT) continue
                    }
                    if (kept > 0) append('L')
                    append(round(run[2 * i])).append(' ').append(round(run[2 * i + 1]))
                    kept++
                }
                append("\"/>\n")
            }
            append("</g>\n</svg>\n")
        }
        return text to runs.size
    }

    private fun hex(colour: Int): String = "#%06x".format(colour and RGB)

    private fun round(v: Float): String = (Math.round(v * 100f) / 100f).toString()
}
