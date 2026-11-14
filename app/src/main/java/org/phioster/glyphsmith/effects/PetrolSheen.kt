package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RowParallel

/**
 * Oil on water: bands of rainbow that follow a film's thickness, lying over the picture where
 * it is dark and thinning out where it is bright. Played as a loop the colours run through.
 */
@Serializable
data class PetrolSheenParams(
    val enabled: Boolean = false,
    /** 0..100 — how strongly the film lies over the picture. */
    val amount: Int = 60,
    /** 0..100 — the size of the bands: small pools, or one wide slick. */
    val scale: Int = 50,
    /** 0..100 — how far the film keeps to the dark parts; 0 lies evenly over everything. */
    val darks: Int = 80,
    /** 0..359 — turns every colour of the film. */
    val hue: Int = 0,
    /** -4..4 — how many times the colours run through over one loop; 0 holds them. */
    val flow: Int = 1,
    val seed: Int = 1,
)

object PetrolSheen {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::petrolSheen,
        { copy(petrolSheen = it) },
        PetrolSheenParams::enabled,
        randomise = { roll ->
            copy(enabled = true, amount = roll.random.nextInt(35, 85), hue = roll.random.nextInt(0, 360), seed = roll.random.nextInt(1, 999))
        },
    ) { pixels, params, ctx -> apply(pixels, params, ctx.time) }

    fun apply(source: Pixels, params: PetrolSheenParams, time: Float = 0f): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || params.amount <= 0 || w < 4 || h < 4) return source
        val amount = params.amount.coerceIn(0, 100) / 60f
        val darks = params.darks.coerceIn(0, 100) / 100f
        // Pools across the width: 1.5 for one slick, 8 for many small ones.
        val across = 8f - params.scale.coerceIn(0, 100) / 100f * 6.5f
        val wide = across / w
        val turn = Math.floorMod(params.hue, 360) / 360f + (time - floor(time)) * params.flow
        val data = source.data
        RowParallel.rows(h) { band ->
            for (y in band) {
                for (x in 0 until w) {
                    val p = data[y * w + x]
                    val lum = PixelOps.luminance(p)
                    val thick = PrintNoise.value(x * wide, y * wide, params.seed) * 1.5f +
                        PrintNoise.value(x * wide * 2.4f, y * wide * 2.4f, params.seed + 3) * 0.5f + lum * 0.9f
                    val at = thick * 1.25f + turn
                    val hue = (at - floor(at)) * 6f
                    val rise = 1f - abs(hue % 2f - 1f)
                    val fr: Float
                    val fg: Float
                    val fb: Float
                    when (hue.toInt()) {
                        0 -> { fr = 1f; fg = rise; fb = 0f }
                        1 -> { fr = rise; fg = 1f; fb = 0f }
                        2 -> { fr = 0f; fg = 1f; fb = rise }
                        3 -> { fr = 0f; fg = rise; fb = 1f }
                        4 -> { fr = rise; fg = 0f; fb = 1f }
                        else -> { fr = 1f; fg = 0f; fb = rise }
                    }
                    // The film is as bright as what lies under it, and never quite pure colour.
                    val lit = 0.25f + lum * 0.9f
                    val share = (((1f - lum * 1.8f).coerceIn(0f, 1f) * darks + (1f - darks)) * 0.5f + 0.08f) * amount
                    val mix = share.coerceIn(0f, 1f)
                    fun channel(base: Int, film: Float): Int {
                        val b = base / 255f
                        val screened = 1f - (1f - b) * (1f - (film * 0.75f + 0.25f) * lit).coerceIn(0f, 1f)
                        return ((b * (1 - mix) + screened * mix) * 255f).toInt().coerceIn(0, 255)
                    }
                    data[y * w + x] = PixelOps.argb(
                        PixelOps.alphaOf(p),
                        channel(PixelOps.redOf(p), fr), channel(PixelOps.greenOf(p), fg), channel(PixelOps.blueOf(p), fb),
                    )
                }
            }
        }
        return source
    }
}
