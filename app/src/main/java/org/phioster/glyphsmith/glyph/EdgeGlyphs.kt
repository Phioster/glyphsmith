package org.phioster.glyphsmith.glyph

import kotlin.math.PI

/** A named set of glyphs, one per edge direction, ordered from horizontal upwards. */
data class EdgeSet(val id: String, val name: String, val glyphs: String)

/**
 * How a detected edge is drawn: the sets, and which glyph a direction picks.
 *
 * The detection is [org.phioster.glyphsmith.render.EdgeDetect] and is shared — it is a Sobel
 * over the cell grid and knows nothing about characters. This is the answer to that field, and
 * it is only ever an answer Glyph Art gives: the pixel modules have no glyph to override a cell
 * with, so a directional line is not something they can draw at all. Both halves used to sit in
 * the shared file, which put a table of box-drawing characters inside the render core.
 *
 * [RenderSettings.edgeSetId][org.phioster.glyphsmith.render.RenderSettings.edgeSetId] stays
 * where it is: it is a stored settings value, and the ids below are what it holds.
 */
object EdgeGlyphs {

    val sets: List<EdgeSet> = listOf(
        EdgeSet("ascii", "ASCII", "-/|\\"),
        EdgeSet("box", "Box Drawing", "─╱│╲"),
        EdgeSet("blocks", "Block Corners", "▄▖▌▘"),
        EdgeSet("heavy", "Heavy", "━╱┃╲"),
    )

    private val byId = sets.associateBy { it.id }

    fun setById(id: String): EdgeSet = byId[id] ?: sets.first()

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
