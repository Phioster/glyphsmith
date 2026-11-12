package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The colour of the tube's coating, which is the colour of everything on it. */
enum class Phosphor(val label: String, val rgb: Int) {
    GREEN("green", 0x3CFF6E),
    AMBER("amber", 0xFFB03C),
    BLUE("blue", 0x5CC8FF),
    WHITE("white", 0xE8F0FF),
}

/** How the tube moves over an animation. Every one of them closes its loop without a seam. */
enum class ScopeMotion(val label: String) {
    STILL("still"),

    /** The beam writes the picture from left to right, and the phosphor fades behind it. */
    SWEEP("sweep"),

    /** The traces travel down the tube, each taking the place of the next. */
    DRIFT("drift"),

    /** A wave runs along the traces, as if the signal were unsteady. */
    WAVE("wave"),
}

@Serializable
data class OscilloscopeParams(
    /** 4..24 — the distance between two traces, in 768ths of the picture's longer side. */
    val spacing: Int = 9,
    /** 0..80 — how far a trace rises where the picture is white, in the same unit. */
    val height: Int = 34,
    /** A trace in front hides the ones behind it, which is what makes the picture a relief. */
    val hidden: Boolean = true,
    val phosphor: Phosphor = Phosphor.GREEN,
    /** 0..200 % — how far the beam's light spreads over the tube. */
    val glow: Int = 100,
    /** The measuring grid etched on the glass. */
    val graticule: Boolean = true,
    /** Moves with the animation clock; a still picture shows the start of the loop. */
    val motion: ScopeMotion = ScopeMotion.STILL,
)

/** A finished tube face: opaque pixels. */
class ScopePicture(val pixels: IntArray, val width: Int, val height: Int)

/**
 * The picture as traces on an oscilloscope: one line per row, rising where the picture is bright.
 *
 * A beam, not a drawing: it is dimmer where it has to move fast, brighter where the picture is,
 * and its light spreads over the phosphor. Every length is a share of the picture's longer side,
 * so a preview and an export show the same traces and differ only in how finely they are drawn,
 * and a small picture gets as many traces as a large one. Free of Android types;
 * [OscilloscopeModule] only wraps the result in a bitmap.
 */
object Oscilloscope {

    /** The longest side of the working picture, in cells. */
    const val WORK_SIDE = 768

    private const val SMOOTH = 2.2f
    private const val NEAR_GLOW = 2.5f
    private const val FAR_GLOW = 9f
    private const val GRATICULE = 48f
    private const val FLOOR_LIGHT = 0.22f
    private const val SPEED_LOSS = 0.9f
    private const val AFTERGLOW = 0.18f
    private const val FADE = 3.5f
    private const val WAVE_HEIGHT = 4f
    private const val WAVE_LENGTH = 110f
    private const val WAVE_STAGGER = 0.012f
    private const val TURN = 6.2831855f
    private const val OPAQUE = 0xFF000000.toInt()

    /** The cell size that brings a source of this size down to the working picture. */
    fun cellFor(width: Int, height: Int): Int = max(1, (max(width, height) + WORK_SIDE - 1) / WORK_SIDE)

    /** Draws [grid] at [scale] output pixels per cell. */
    fun draw(grid: CellGrid, params: RenderSettings, scale: Float): ScopePicture {
        val scope = params.oscilloscope
        val cols = grid.cols
        val rows = grid.rows
        val width = max(1, (cols * scale).toInt())
        val height = max(1, (rows * scale).toInt())
        // Cells per unit: the working picture is not always the full 768 across.
        val unit = max(cols, rows) / WORK_SIDE.toFloat()
        val tone = FloatArray(cols * rows) { grid.luma[it].coerceIn(0f, 1f) }
        // A beam cannot follow single cells; unsmoothed, every trace is a row of spikes.
        repeat(3) { boxBlur(tone, cols, rows, cells(SMOOTH, unit)) }

        val time = params.temporal.time - floor(params.temporal.time)
        val core = traces(tone, cols, rows, width, height, scale, unit, scope, time)
        return ScopePicture(lit(core, cols, rows, width, height, scale, unit, scope), width, height)
    }

    private fun cells(units: Float, unit: Float): Int = max(1, (units * unit + 0.5f).toInt())

    /** [tone] at a column and a row that need not be whole. */
    private fun toneAt(tone: FloatArray, cols: Int, rows: Int, x: Float, y: Float): Float {
        val x0 = x.toInt().coerceIn(0, cols - 1)
        val y0 = y.toInt().coerceIn(0, rows - 1)
        return bilinear(tone, cols, x0, min(x0 + 1, cols - 1), y0, min(y0 + 1, rows - 1), x - x0, y - y0)
    }

    /** The beam's own line, 0..255 per output pixel, before any light spreads from it. */
    @Suppress("LongParameterList")
    private fun traces(
        tone: FloatArray,
        cols: Int,
        rows: Int,
        width: Int,
        height: Int,
        scale: Float,
        unit: Float,
        scope: OscilloscopeParams,
        time: Float,
    ): ByteArray {
        val core = ByteArray(width * height)
        val spacing = max(2f, scope.spacing.coerceIn(4, 24) * unit)
        val rise = scope.height.coerceIn(0, 80) * unit
        val half = max(1f, scale * 0.9f) / 2f
        // Per column, the highest point any nearer trace has reached: nothing is drawn below it.
        val horizon = FloatArray(width) { Float.MAX_VALUE }
        val ys = FloatArray(width)
        val vs = FloatArray(width)

        // Nearest first, which on a tube face means from the bottom up.
        // Drifting moves every trace down by up to one spacing, where the next one stood.
        val drift = if (scope.motion == ScopeMotion.DRIFT) time * spacing else 0f
        val wave = if (scope.motion == ScopeMotion.WAVE) WAVE_HEIGHT * unit * scale else 0f
        var line = floor((rows - 1 - drift) / spacing) * spacing + drift
        while (line > 0.01f) {
            for (x in 0 until width) {
                val at = ((x + 0.5f) / scale - 0.5f).coerceIn(0f, cols - 1f)
                val v = toneAt(tone, cols, rows, at, line)
                vs[x] = v
                ys[x] = (line - v * rise) * scale
                if (wave > 0f) {
                    // One whole turn per loop; each trace a little behind the one below it.
                    ys[x] += wave * kotlin.math.sin(TURN * (time + at / (WAVE_LENGTH * unit) + (line - drift) * WAVE_STAGGER / unit))
                }
            }
            for (x in 0 until width) {
                val next = ys[min(x + 1, width - 1)]
                val top = min(ys[x], next) - half
                val bottom = max(ys[x], next) + half
                var beam = (FLOOR_LIGHT + (1f - FLOOR_LIGHT) * vs[x]) / (1f + SPEED_LOSS * abs(next - ys[x]))
                if (scope.motion == ScopeMotion.SWEEP) {
                    // How long ago the beam passed here, in loops: just written is brightest.
                    val since = time - x.toFloat() / width
                    val age = since - floor(since)
                    beam *= AFTERGLOW + (1f - AFTERGLOW) * exp(-FADE * age) * 1.5f
                }
                val limit = if (scope.hidden) horizon[x] else Float.MAX_VALUE
                var y = max(0, floor(top).toInt())
                val last = min(height - 1, floor(bottom).toInt())
                while (y <= last) {
                    if (y < limit) {
                        val cover = (min(y + 1f, bottom) - max(y.toFloat(), top)).coerceIn(0f, 1f)
                        val value = (beam * cover * 255f).toInt().coerceAtMost(255)
                        val i = y * width + x
                        if (value > (core[i].toInt() and 0xFF)) core[i] = value.toByte()
                    }
                    y++
                }
            }
            if (scope.hidden) for (x in 0 until width) horizon[x] = min(horizon[x], ys[x] - half)
            line -= spacing
        }
        return core
    }

    /** The tube face: the beam, the light it spreads, the phosphor's colour and the glass. */
    @Suppress("LongParameterList")
    private fun lit(
        core: ByteArray,
        cols: Int,
        rows: Int,
        width: Int,
        height: Int,
        scale: Float,
        unit: Float,
        scope: OscilloscopeParams,
    ): IntArray {
        // The spread is soft, so it is worked out on the small working picture and stretched.
        val near = FloatArray(cols * rows)
        val counts = IntArray(cols * rows)
        for (y in 0 until height) {
            val row = min(rows - 1, (y / scale).toInt()) * cols
            for (x in 0 until width) {
                val cell = row + min(cols - 1, (x / scale).toInt())
                near[cell] += (core[y * width + x].toInt() and 0xFF) / 255f
                counts[cell]++
            }
        }
        for (i in near.indices) if (counts[i] > 0) near[i] /= counts[i]
        val far = near.copyOf()
        repeat(3) { boxBlur(near, cols, rows, cells(NEAR_GLOW, unit)) }
        repeat(3) { boxBlur(far, cols, rows, cells(FAR_GLOW, unit)) }

        val spread = scope.glow.coerceIn(0, 200) / 100f
        val red = (scope.phosphor.rgb shr 16 and 0xFF) / 255f
        val green = (scope.phosphor.rgb shr 8 and 0xFF) / 255f
        val blue = (scope.phosphor.rgb and 0xFF) / 255f
        val every = max(2, (GRATICULE * unit * scale).toInt())
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val gy = ((y + 0.5f) / scale - 0.5f).coerceIn(0f, rows - 1f)
            val y0 = gy.toInt()
            val y1 = min(y0 + 1, rows - 1)
            val ty = gy - y0
            for (x in 0 until width) {
                val gx = ((x + 0.5f) / scale - 0.5f).coerceIn(0f, cols - 1f)
                val x0 = gx.toInt()
                val x1 = min(x0 + 1, cols - 1)
                val tx = gx - x0
                val soft = bilinear(near, cols, x0, x1, y0, y1, tx, ty)
                val wide = bilinear(far, cols, x0, x1, y0, y1, tx, ty)
                val beam = (core[y * width + x].toInt() and 0xFF) / 255f
                val light = 1f - exp(-1.5f * (1.6f * beam + spread * (1.0f * soft + 0.3f * wide)))
                // The hottest part of the trace bleaches towards white, as phosphor does.
                val white = light * light * light * 0.6f
                val etched = if (scope.graticule && (x % every == 0 || y % every == 0)) 0.05f else 0f
                val r = red * (light + etched + 0.012f) + (1f - red) * white
                val g = green * (light + etched + 0.012f) + (1f - green) * white
                val b = blue * (light + etched + 0.012f) + (1f - blue) * white
                out[y * width + x] = OPAQUE or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
            }
        }
        return out
    }

    private fun channel(value: Float): Int = (value * 255f).toInt().coerceIn(0, 255)

    @Suppress("LongParameterList")
    private fun bilinear(field: FloatArray, cols: Int, x0: Int, x1: Int, y0: Int, y1: Int, tx: Float, ty: Float): Float {
        val top = field[y0 * cols + x0] * (1f - tx) + field[y0 * cols + x1] * tx
        val bottom = field[y1 * cols + x0] * (1f - tx) + field[y1 * cols + x1] * tx
        return top * (1f - ty) + bottom * ty
    }

    /** One box blur in place, edges held; three of them are near enough to a Gaussian. */
    private fun boxBlur(field: FloatArray, cols: Int, rows: Int, radius: Int) {
        val line = FloatArray(max(cols, rows))
        val window = 2 * radius + 1
        for (y in 0 until rows) {
            var sum = 0f
            for (k in -radius..radius) sum += field[y * cols + k.coerceIn(0, cols - 1)]
            for (x in 0 until cols) {
                line[x] = sum / window
                sum += field[y * cols + min(x + radius + 1, cols - 1)] - field[y * cols + max(x - radius, 0)]
            }
            for (x in 0 until cols) field[y * cols + x] = line[x]
        }
        for (x in 0 until cols) {
            var sum = 0f
            for (k in -radius..radius) sum += field[k.coerceIn(0, rows - 1) * cols + x]
            for (y in 0 until rows) {
                line[y] = sum / window
                sum += field[min(y + radius + 1, rows - 1) * cols + x] - field[max(y - radius, 0) * cols + x]
            }
            for (y in 0 until rows) field[y * cols + x] = line[y]
        }
    }
}
