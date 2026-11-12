package org.phioster.glyphsmith.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * Flow ASCII as real text and real strokes: every character a turned `<text>`, every hair a line.
 *
 * Marks of one colour share a group, so the colour is written once; a fur picture has tens of
 * thousands of hairs, and all those of one colour go into a single path.
 */
object FlowVector {

    private const val RGB = 0xFFFFFF
    private const val DEGREES = 57.29578f
    private const val TEXT_SHARE = 1.15f
    private const val HAIR_WIDTH = 0.5f

    /** The SVG and how many marks it holds. One SVG unit is one cell of the working picture. */
    fun svg(marks: FlowMarks, params: RenderSettings): Pair<String, Int> {
        val byColour = (0 until marks.count).groupBy { marks.color[it] and RGB }
        val text = buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(marks.cols)
            append("\" height=\"").append(marks.rows)
            append("\" viewBox=\"0 0 ").append(marks.cols).append(' ').append(marks.rows).append("\">\n")
            if (!params.transparentBackground) {
                append("<rect width=\"").append(marks.cols).append("\" height=\"").append(marks.rows)
                append("\" fill=\"").append(hex(params.backgroundColor)).append("\"/>\n")
            }
            append("<g font-family=\"monospace\" font-weight=\"bold\" font-size=\"")
            append(round(marks.size * TEXT_SHARE))
            append("\" text-anchor=\"middle\" dominant-baseline=\"central\" stroke-width=\"")
            append(HAIR_WIDTH).append("\" stroke-linecap=\"round\">\n")
            byColour.forEach { (colour, indices) ->
                val hairs = indices.filter { marks.glyph[it] == FlowMarks.HAIR }
                if (hairs.isNotEmpty()) {
                    append("<path fill=\"none\" stroke=\"").append(hex(colour)).append("\" d=\"")
                    hairs.forEach { i ->
                        val dx = cos(marks.angle[i]) * marks.half[i]
                        val dy = sin(marks.angle[i]) * marks.half[i]
                        append('M').append(round(marks.x[i] - dx)).append(' ').append(round(marks.y[i] - dy))
                        append('L').append(round(marks.x[i] + dx)).append(' ').append(round(marks.y[i] + dy))
                    }
                    append("\"/>\n")
                }
                val letters = indices.filter { marks.glyph[it] != FlowMarks.HAIR }
                if (letters.isNotEmpty()) {
                    append("<g fill=\"").append(hex(colour)).append("\">")
                    letters.forEach { i ->
                        val x = round(marks.x[i])
                        val y = round(marks.y[i])
                        append("<text x=\"").append(x).append("\" y=\"").append(y).append('"')
                        val turn = marks.angle[i] * DEGREES
                        if (turn != 0f) {
                            append(" transform=\"rotate(").append(round(turn)).append(' ').append(x).append(' ').append(y).append(")\"")
                        }
                        append('>').append(escaped(marks.glyph[i])).append("</text>")
                    }
                    append("</g>\n")
                }
            }
            append("</g>\n</svg>\n")
        }
        return text to marks.count
    }

    /** The three characters XML reads as markup. */
    private fun escaped(glyph: Char): String = when (glyph) {
        '<' -> "&lt;"
        '>' -> "&gt;"
        '&' -> "&amp;"
        else -> glyph.toString()
    }

    private fun hex(colour: Int): String = "#%06x".format(colour and RGB)

    private fun round(v: Float): String = (Math.round(v * 100f) / 100f).toString()
}
