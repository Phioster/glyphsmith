package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.effects.PixelOps.alphaOf
import org.phioster.glyphsmith.effects.PixelOps.argb
import org.phioster.glyphsmith.effects.PixelOps.blueOf
import org.phioster.glyphsmith.effects.PixelOps.greenOf
import org.phioster.glyphsmith.effects.PixelOps.luminance
import org.phioster.glyphsmith.effects.PixelOps.redOf
import org.phioster.glyphsmith.core.image.Pixels

/** Colour wash. [TintMode.DUOTONE] remaps the whole tonal range between two colours. */
object Tint {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::tint,
        { copy(tint = it) },
        TintParams::enabled,
        // The wash takes its colour from the palette the rest of the look was rolled with. A
        // tint in an unrelated hue is what makes a rolled look read as a mistake.
        randomise = { roll -> copy(enabled = true, color = roll.palette.colors.last()) },
    ) { pixels, params, _ -> apply(pixels, params) }

    fun apply(source: Pixels, params: TintParams): Pixels {
        if (!params.enabled || params.amount <= 0) return source
        val amount = (params.amount / 100f).coerceIn(0f, 1f)

        for (i in source.data.indices) {
            val pixel = source.data[i]
            val luma = luminance(pixel)

            val targetR: Float
            val targetG: Float
            val targetB: Float
            if (params.mode == TintMode.DUOTONE) {
                targetR = mix(redOf(params.shadowColor), redOf(params.highlightColor), luma)
                targetG = mix(greenOf(params.shadowColor), greenOf(params.highlightColor), luma)
                targetB = mix(blueOf(params.shadowColor), blueOf(params.highlightColor), luma)
            } else {
                // Straight tint keeps the pixel's own brightness and takes only the hue.
                targetR = redOf(params.color) * luma
                targetG = greenOf(params.color) * luma
                targetB = blueOf(params.color) * luma
            }

            source.data[i] = argb(
                alphaOf(pixel),
                (redOf(pixel) + (targetR - redOf(pixel)) * amount).toInt(),
                (greenOf(pixel) + (targetG - greenOf(pixel)) * amount).toInt(),
                (blueOf(pixel) + (targetB - blueOf(pixel)) * amount).toInt(),
            )
        }
        return source
    }

    private fun mix(from: Int, to: Int, t: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)
}
