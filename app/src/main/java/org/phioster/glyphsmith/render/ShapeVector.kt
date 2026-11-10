package org.phioster.glyphsmith.render

/**
 * A [ShapeField] as real vectors: each shape is defined once and placed with a transform, so
 * the file stays small and a cutter or plotter gets true outlines instead of traced pixels.
 */
object ShapeVector {

    private const val RGB = 0xFFFFFF

    /** The SVG and how many shapes it holds. [cell] is the size of one grid cell in SVG units. */
    fun svg(field: ShapeField, cell: Int, params: RenderSettings): Pair<String, Int> {
        val width = field.cols * cell
        val height = field.rows * cell
        var count = 0
        val text = buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
            append("\" height=\"").append(height)
            append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n<defs>")
            field.shape.distinct().filter { it != ShapeKind.EMPTY }.forEach { kind ->
                append("<path id=\"s").append(kind.ordinal).append("\" fill-rule=\"evenodd\" d=\"")
                append(Shapes.pathData(kind)).append("\"/>")
            }
            append("</defs>\n")
            if (!params.transparentBackground) {
                append("<rect width=\"").append(width).append("\" height=\"").append(height)
                append("\" fill=\"").append(hex(params.backgroundColor)).append("\"/>\n")
            }
            for (i in field.order) {
                val radius = field.radius[i] * cell
                if (field.shape[i] == ShapeKind.EMPTY || radius <= 0f) continue
                count++
                append("<use href=\"#s").append(field.shape[i].ordinal)
                append("\" transform=\"translate(").append(round(field.x[i] * cell)).append(' ')
                append(round(field.y[i] * cell)).append(')')
                if (field.rotation != 0) append(" rotate(").append(field.rotation).append(')')
                append(" scale(").append(round(radius)).append(")\" fill=\"")
                append(hex(field.color[i])).append("\"/>\n")
            }
            append("</svg>\n")
        }
        return text to count
    }

    private fun hex(colour: Int): String = "#%06x".format(colour and RGB)

    private fun round(v: Float): String = (Math.round(v * 100f) / 100f).toString()
}
