package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.phioster.glyphsmith.core.image.Pixels

/** One slice of the tonal range and the shape drawn for it. */
@Serializable
data class ShapeBand(
    val shape: ShapeKind = ShapeKind.CIRCLE,
    /** 0..200 — this band's shapes, in % of the grid's shape size. */
    val size: Int = 100,
    /** 1..100 — the band's share of the tonal range, relative to the other bands. */
    val width: Int = 50,
    /** This band's own colour, used when [ShapeShifterParams.perBandColor] is on. */
    val color: Int = -1,
)

enum class FeatherPattern(val label: String) { ORDERED("ordered"), SCATTER("scatter") }

enum class ShapeLayout(val label: String) { SQUARE("square"), OFFSET("offset") }

/**
 * Shape Shifter: the picture as a grid of vector shapes. Each cell's tone picks a band, the
 * band says which shape and how large; the colour comes from the colour mode as usual.
 */
@Serializable
data class ShapeShifterParams(
    /** Dark to bright. */
    val bands: List<ShapeBand> = ShapeSets.default.bands,
    /** 0..100 — how far neighbouring bands dither into each other. */
    val feather: Int = 0,
    val featherPattern: FeatherPattern = FeatherPattern.ORDERED,
    /** 10..200 — a shape's size in % of its cell; above 100 neighbours overlap. */
    val shapeSize: Int = 100,
    /** 0..100 — how much a cell's own tone shrinks its shape, on top of the band size. */
    val toneScaling: Int = 0,
    val layout: ShapeLayout = ShapeLayout.SQUARE,
    /** 0..359 — every shape turned by this many degrees. */
    val rotation: Int = 0,
    /** Each band draws in its own [ShapeBand.color] instead of what the colour mode would give. */
    val perBandColor: Boolean = false,
    /** 0..100 — how far each shape is turned at random, up to a quarter turn either way. */
    val rotationJitter: Int = 0,
    /** Hard pixel edges instead of smoothed ones. */
    val crispEdges: Boolean = false,
)

/** A named list of bands to start from. */
class ShapeSet(val name: String, val bands: List<ShapeBand>) {
    override fun toString(): String = name
}

object ShapeSets {
    private fun set(name: String, vararg bands: Pair<ShapeKind, Int>) =
        ShapeSet(name, bands.map { (shape, size) -> ShapeBand(shape, size) })

    private const val RED = 0xFFE5262B.toInt()
    private const val GREY = 0xFF8A8A8A.toInt()
    private const val PALE = 0xFFE4E4E4.toInt()
    private const val DEEP_VIOLET = 0xFF5A2DA8.toInt()
    private const val VIOLET = 0xFF9B4DFF.toInt()
    private const val ORCHID = 0xFFE05CFF.toInt()
    private const val HOT_PINK = 0xFFFF4FA8.toInt()

    // The brightest band stops at or just under 100, so neighbours touch without merging and
    // the shapes stay readable; overlap is a choice made with the grid's shape size.
    val all: List<ShapeSet> = listOf(
        set(
            "circles",
            ShapeKind.EMPTY to 100, ShapeKind.CIRCLE to 35, ShapeKind.CIRCLE to 55,
            ShapeKind.CIRCLE to 75, ShapeKind.CIRCLE to 95,
        ),
        set(
            "4-point stars",
            ShapeKind.EMPTY to 100, ShapeKind.PLUS to 40, ShapeKind.STAR4 to 65,
            ShapeKind.STAR4 to 90, ShapeKind.DIAMOND to 80,
        ),
        set(
            "plus and cross",
            ShapeKind.EMPTY to 100, ShapeKind.CROSS to 55, ShapeKind.CROSS to 80,
            ShapeKind.PLUS to 70, ShapeKind.PLUS to 90,
        ),
        set(
            "diamonds",
            ShapeKind.EMPTY to 100, ShapeKind.DIAMOND to 40, ShapeKind.DIAMOND to 65,
            ShapeKind.DIAMOND to 85, ShapeKind.SQUARE to 95,
        ),
        set(
            "rings",
            ShapeKind.EMPTY to 100, ShapeKind.RING to 55, ShapeKind.RING to 90,
            ShapeKind.CIRCLE to 90,
        ),
        set(
            "hearts",
            ShapeKind.EMPTY to 100, ShapeKind.HEART to 45, ShapeKind.HEART to 70,
            ShapeKind.HEART to 95,
        ),
        set(
            "stars",
            ShapeKind.EMPTY to 100, ShapeKind.STAR5 to 45, ShapeKind.STAR5 to 70,
            ShapeKind.STAR5 to 100,
        ),
        set(
            "bars",
            ShapeKind.EMPTY to 100, ShapeKind.BAR to 55, ShapeKind.BAR to 95,
            ShapeKind.SQUARE to 100,
        ),
        set(
            "hexagons",
            ShapeKind.EMPTY to 100, ShapeKind.HEXAGON to 45, ShapeKind.HEXAGON to 72,
            ShapeKind.HEXAGON to 100,
        ),
        // A dither built from bands: the tiles fill their cell, so the steps read as tone.
        set(
            "bayer tiles",
            ShapeKind.EMPTY to 100, ShapeKind.BAYER_13 to 100, ShapeKind.BAYER_38 to 100,
            ShapeKind.BAYER_63 to 100, ShapeKind.SQUARE to 114,
        ),
        // Carries band colours of its own; they show once "colour per band" is on.
        ShapeSet(
            "red hearts",
            listOf(
                ShapeBand(ShapeKind.EMPTY),
                ShapeBand(ShapeKind.CIRCLE, 40, color = GREY),
                ShapeBand(ShapeKind.CIRCLE, 70, color = PALE),
                ShapeBand(ShapeKind.CIRCLE, 95, color = PALE),
                ShapeBand(ShapeKind.PIXEL_HEART, 100, 25, RED),
            ),
        ),
        // Dots, then slim stars, then hearts from violet to pink: made to be lit by a glow.
        ShapeSet(
            "glowing hearts",
            listOf(
                ShapeBand(ShapeKind.EMPTY, width = 35),
                ShapeBand(ShapeKind.CIRCLE, 30, 30, DEEP_VIOLET),
                ShapeBand(ShapeKind.STAR4, 75, 40, VIOLET),
                ShapeBand(ShapeKind.PIXEL_HEART, 95, 50, ORCHID),
                ShapeBand(ShapeKind.PIXEL_HEART, 100, 60, HOT_PINK),
            ),
        ),
    )

    val default: ShapeSet get() = all.first()

    /** The bands of the set called [name]; the default's if there is none. */
    fun named(name: String): List<ShapeBand> = (all.firstOrNull { it.name == name } ?: default).bands
}

/** The band list's own arithmetic, kept out of the UI so it can be tested without one. */
object ShapeBands {
    const val MAX_BANDS = 8
    private const val EVEN_WIDTH = 50

    /** Each band's upper bound in 0..1; the last is always 1. */
    fun bounds(bands: List<ShapeBand>): FloatArray {
        val total = bands.sumOf { it.width.coerceAtLeast(1) }.toFloat()
        var run = 0f
        return FloatArray(bands.size) { i ->
            run += bands[i].width.coerceAtLeast(1)
            if (i == bands.lastIndex) 1f else run / total
        }
    }

    /** The band [tone] (0..1) falls into. */
    fun indexOf(bounds: FloatArray, tone: Float): Int {
        for (i in bounds.indices) if (tone < bounds[i]) return i
        return bounds.lastIndex
    }

    /** Splits band [index] into two halves of the same shape. */
    fun split(bands: List<ShapeBand>, index: Int): List<ShapeBand> {
        if (bands.size >= MAX_BANDS || index !in bands.indices) return bands
        val half = bands[index].copy(width = (bands[index].width / 2).coerceAtLeast(1))
        return bands.take(index) + half + half + bands.drop(index + 1)
    }

    fun remove(bands: List<ShapeBand>, index: Int): List<ShapeBand> =
        if (bands.size <= 1 || index !in bands.indices) bands else bands.filterIndexed { i, _ -> i != index }

    fun distribute(bands: List<ShapeBand>): List<ShapeBand> = bands.map { it.copy(width = EVEN_WIDTH) }

    /** Swaps which tones get which shapes; the widths stay where they are. */
    fun reverse(bands: List<ShapeBand>): List<ShapeBand> =
        bands.indices.map { i -> bands[bands.lastIndex - i].copy(width = bands[i].width) }

    /** "0–63" for the editor: the 8-bit tones band [index] covers. */
    fun rangeLabel(bands: List<ShapeBand>, index: Int): String {
        val bounds = bounds(bands)
        val from = if (index == 0) 0 else (bounds[index - 1] * TONES).toInt()
        val to = if (index == bands.lastIndex) TONES - 1 else (bounds[index] * TONES).toInt() - 1
        return "$from–${to.coerceAtLeast(from)}"
    }

    private const val TONES = 256
}

/**
 * Where every shape goes: one entry per cell, positions and radii in cell units so the same
 * field draws the preview, the full-size export and the SVG.
 */
class ShapeField(
    val cols: Int,
    val rows: Int,
    val x: FloatArray,
    val y: FloatArray,
    val radius: FloatArray,
    val shape: Array<ShapeKind>,
    val color: IntArray,
    /** Cell indices dark to bright, so brighter shapes are drawn over darker ones. */
    val order: IntArray,
    /** Degrees per cell: the grid's rotation plus that cell's jitter. */
    val rotation: IntArray,
    val crisp: Boolean = false,
)

object ShapeShifter {

    private const val SAMPLES = 4
    private const val MAX_PIXELS = 16_000_000L
    private const val MIN_BLOCK = 24
    private const val OPAQUE = 0xFF000000.toInt()
    private const val LEVELS = 256
    private const val QUARTER = 4
    private const val FULL_TURN = 360
    private const val JITTER_REACH = 90
    private const val JITTER_STEP = 6

    /** 4×4 Bayer, the ordered feather's thresholds. */
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /**
     * Output pixels per cell: as large as [maxSide] allows, but no more than twice the source
     * resolution and never past a pixel budget — an export must not ask for a 270 MB buffer.
     */
    fun blockFor(cols: Int, rows: Int, cell: Int, maxSide: Int): Int {
        val fill = (maxSide / max(cols, rows)).coerceAtLeast(1)
        val budget = sqrt(MAX_PIXELS.toDouble() / (cols.toLong() * rows)).toInt().coerceAtLeast(1)
        return min(min(fill, max(cell * 2, MIN_BLOCK)), budget)
    }

    fun field(grid: CellGrid, params: RenderSettings): ShapeField {
        val shifter = params.shapeShifter
        val bands = shifter.bands.ifEmpty { ShapeSets.default.bands }
        val bounds = ShapeBands.bounds(bands)
        val palette = params.renderPalette().colors
        val n = grid.cols * grid.rows
        val x = FloatArray(n)
        val y = FloatArray(n)
        val radius = FloatArray(n)
        val shape = Array(n) { ShapeKind.EMPTY }
        val color = IntArray(n)
        val rotation = IntArray(n)
        val jitterSteps = shifter.rotationJitter.coerceIn(0, 100) * JITTER_REACH / 100 / JITTER_STEP
        val base = 0.5f * shifter.shapeSize.coerceIn(10, 200) / 100f
        val toneScale = shifter.toneScaling.coerceIn(0, 100) / 100f
        // The feather jitters the tone by up to half an average band before the band is chosen.
        val spread = shifter.feather.coerceIn(0, 100) / 100f / bands.size

        for (row in 0 until grid.rows) {
            val shift = if (shifter.layout == ShapeLayout.OFFSET && row % 2 == 1) 0.5f else 0f
            for (col in 0 until grid.cols) {
                val i = row * grid.cols + col
                val tone = grid.luma[i].coerceIn(0f, 1f)
                val jitter = (threshold(col, row, shifter.featherPattern) - 0.5f) * spread
                val band = bands[ShapeBands.indexOf(bounds, (tone + jitter).coerceIn(0f, 1f))]
                x[i] = col + 0.5f + shift
                y[i] = row + 0.5f
                shape[i] = band.shape
                radius[i] = base * band.size.coerceIn(0, 200) / 100f * (1f - toneScale * (1f - tone))
                color[i] = if (shifter.perBandColor) band.color or OPAQUE else colourOf(i, tone, grid, params, palette)
                // Jitter in whole steps, so cells that land on the same turn share one stamp.
                val turn = Math.round((hash(col + 7919, row) * 2f - 1f) * jitterSteps) * JITTER_STEP
                rotation[i] = Math.floorMod(shifter.rotation + turn, FULL_TURN)
            }
        }
        return ShapeField(
            grid.cols, grid.rows, x, y, radius, shape, color, byTone(grid.luma, n), rotation, shifter.crispEdges,
        )
    }

    private fun threshold(col: Int, row: Int, pattern: FeatherPattern): Float = when (pattern) {
        FeatherPattern.ORDERED -> (bayer[(row and 3) * 4 + (col and 3)] + 0.5f) / 16f
        FeatherPattern.SCATTER -> hash(col, row)
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

    /** A counting sort over 256 tone levels: stable, and no boxing for a hundred thousand cells. */
    private fun byTone(luma: FloatArray, n: Int): IntArray {
        val level = IntArray(n) { (luma[it].coerceIn(0f, 1f) * (LEVELS - 1)).toInt() }
        val start = IntArray(LEVELS + 1)
        for (l in level) start[l + 1]++
        for (l in 0 until LEVELS) start[l + 1] += start[l]
        val order = IntArray(n)
        for (i in 0 until n) order[start[level[i]]++] = i
        return order
    }

    /** Draws [field] at [block] output pixels per cell. */
    fun render(field: ShapeField, block: Int, params: RenderSettings): Pixels {
        val width = field.cols * block
        val height = field.rows * block
        val out = IntArray(width * height)
        if (!params.transparentBackground) out.fill(params.backgroundColor or OPAQUE)

        val stamps = HashMap<Long, Stamp>()

        for (i in field.order) {
            val kind = field.shape[i]
            val halfPixels = Math.round(field.radius[i] * block * 2f)
            if (kind == ShapeKind.EMPTY || halfPixels <= 0) continue
            // Centres are snapped to quarter pixels, so a stamp can be shared by every cell
            // that lands on the same sub-pixel position.
            val cx = Math.round(field.x[i] * block * QUARTER)
            val cy = Math.round(field.y[i] * block * QUARTER)
            val fx = Math.floorMod(cx, QUARTER)
            val fy = Math.floorMod(cy, QUARTER)
            val turn = field.rotation[i]
            val key = kind.ordinal.toLong() or (fx.toLong() shl 8) or (fy.toLong() shl 10) or
                (turn.toLong() shl 12) or (halfPixels.toLong() shl 24)
            val stamp = stamps.getOrPut(key) {
                val radians = turn * PI / 180.0
                Stamp.of(
                    kind, halfPixels / 2f, fx / QUARTER.toFloat(), fy / QUARTER.toFloat(),
                    cos(radians).toFloat(), sin(radians).toFloat(), field.crisp,
                )
            }
            blit(out, width, height, stamp, Math.floorDiv(cx, QUARTER), Math.floorDiv(cy, QUARTER), field.color[i])
        }
        return Pixels(out, width, height)
    }

    @Suppress("LongParameterList")
    private fun blit(out: IntArray, width: Int, height: Int, stamp: Stamp, cx: Int, cy: Int, color: Int) {
        val left = cx - stamp.half
        val top = cy - stamp.half
        val side = stamp.half * 2
        for (j in max(0, -top) until min(side, height - top)) {
            val row = (top + j) * width + left
            for (i in max(0, -left) until min(side, width - left)) {
                val cover = stamp.alpha[j * side + i].toInt() and 0xFF
                if (cover != 0) out[row + i] = over(out[row + i], color, cover)
            }
        }
    }

    /** [color] over [dst] at [cover]/255; a transparent background stays transparent around it. */
    private fun over(dst: Int, color: Int, cover: Int): Int {
        if (cover == 255) return color or OPAQUE
        val keep = (dst ushr 24) * (255 - cover) / 255
        val alpha = cover + keep
        fun mix(shift: Int): Int =
            (((color shr shift) and 0xFF) * cover + ((dst shr shift) and 0xFF) * keep) / alpha
        return (alpha shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }

    /** One shape's coverage at one size and sub-pixel position, 0..255 per pixel. */
    private class Stamp(val half: Int, val alpha: ByteArray) {
        companion object {
            @Suppress("LongParameterList")
            fun of(
                kind: ShapeKind,
                radius: Float,
                fx: Float,
                fy: Float,
                cosR: Float,
                sinR: Float,
                crisp: Boolean,
            ): Stamp {
                val half = ceil(radius).toInt() + 1
                val side = half * 2
                val alpha = ByteArray(side * side)
                val contours = Shapes.contours(kind)
                for (j in 0 until side) {
                    for (i in 0 until side) {
                        var hits = 0
                        for (s in 0 until SAMPLES * SAMPLES) {
                            val px = i + (s % SAMPLES + 0.5f) / SAMPLES - half - fx
                            val py = j + (s / SAMPLES + 0.5f) / SAMPLES - half - fy
                            // Undo the rotation, then scale the pixel into the unit box.
                            val ux = (px * cosR + py * sinR) / radius
                            val uy = (py * cosR - px * sinR) / radius
                            if (Shapes.inside(contours, ux, uy)) hits++
                        }
                        val cover = hits * 255 / (SAMPLES * SAMPLES)
                        alpha[j * side + i] = (if (crisp) (if (cover >= 128) 255 else 0) else cover).toByte()
                    }
                }
                return Stamp(half, alpha)
            }
        }
    }
}
