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
    ROUNDED_SQUARE("rounded square"),
    HEXAGON("hexagon"),
    OCTAGON("octagon"),
    SQUARE_RING("square ring"),
    TRIANGLE_DOWN("triangle down"),
    TRIANGLE_LEFT("triangle left"),
    TRIANGLE_RIGHT("triangle right"),
    LINES("lines"),
    DOTS("four dots"),
    PIXEL_HEART("pixel heart"),

    /** Bayer 4×4 tiles: stack them across the bands and the bands become a dither of your own. */
    BAYER_13("bayer 13%"),
    BAYER_38("bayer 38%"),
    BAYER_63("bayer 63%"),
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

    // Lazy, because the outlines are built from tables declared further down this object.
    private val all by lazy { ShapeKind.entries.associateWith { build(it) } }

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
        ShapeKind.TRIANGLE -> listOf(TRIANGLE)
        ShapeKind.PLUS -> listOf(plus())
        ShapeKind.CROSS -> listOf(turned(plus(), PI / 4))
        ShapeKind.STAR4 -> listOf(star(4, 0.32f))
        ShapeKind.STAR5 -> listOf(star(5, 0.42f))
        ShapeKind.HEART -> listOf(heart())
        ShapeKind.BAR -> listOf(box(1f, 0.28f))
        ShapeKind.ROUNDED_SQUARE -> listOf(roundedSquare())
        ShapeKind.HEXAGON -> listOf(regular(6, -PI / 2))
        ShapeKind.OCTAGON -> listOf(regular(8, PI / 8))
        ShapeKind.SQUARE_RING -> listOf(box(SQUARE_HALF, SQUARE_HALF), box(0.5f, 0.5f))
        ShapeKind.TRIANGLE_DOWN -> listOf(turned(TRIANGLE, PI))
        ShapeKind.TRIANGLE_LEFT -> listOf(turned(TRIANGLE, -PI / 2))
        ShapeKind.TRIANGLE_RIGHT -> listOf(turned(TRIANGLE, PI / 2))
        ShapeKind.LINES -> listOf(-0.66f, 0f, 0.66f).map { moved(box(1f, 0.17f), 0f, it) }
        ShapeKind.DOTS -> listOf(-0.5f, 0.5f).flatMap { y -> listOf(-0.5f, 0.5f).map { x -> moved(round(0.3f), x, y) } }
        ShapeKind.PIXEL_HEART -> tiles(HEART_PIXELS)
        ShapeKind.BAYER_13 -> bayerTile(2)
        ShapeKind.BAYER_38 -> bayerTile(6)
        ShapeKind.BAYER_63 -> bayerTile(10)
    }

    private val TRIANGLE = floatArrayOf(0f, -1f, 0.94f, 0.8f, -0.94f, 0.8f)
    private val HEART_PIXELS = listOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    private fun regular(sides: Int, start: Double) = FloatArray(sides * 2) { i ->
        val angle = start + 2.0 * PI * (i / 2) / sides
        (if (i % 2 == 0) cos(angle) else sin(angle)).toFloat()
    }

    /** A square whose corners are quarter circles a third of its half-width across. */
    private fun roundedSquare(): FloatArray {
        val steps = 6
        val bend = 0.3f
        val reach = SQUARE_HALF - bend
        val out = ArrayList<Float>()
        for (corner in 0 until 4) {
            val cx = if (corner == 0 || corner == 3) reach else -reach
            val cy = if (corner < 2) reach else -reach
            for (k in 0..steps) {
                val angle = corner * PI / 2 + k * PI / 2 / steps
                out += cx + bend * cos(angle).toFloat()
                out += cy + bend * sin(angle).toFloat()
            }
        }
        return out.toFloatArray()
    }

    private fun moved(points: FloatArray, dx: Float, dy: Float) =
        FloatArray(points.size) { i -> points[i] + if (i % 2 == 0) dx else dy }

    /** One square per X in [rows], the picture centred and fitted to the box by its longer side. */
    private fun tiles(rows: List<String>): List<FloatArray> {
        val cols = rows.maxOf { it.length }
        val step = 2f / maxOf(cols, rows.size)
        val left = -cols * step / 2f
        val top = -rows.size * step / 2f
        return rows.flatMapIndexed { r, line ->
            line.indices.filter { line[it] == 'X' }.map { c ->
                moved(box(step / 2f, step / 2f), left + (c + 0.5f) * step, top + (r + 0.5f) * step)
            }
        }
    }

    /** The cells of a 4×4 Bayer matrix that are on below [level], as squares filling the box. */
    private fun bayerTile(level: Int): List<FloatArray> =
        tiles((0 until 4).map { r -> (0 until 4).joinToString("") { c -> if (BAYER[r * 4 + c] < level) "X" else "." } })

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
