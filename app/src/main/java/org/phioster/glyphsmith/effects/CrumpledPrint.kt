package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.sqrt
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RowParallel

/**
 * The picture printed on paper that was balled up and smoothed out again.
 *
 * The sheet is a field of small cones, folds within folds, lit from the top left: every facet
 * takes its own share of the light and the creases between them stand out as edges.
 */
@Serializable
data class CrumpledPrintParams(
    val enabled: Boolean = false,
    /** 0..100 — how many folds: few large facets, or a sheet crushed small. */
    val folds: Int = 30,
    /** 0..100 — how deep the folds are lit. */
    val depth: Int = 42,
    /** 0..100 — how much the paper shows through the ink. */
    val paper: Int = 16,
    val seed: Int = 1,
)

object CrumpledPrint {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::crumpledPrint,
        { copy(crumpledPrint = it) },
        CrumpledPrintParams::enabled,
        randomise = { roll ->
            copy(enabled = true, folds = roll.random.nextInt(20, 85), depth = roll.random.nextInt(30, 80), seed = roll.random.nextInt(1, 999))
        },
    ) { pixels, params, _ -> apply(pixels, params) }

    /** Three sizes of fold, each finer and fainter than the last. */
    private val FINER = floatArrayOf(1f, 2f, 4f)
    private val WEIGHT = floatArrayOf(1f, 0.5f, 0.14f)
    private const val LIGHT = 0.7071f
    private const val PAPER_RED = 240f
    private const val PAPER_GREEN = 236f
    private const val PAPER_BLUE = 224f

    fun apply(source: Pixels, params: CrumpledPrintParams): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || w < 8 || h < 8) return source
        // Folds across the width at the coarsest size: 3 for a sheet folded twice, 10 crushed.
        val across = 3f + params.folds.coerceIn(0, 100) / 100f * 7f
        val depth = params.depth.coerceIn(0, 100) / 100f * 0.62f
        val paper = params.paper.coerceIn(0, 100) / 100f
        val data = source.data
        RowParallel.rows(h) { band ->
            for (y in band) {
                for (x in 0 until w) {
                    var light = 0f
                    for (level in FINER.indices) {
                        val scale = across * FINER[level] / w
                        light += WEIGHT[level] * slope(x * scale, y * scale, params.seed + level * 31)
                    }
                    val shade = 1f + light * depth
                    val glare = (shade - 1.12f).coerceIn(0f, 1f) * 128f
                    val p = data[y * w + x]
                    data[y * w + x] = PixelOps.argb(
                        PixelOps.alphaOf(p),
                        ((PixelOps.redOf(p) * (1 - paper) + PAPER_RED * paper) * shade + glare).toInt().coerceIn(0, 255),
                        ((PixelOps.greenOf(p) * (1 - paper) + PAPER_GREEN * paper) * shade + glare).toInt().coerceIn(0, 255),
                        ((PixelOps.blueOf(p) * (1 - paper) + PAPER_BLUE * paper) * shade + glare).toInt().coerceIn(0, 255),
                    )
                }
            }
        }
        return source
    }

    /**
     * How much of the light a cone's side takes at ([x], [y]): the cone stands on the nearest of
     * the points scattered one to a cell, and its slope there points straight away from it.
     */
    private fun slope(x: Float, y: Float, seed: Int): Float {
        val cx = floor(x).toInt()
        val cy = floor(y).toInt()
        var best = Float.MAX_VALUE
        var bx = 0f
        var by = 0f
        for (j in -1..1) for (i in -1..1) {
            val px = cx + i + PrintNoise.hash(cx + i, cy + j, seed)
            val py = cy + j + PrintNoise.hash(cx + i, cy + j, seed + 17)
            val d = (x - px) * (x - px) + (y - py) * (y - py)
            if (d < best) {
                best = d
                bx = x - px
                by = y - py
            }
        }
        val length = sqrt(best).coerceAtLeast(1e-4f)
        return (bx + by) / length * LIGHT
    }
}
