package org.phioster.glyphsmith.render

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** Gradient magnitude and direction per cell. */
class EdgeField(
    val magnitude: FloatArray,
    val angle: FloatArray,
    val cols: Int,
    val rows: Int,
) {
    fun magnitudeAt(col: Int, row: Int): Float = magnitude[row * cols + col]
    fun angleAt(col: Int, row: Int): Float = angle[row * cols + col]
}

/** A named set of glyphs, one per edge direction, ordered from horizontal upwards. */
data class EdgeSet(val id: String, val name: String, val glyphs: String)

/**
 * Sobel over the **cell grid**, not the source pixels.
 *
 * The cell grid is what actually gets drawn, and it is only `cols × rows` values — running
 * the operator there costs nothing measurable, where running it on a 2048² source would be
 * the most expensive thing in the pipeline. It also means the detected edge is at exactly
 * the resolution the glyph can express.
 */
object EdgeDetect {

    val sets: List<EdgeSet> = listOf(
        EdgeSet("ascii", "ASCII", "-/|\\"),
        EdgeSet("box", "Box Drawing", "─╱│╲"),
        EdgeSet("blocks", "Block Corners", "▄▖▌▘"),
        EdgeSet("heavy", "Heavy", "━╱┃╲"),
    )

    private val byId = sets.associateBy { it.id }

    fun setById(id: String): EdgeSet = byId[id] ?: sets.first()

    fun sobel(luma: FloatArray, cols: Int, rows: Int): EdgeField {
        val magnitude = FloatArray(cols * rows)
        val angle = FloatArray(cols * rows)

        fun at(x: Int, y: Int): Float =
            luma[y.coerceIn(0, rows - 1) * cols + x.coerceIn(0, cols - 1)]

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val tl = at(x - 1, y - 1)
                val tc = at(x, y - 1)
                val tr = at(x + 1, y - 1)
                val ml = at(x - 1, y)
                val mr = at(x + 1, y)
                val bl = at(x - 1, y + 1)
                val bc = at(x, y + 1)
                val br = at(x + 1, y + 1)

                val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)

                val index = y * cols + x
                // Divided by 4 so a full black/white step lands at magnitude 1.
                magnitude[index] = (sqrt(gx * gx + gy * gy) / 4f).coerceIn(0f, 1f)
                angle[index] = atan2(gy, gx)
            }
        }
        return EdgeField(magnitude, angle, cols, rows)
    }

    /**
     * Glyph for a gradient direction. The *edge* runs perpendicular to the gradient, so the
     * angle is rotated by 90° before it is bucketed — otherwise every line would be drawn
     * across itself.
     */
    fun glyphFor(gradientAngle: Float, set: EdgeSet): Char {
        val glyphs = set.glyphs
        if (glyphs.isEmpty()) return ' '
        val edgeAngle = gradientAngle + (PI / 2).toFloat()
        // Direction is mod 180°: a line at 10° and one at 190° look identical.
        val normalised = ((edgeAngle % PI.toFloat()) + PI.toFloat()) % PI.toFloat()
        val bucket = ((normalised / PI.toFloat()) * glyphs.length + 0.5f).toInt() % glyphs.length
        return glyphs[bucket]
    }
}
