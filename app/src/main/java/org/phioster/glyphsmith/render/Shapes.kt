package org.phioster.glyphsmith.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The forms a band can draw. Presets store the name, so entries are only ever added. */
enum class ShapeKind(val label: String) {
    EMPTY("empty"),
    CIRCLE("circle"),
    RING("ring"),
    SQUARE("square"),
    DIAMOND("diamond"),
    TRIANGLE("triangle"),
    PLUS("plus"),
    CROSS("cross"),
    STAR4("4-point star"),
    STAR5("5-point star"),
    HEART("heart"),
    BAR("bar"),
}

/**
 * Every shape as closed outlines in a unit box (-1..1, y down), filled even-odd.
 *
 * One definition feeds the rasteriser, the SVG export and the editor's icons, so the three
 * cannot drift apart.
 */
object Shapes {

    private const val ROUND_STEPS = 48
    private const val HEART_STEPS = 40
    private const val ARM = 0.3f
    private const val SQUARE_HALF = 0.88f

    private val all = ShapeKind.entries.associateWith { build(it) }

    fun contours(kind: ShapeKind): List<FloatArray> = all.getValue(kind)

    /** Even-odd point test against [contours], in unit coordinates. */
    fun inside(contours: List<FloatArray>, x: Float, y: Float): Boolean {
        var odd = false
        for (c in contours) {
            val n = c.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = c[2 * i]
                val yi = c[2 * i + 1]
                val xj = c[2 * j]
                val yj = c[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) odd = !odd
                j = i
            }
        }
        return odd
    }

    /** The outlines as SVG path data, three decimals — enough for a unit box scaled to a cell. */
    fun pathData(kind: ShapeKind): String = buildString {
        contours(kind).forEach { c ->
            for (i in 0 until c.size / 2) {
                append(if (i == 0) 'M' else 'L')
                append(trim(c[2 * i])).append(' ').append(trim(c[2 * i + 1]))
            }
            append('Z')
        }
    }

    private fun trim(v: Float): String {
        val rounded = Math.round(v * 1000f) / 1000f
        return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
    }

    private fun build(kind: ShapeKind): List<FloatArray> = when (kind) {
        ShapeKind.EMPTY -> emptyList()
        ShapeKind.CIRCLE -> listOf(round(1f))
        ShapeKind.RING -> listOf(round(1f), round(0.6f))
        ShapeKind.SQUARE -> listOf(box(SQUARE_HALF, SQUARE_HALF))
        ShapeKind.DIAMOND -> listOf(floatArrayOf(0f, -1f, 1f, 0f, 0f, 1f, -1f, 0f))
        ShapeKind.TRIANGLE -> listOf(floatArrayOf(0f, -1f, 0.94f, 0.8f, -0.94f, 0.8f))
        ShapeKind.PLUS -> listOf(plus())
        ShapeKind.CROSS -> listOf(turned(plus(), PI / 4))
        ShapeKind.STAR4 -> listOf(star(4, 0.32f))
        ShapeKind.STAR5 -> listOf(star(5, 0.42f))
        ShapeKind.HEART -> listOf(heart())
        ShapeKind.BAR -> listOf(box(1f, 0.28f))
    }

    private fun box(halfW: Float, halfH: Float) =
        floatArrayOf(-halfW, -halfH, halfW, -halfH, halfW, halfH, -halfW, halfH)

    private fun round(radius: Float) = FloatArray(ROUND_STEPS * 2) { i ->
        val angle = 2.0 * PI * (i / 2) / ROUND_STEPS
        (if (i % 2 == 0) cos(angle) else sin(angle)).toFloat() * radius
    }

    private fun plus() = floatArrayOf(
        -ARM, -1f, ARM, -1f, ARM, -ARM, 1f, -ARM, 1f, ARM, ARM, ARM,
        ARM, 1f, -ARM, 1f, -ARM, ARM, -1f, ARM, -1f, -ARM, -ARM, -ARM,
    )

    /** Points alternate between the rim and [inner]; the first point is straight up. */
    private fun star(points: Int, inner: Float) = FloatArray(points * 4) { i ->
        val k = i / 2
        val angle = -PI / 2 + k * PI / points
        val radius = if (k % 2 == 0) 1f else inner
        (if (i % 2 == 0) cos(angle) else sin(angle)).toFloat() * radius
    }

    /** The classic parametric heart, shifted and scaled into the unit box. */
    private fun heart() = FloatArray(HEART_STEPS * 2) { i ->
        val t = 2.0 * PI * (i / 2) / HEART_STEPS
        if (i % 2 == 0) {
            (16.0 * sin(t) * sin(t) * sin(t) / 17.0).toFloat()
        } else {
            val up = 13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t)
            ((-up - 2.5) / 14.5).toFloat()
        }
    }

    private fun turned(points: FloatArray, angle: Double): FloatArray {
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        return FloatArray(points.size) { i ->
            val x = points[i - i % 2]
            val y = points[i - i % 2 + 1]
            if (i % 2 == 0) x * c - y * s else x * s + y * c
        }
    }
}
