package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RowParallel

/**
 * A screen of hexagons: one cell per comb, as large as the picture is bright there and in the
 * colour it has there. Dark parts shrink to points and the ground shows between them.
 */
@Serializable
data class HoneycombScreenParams(
    val enabled: Boolean = false,
    /** 4..60 — the size of a comb, in thousandths of the picture's width. */
    val cell: Int = 15,
    /** 0..100 — how large a cell grows for a given brightness. */
    val size: Int = 60,
    /** 0..100 — how much colour a cell keeps; 0 leaves it grey. */
    val colour: Int = 100,
)

object HoneycombScreen {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::honeycomb,
        { copy(honeycomb = it) },
        HoneycombScreenParams::enabled,
        randomise = { roll -> copy(enabled = true, cell = roll.random.nextInt(8, 30), size = roll.random.nextInt(40, 85)) },
    ) { pixels, params, _ -> apply(pixels, params) }

    private val ROOT3 = sqrt(3f)
    private const val GROUND_RED = 6
    private const val GROUND_GREEN = 6
    private const val GROUND_BLUE = 10

    fun apply(source: Pixels, params: HoneycombScreenParams): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || w < 8 || h < 8) return source
        val cell = (params.cell.coerceIn(4, 60) * w / 1000f).coerceAtLeast(2f)
        val grow = 0.75f + params.size.coerceIn(0, 100) / 100f * 0.7f
        val keep = params.colour.coerceIn(0, 100) / 100f
        // A cell takes the colour of its whole comb, not of the one pixel a dither left at its centre.
        val from = PrintNoise.softened(source.data, w, h, (cell * 0.45f).toInt().coerceAtLeast(1))
        val data = source.data
        RowParallel.rows(h) { band ->
            for (y in band) {
                for (x in 0 until w) {
                    // Which comb this pixel lies in: cube coordinates, rounded to the nearest cell.
                    val q = (ROOT3 / 3f * x - y / 3f) / cell
                    val r = (2f / 3f * y) / cell
                    var rx = q.roundToInt()
                    val ry = (-q - r).roundToInt()
                    var rz = r.roundToInt()
                    val dx = abs(rx - q)
                    val dy = abs(ry - (-q - r))
                    val dz = abs(rz - r)
                    if (dx > dy && dx > dz) rx = -ry - rz else if (dy <= dz) rz = -rx - ry
                    val cx = cell * ROOT3 * (rx + rz / 2f)
                    val cy = cell * 1.5f * rz
                    val centre = from[cy.toInt().coerceIn(0, h - 1) * w + cx.toInt().coerceIn(0, w - 1)]
                    val lum = PixelOps.luminance(centre)
                    // Distance in the comb's own measure: 1 on its edge, whichever side.
                    val ax = abs(x - cx)
                    val ay = abs(y - cy)
                    val away = maxOf(ax * ROOT3 / 2f + ay / 2f, ay) / (cell * 0.866f)
                    val reach = sqrt((lum * grow).coerceIn(0f, 1f)) * 0.98f
                    val ink = ((reach - away) * cell * 0.8f + 0.5f).coerceIn(0f, 1f)
                    // A cell is brighter than the picture was there, since the ground takes its share.
                    val lift = (lum * 1.4f + 0.25f).coerceIn(0f, 1f) / lum.coerceAtLeast(0.05f)
                    val grey = lum * lift * 255f
                    val cr = (grey * (1 - keep) + PixelOps.redOf(centre) * lift * keep).coerceAtMost(255f)
                    val cg = (grey * (1 - keep) + PixelOps.greenOf(centre) * lift * keep).coerceAtMost(255f)
                    val cb = (grey * (1 - keep) + PixelOps.blueOf(centre) * lift * keep).coerceAtMost(255f)
                    data[y * w + x] = PixelOps.argb(
                        PixelOps.alphaOf(from[y * w + x]),
                        (cr * ink + GROUND_RED * (1 - ink)).toInt().coerceIn(0, 255),
                        (cg * ink + GROUND_GREEN * (1 - ink)).toInt().coerceIn(0, 255),
                        (cb * ink + GROUND_BLUE * (1 - ink)).toInt().coerceIn(0, 255),
                    )
                }
            }
        }
        return source
    }
}
