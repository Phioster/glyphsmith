package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RowParallel

/**
 * A technical drawing: the picture's edges as pale lines on blue, over a drawing grid.
 *
 * Nothing of the picture's colour survives; what it keeps is where things begin and end.
 */
@Serializable
data class BlueprintParams(
    val enabled: Boolean = false,
    /** 0..100 — how readily an edge becomes a line. */
    val lines: Int = 55,
    /** 0..100 — how strongly the grid shows; 0 leaves it out. */
    val grid: Int = 50,
    /** 0..100 — how much of the picture's brightness shows faintly between the lines. */
    val fill: Int = 22,
    val seed: Int = 1,
)

object Blueprint {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::blueprint,
        { copy(blueprint = it) },
        BlueprintParams::enabled,
        randomise = { roll -> copy(enabled = true, lines = roll.random.nextInt(35, 80), fill = roll.random.nextInt(0, 40)) },
    ) { pixels, params, _ -> apply(pixels, params) }

    private const val PAPER_RED = 18
    private const val PAPER_GREEN = 58
    private const val PAPER_BLUE = 128
    private const val LINE_RED = 226
    private const val LINE_GREEN = 238
    private const val LINE_BLUE = 250

    /** Grid squares across the width, and how many of them make a heavier line. */
    private const val SQUARES = 20
    private const val MAJOR = 5

    /** Differences below this are the picture's grain, not an edge. */
    private const val FLOOR = 0.035f

    fun apply(source: Pixels, params: BlueprintParams): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || w < 8 || h < 8) return source
        val gain = params.lines.coerceIn(0, 100) / 100f * 10f
        val grid = params.grid.coerceIn(0, 100) / 100f
        val fill = params.fill.coerceIn(0, 100) / 100f
        // How far apart the two samples of an edge lie: a share of the width, so a line is as
        // heavy in the export as in the preview.
        val reach = (w / 300f).roundToInt().coerceAtLeast(1)
        val square = (w.toFloat() / SQUARES).coerceAtLeast(4f)
        val stroke = (w / 720f).coerceAtLeast(1f)
        val data = source.data
        val lum = FloatArray(w * h) { PixelOps.luminance(data[it]) }
        RowParallel.rows(h) { band ->
            for (y in band) {
                val up = (y - reach).coerceAtLeast(0) * w
                val down = (y + reach).coerceAtMost(h - 1) * w
                val gy = y / square
                val onRow = (gy - gy.toInt()) * square < stroke
                val majorRow = onRow && gy.toInt() % MAJOR == 0
                for (x in 0 until w) {
                    val left = (x - reach).coerceAtLeast(0)
                    val right = (x + reach).coerceAtMost(w - 1)
                    val edge = (abs(lum[y * w + right] - lum[y * w + left]) + abs(lum[down + x] - lum[up + x]) - FLOOR).coerceAtLeast(0f) * gain
                    val gx = x / square
                    val onColumn = (gx - gx.toInt()) * square < stroke
                    val ruled = when {
                        majorRow || (onColumn && gx.toInt() % MAJOR == 0) -> 0.36f
                        onRow || onColumn -> 0.16f
                        else -> 0f
                    } * grid
                    val ink = (edge + ruled + lum[y * w + x] * fill).coerceIn(0f, 1f)
                    // The blue is never quite even: a print fades where the light got at it.
                    val wash = 0.9f + PrintNoise.value(x / (square * 2f), y / (square * 2f), params.seed) * 0.2f
                    data[y * w + x] = PixelOps.argb(
                        PixelOps.alphaOf(data[y * w + x]),
                        (PAPER_RED * wash * (1 - ink) + LINE_RED * ink).toInt().coerceIn(0, 255),
                        (PAPER_GREEN * wash * (1 - ink) + LINE_GREEN * ink).toInt().coerceIn(0, 255),
                        (PAPER_BLUE * wash * (1 - ink) + LINE_BLUE * ink).toInt().coerceIn(0, 255),
                    )
                }
            }
        }
        return source
    }
}
