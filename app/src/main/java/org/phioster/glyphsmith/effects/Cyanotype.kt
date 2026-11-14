package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RowParallel

/**
 * A sun print: the picture in Prussian blue on paper that was coated by hand.
 *
 * Tones run from deep blue to the paper's own white, and the coating stops short of the edge in
 * an uneven brushed line, the way the sensitiser never quite reaches it.
 */
@Serializable
data class CyanotypeParams(
    val enabled: Boolean = false,
    /** 0..100 — how far the picture's own colours give way to the blue. */
    val strength: Int = 100,
    /** 0..100 — how wide the uncoated paper round the edge is; 0 prints to the edge. */
    val border: Int = 45,
    /** 0..100 — paper tooth in the print. */
    val grain: Int = 40,
    val seed: Int = 1,
)

object Cyanotype {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::cyanotype,
        { copy(cyanotype = it) },
        CyanotypeParams::enabled,
        randomise = { roll -> copy(enabled = true, border = roll.random.nextInt(0, 70), seed = roll.random.nextInt(1, 999)) },
    ) { pixels, params, _ -> apply(pixels, params) }

    private val BLUES = intArrayOf(0x08183A, 0x0E3468, 0x2868A0, 0x8CBCD6, 0xECEEE4)
    private const val PAPER_RED = 236
    private const val PAPER_GREEN = 232
    private const val PAPER_BLUE = 218

    fun apply(source: Pixels, params: CyanotypeParams): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || w < 4 || h < 4) return source
        val mix = params.strength.coerceIn(0, 100) / 100f
        val grain = params.grain.coerceIn(0, 100) / 100f * 0.11f
        // Lengths as shares of the width, so preview and export show the same edge.
        val unit = w / 720f
        val inset = params.border.coerceIn(0, 100) / 100f * 31f * unit
        val rough = params.border.coerceIn(0, 100) / 100f
        val data = source.data
        RowParallel.rows(h) { band ->
            for (y in band) {
                for (x in 0 until w) {
                    val p = data[y * w + x]
                    val lum = PixelOps.luminance(p)
                    val t = Math.pow(((lum - 0.04f) * 1.25f).coerceIn(0f, 1f).toDouble(), 0.85).toFloat()
                    val blue = PrintNoise.ramp(BLUES, t)
                    val tooth = (PrintNoise.hash(x, y, params.seed) - 0.5f) * grain * 255f
                    var r = PixelOps.redOf(p) * (1 - mix) + PixelOps.redOf(blue) * mix + tooth
                    var g = PixelOps.greenOf(p) * (1 - mix) + PixelOps.greenOf(blue) * mix + tooth
                    var b = PixelOps.blueOf(p) * (1 - mix) + PixelOps.blueOf(blue) * mix + tooth
                    if (inset > 0f) {
                        val edge = minOf(minOf(x, w - 1 - x), minOf(y, h - 1 - y)).toFloat()
                        val ragged = PrintNoise.value(x / (14f * unit), y / (14f * unit), params.seed + 5) * 46f +
                            PrintNoise.value(x / (3f * unit), y / (3f * unit), params.seed + 6) * 10f
                        val coat = ((edge - inset * 0.45f - ragged * rough * unit) / (6f * unit) + 0.5f).coerceIn(0f, 1f)
                        val fibre = (PrintNoise.value(x / (3f * unit), y / (3f * unit), params.seed + 9) - 0.5f) * 10f
                        r = r * coat + (PAPER_RED + fibre) * (1 - coat)
                        g = g * coat + (PAPER_GREEN + fibre) * (1 - coat)
                        b = b * coat + (PAPER_BLUE + fibre) * (1 - coat)
                    }
                    data[y * w + x] = PixelOps.argb(
                        PixelOps.alphaOf(p), r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255),
                    )
                }
            }
        }
        return source
    }
}
