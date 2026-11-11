package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the rows of [RenderMode.FlowAscii] are made of. */
enum class FlowStyle(val label: String) {
    /** Characters, turned to lie along the forms. */
    GLYPHS("characters"),

    /** Very many short hairs along the forms. */
    FUR("fur"),
}

@Serializable
data class FlowAsciiParams(
    val style: FlowStyle = FlowStyle.GLYPHS,
    /** 3..20 — a character's size, in cells of the working picture; fur ignores it. */
    val size: Int = 7,
    /** 50..200 % — how far apart the rows stand; 100 sets them shoulder to shoulder. */
    val spacing: Int = 100,
    /** Chooses the character by how the surface stands to the viewer as well as by its brightness. */
    val byTilt: Boolean = true,
    /** 0..40 % — brightness below which nothing is set, so black stays black. */
    val blackBelow: Int = 4,
    /** 25..300 % — how long the hairs of the fur are. */
    val hairLength: Int = 100,
    /** Redraws strong edges with line characters on the upright grid. */
    val outline: Boolean = false,
)

/**
 * The marks of a flow-ASCII picture: where each character or hair goes and which way it lies.
 * Positions are in cells of the working picture; drawing them is the module's business.
 */
class FlowMarks(
    val cols: Int,
    val rows: Int,
    val count: Int,
    val x: FloatArray,
    val y: FloatArray,
    /** Radians. */
    val angle: FloatArray,
    /** The character, or [HAIR] for a stroke of [half] cells to either side. */
    val glyph: CharArray,
    val half: FloatArray,
    val color: IntArray,
    /** Character size in cells. */
    val size: Float,
) {
    companion object {
        const val HAIR = '\u0000'
    }
}

/** Flow ASCII: rows of characters that follow the forms of the picture. Free of Android types. */
object FlowAscii {

    /** The longer side of the working picture. Fixed, so a preview and an export set the same marks. */
    const val WORK_SIDE = 1024

    // The character says how the surface stands to us; its row, which way the surface runs.
    private const val FACING = " .:o08S\$#@"
    private const val TURNED = " .:c()C0O"
    private const val GRAZING = " ..::--"
    private const val OUTLINE = "-\\|/"

    private const val TURNED_FROM = 0.2f
    private const val GRAZING_FROM = 0.55f
    private const val OUTLINE_FROM = 0.3f
    private const val ROW_SHARE = 0.7f
    private const val STEP_SHARE = 0.62f
    private const val FUR_SEP = 2.3f
    private const val FUR_STEP = 2.6f
    private const val OPAQUE = 0xFF000000.toInt()
    private const val PI = 3.1415927f

    /** The cell size that brings a source of this size down to the working picture. */
    fun cellFor(width: Int, height: Int): Int = max(1, (max(width, height) + WORK_SIDE - 1) / WORK_SIDE)

    fun marks(grid: CellGrid, params: RenderSettings): FlowMarks {
        val flow = params.flowAscii
        val field = FlowField.of(grid.luma, grid.cols, grid.rows)
        val palette = params.renderPalette().colors
        val spacing = flow.spacing.coerceIn(50, 200) / 100f
        val dark = flow.blackBelow.coerceIn(0, 40) / 100f
        val size = flow.size.coerceIn(3, 20).toFloat()
        val out = Builder(grid.cols, grid.rows, size)

        if (flow.style == FlowStyle.FUR) {
            val lines = Streamlines.trace(field, FUR_SEP * spacing, FUR_STEP)
            val reach = flow.hairLength.coerceIn(25, 300) / 100f
            for (i in 0 until lines.count) {
                val at = field.index(lines.x[i], lines.y[i])
                val tone = grid.luma[at].coerceIn(0f, 1f)
                if (tone < dark) continue
                // A hair is never quite in line: a little off, and its own length and shade.
                val turn = (hash(i, 1) - 0.5f) * 0.7f
                val length = (0.9f + 2.6f * tone) * (0.6f + 0.7f * hash(i, 2)) * reach
                out.add(
                    lines.x[i] + (hash(i, 3) - 0.5f) * 1.6f, lines.y[i] + (hash(i, 4) - 0.5f) * 1.6f,
                    lines.angle[i] + turn, FlowMarks.HAIR, length,
                    shaded(colourOf(at, tone, grid, params, palette), 0.7f + 0.45f * hash(i, 5)),
                )
            }
        } else {
            val lines = Streamlines.trace(field, size * ROW_SHARE * spacing, size * STEP_SHARE)
            for (i in 0 until lines.count) {
                val at = field.index(lines.x[i], lines.y[i])
                val tone = grid.luma[at].coerceIn(0f, 1f)
                if (tone < dark) continue
                val ramp = when {
                    !flow.byTilt -> FACING
                    field.tilt[at] > GRAZING_FROM -> GRAZING
                    field.tilt[at] > TURNED_FROM -> TURNED
                    else -> FACING
                }
                val glyph = ramp[min(ramp.length - 1, (tone * ramp.length).toInt())]
                if (glyph == ' ') continue
                out.add(lines.x[i], lines.y[i], lines.angle[i], glyph, 0f, colourOf(at, tone, grid, params, palette))
            }
        }
        if (flow.outline) outline(field, grid, params, palette, size, out)
        return out.build()
    }

    /** Strong edges once more, upright and on a grid: the line character that lies nearest the edge. */
    @Suppress("LongParameterList")
    private fun outline(field: FlowField, grid: CellGrid, params: RenderSettings, palette: List<Int>, size: Float, out: Builder) {
        var y = size / 2f
        while (y < grid.rows) {
            var x = size / 2f
            while (x < grid.cols) {
                val at = field.index(x, y)
                if (field.edge[at] >= OUTLINE_FROM) {
                    val turn = (field.angle[at] % PI + PI) % PI
                    val glyph = OUTLINE[((turn + PI / 8f) / (PI / 4f)).toInt() % OUTLINE.length]
                    out.add(x, y, 0f, glyph, 0f, colourOf(at, 1f, grid, params, palette))
                }
                x += size * 0.6f
            }
            y += size * 0.9f
        }
    }

    private fun colourOf(i: Int, tone: Float, grid: CellGrid, params: RenderSettings, palette: List<Int>): Int {
        val colour = when (params.colorMode) {
            ColorMode.SINGLE -> params.inkColor
            ColorMode.SOURCE -> grid.colors?.get(i) ?: params.inkColor
            ColorMode.PALETTE -> if (palette.isEmpty()) {
                params.inkColor
            } else {
                palette[(tone * (palette.size - 1) + 0.5f).toInt().coerceIn(0, palette.lastIndex)]
            }
        }
        return colour or OPAQUE
    }

    private fun shaded(color: Int, by: Float): Int {
        fun ch(shift: Int) = min(255, ((color shr shift and 0xFF) * by).toInt())
        return OPAQUE or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun hash(n: Int, salt: Int): Float {
        var v = n * 374761393 + salt * 668265263
        v = (v xor (v ushr 13)) * 1274126177
        return ((v xor (v ushr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }

    private class Builder(val cols: Int, val rows: Int, val size: Float) {
        private var n = 0
        private var x = FloatArray(4096)
        private var y = FloatArray(4096)
        private var angle = FloatArray(4096)
        private var glyph = CharArray(4096)
        private var half = FloatArray(4096)
        private var color = IntArray(4096)

        @Suppress("LongParameterList")
        fun add(px: Float, py: Float, a: Float, g: Char, h: Float, c: Int) {
            if (n == x.size) {
                val grown = n * 2
                x = x.copyOf(grown)
                y = y.copyOf(grown)
                angle = angle.copyOf(grown)
                glyph = glyph.copyOf(grown)
                half = half.copyOf(grown)
                color = color.copyOf(grown)
            }
            x[n] = px
            y[n] = py
            angle[n] = a
            glyph[n] = g
            half[n] = h
            color[n] = c
            n++
        }

        fun build() = FlowMarks(cols, rows, n, x, y, angle, glyph, half, color, size)
    }
}

/** Points set along the flow, evenly: rows that keep their distance from one another. */
internal class Streamlines(val count: Int, val x: FloatArray, val y: FloatArray, val angle: FloatArray) {

    companion object {
        private const val MAX_STEPS = 600
        private const val OWN_SHARE = 0.8f
        private const val FILL_SHARE = 0.8f
        private const val SKIP_RECENT = 3

        /**
         * Lines through [field] that keep [sep] cells apart, with a point every [step] cells.
         * Started where the direction is clearest, so the strong forms get their lines first; a
         * last pass drops single points into the gaps the lines left, so areas close up.
         */
        fun trace(field: FlowField, sep: Float, step: Float): Streamlines {
            val cols = field.cols
            val rows = field.rows
            val cell = sep
            val gw = (cols / cell).toInt() + 2
            val gh = (rows / cell).toInt() + 2
            val head = IntArray(gw * gh) { -1 }
            var px = FloatArray(8192)
            var py = FloatArray(8192)
            var pa = FloatArray(8192)
            var line = IntArray(8192)
            var next = IntArray(8192)
            var n = 0
            val sep2 = sep * sep

            fun free(x: Float, y: Float, own: Int, d2: Float): Boolean {
                val cx = (x / cell).toInt()
                val cy = (y / cell).toInt()
                for (j in max(0, cy - 1)..min(gh - 1, cy + 1)) {
                    for (i in max(0, cx - 1)..min(gw - 1, cx + 1)) {
                        var p = head[j * gw + i]
                        while (p >= 0) {
                            if (line[p] != own) {
                                val dx = px[p] - x
                                val dy = py[p] - y
                                if (dx * dx + dy * dy < d2) return false
                            }
                            p = next[p]
                        }
                    }
                }
                return true
            }

            fun put(x: Float, y: Float, a: Float, id: Int) {
                if (n == px.size) {
                    val grown = n * 2
                    px = px.copyOf(grown)
                    py = py.copyOf(grown)
                    pa = pa.copyOf(grown)
                    line = line.copyOf(grown)
                    next = next.copyOf(grown)
                }
                val slot = (y / cell).toInt() * gw + (x / cell).toInt()
                px[n] = x
                py[n] = y
                pa[n] = a
                line[n] = id
                next[n] = head[slot]
                head[slot] = n
                n++
            }

            // One seed per cell of the spacing grid, a little off its corner, clearest first.
            val seedCount = (gw - 1) * (gh - 1)
            val seedX = FloatArray(seedCount)
            val seedY = FloatArray(seedCount)
            val worth = FloatArray(seedCount)
            for (s in 0 until seedCount) {
                seedX[s] = min(cols - 0.01f, (s % (gw - 1) + jitter(s, 1)) * cell)
                seedY[s] = min(rows - 0.01f, (s / (gw - 1) + jitter(s, 2)) * cell)
                val at = field.index(seedX[s], seedY[s])
                worth[s] = field.sure[at] * field.edge[at]
            }
            val order = (0 until seedCount).sortedByDescending { worth[it] }

            val runX = FloatArray(MAX_STEPS * 2 + 1)
            val runY = FloatArray(MAX_STEPS * 2 + 1)
            val runA = FloatArray(MAX_STEPS * 2 + 1)
            var lines = 0
            for (s in order) {
                val sx = seedX[s]
                val sy = seedY[s]
                if (!free(sx, sy, -1, sep2)) continue
                // The run is built outward from the middle of the buffer, forward then backward.
                var first = MAX_STEPS
                var last = MAX_STEPS
                runX[first] = sx
                runY[first] = sy
                runA[first] = field.angle[field.index(sx, sy)]
                for (sign in intArrayOf(1, -1)) {
                    var x = sx
                    var y = sy
                    var before = runA[MAX_STEPS] + if (sign == 1) 0f else PI
                    for (k in 0 until MAX_STEPS) {
                        var a = field.angle[field.index(x, y)]
                        // The field has no arrowhead; keep going the way we were going.
                        if (cos(a - before) < 0f) a += PI
                        x += cos(a) * step
                        y += sin(a) * step
                        // Written so that a position that is not a number fails it too.
                        if (!(x >= 0f && y >= 0f && x < cols && y < rows) || !free(x, y, lines, sep2)) break
                        // A line running round a closed form must stop when it meets its own
                        // start, or it laps the form again and again and draws a solid tube.
                        if (meetsItself(runX, runY, first, last, sign, x, y, sep2 * OWN_SHARE)) break
                        if (sign == 1) last++ else first--
                        val at = if (sign == 1) last else first
                        runX[at] = x
                        runY[at] = y
                        runA[at] = a
                        before = a
                    }
                }
                for (i in first..last) put(runX[i], runY[i], runA[i], lines)
                lines++
            }

            val tight = sep2 * FILL_SHARE * FILL_SHARE
            var y = sep / 4f
            while (y < rows) {
                var x = sep / 4f
                while (x < cols) {
                    if (free(x, y, -1, tight)) put(x, y, field.angle[field.index(x, y)], lines++)
                    x += sep / 2f
                }
                y += sep / 2f
            }
            return Streamlines(n, px, py, pa)
        }

        private const val PI = 3.1415927f

        /** Whether ([x], [y]) is within √[d2] of the run's own points, the last few steps aside. */
        @Suppress("LongParameterList")
        private fun meetsItself(runX: FloatArray, runY: FloatArray, first: Int, last: Int, sign: Int, x: Float, y: Float, d2: Float): Boolean {
            val from = if (sign == 1) first else first + SKIP_RECENT
            val to = if (sign == 1) last - SKIP_RECENT else last
            for (i in from..to) {
                val dx = runX[i] - x
                val dy = runY[i] - y
                if (dx * dx + dy * dy < d2) return true
            }
            return false
        }

        private fun jitter(n: Int, salt: Int): Float {
            var v = n * 374761393 + salt * 668265263
            v = (v xor (v ushr 13)) * 1274126177
            return ((v xor (v ushr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
        }
    }
}
