package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.core.image.Pixels

/**
 * Blur and sharpen from one slider, because they are the same operation read in opposite
 * directions: an unsharp mask is the blurred image subtracted back out of the original.
 *
 * On ASCII art this behaves differently from a photograph. The glyphs are hard-edged shapes
 * on a flat background, so a little blur reads as phosphor bleed and a little sharpen reads
 * as an over-driven CRT — which is why the range is deliberately narrow at the sharpen end.
 */
object BlurSharpen {

    fun apply(source: Pixels, params: BlurSharpenParams): Pixels {
        if (!params.enabled || params.amount == 0) return source

        val radius = params.radius.coerceIn(1, 12)
        val blurred = PixelOps.blurRgba(source, radius)
        if (params.amount > 0) return mix(source, blurred, params.amount / 100f)

        // Sharpening pushes the original away from its own blur. Anything past 1 starts
        // ringing, and on hard-edged glyphs that shows up as a halo long before it does on
        // a photo, so the slider tops out at that point rather than going further.
        return unsharp(source, blurred, -params.amount / 100f)
    }

    /** Linear interpolation towards [blurred]; [t] of 1 is the fully blurred image. */
    private fun mix(source: Pixels, blurred: Pixels, t: Float): Pixels {
        val amount = t.coerceIn(0f, 1f)
        for (i in source.data.indices) {
            source.data[i] = lerpPixel(source.data[i], blurred.data[i], amount)
        }
        return source
    }

    private fun unsharp(source: Pixels, blurred: Pixels, amount: Float): Pixels {
        for (i in source.data.indices) {
            val original = source.data[i]
            val soft = blurred.data[i]
            source.data[i] = PixelOps.argb(
                PixelOps.alphaOf(original),
                boost(PixelOps.redOf(original), PixelOps.redOf(soft), amount),
                boost(PixelOps.greenOf(original), PixelOps.greenOf(soft), amount),
                boost(PixelOps.blueOf(original), PixelOps.blueOf(soft), amount),
            )
        }
        return source
    }

    private fun boost(original: Int, soft: Int, amount: Float): Int =
        (original + (original - soft) * amount).toInt().coerceIn(0, 255)

    private fun lerpPixel(from: Int, to: Int, t: Float): Int = PixelOps.argb(
        lerp(PixelOps.alphaOf(from), PixelOps.alphaOf(to), t),
        lerp(PixelOps.redOf(from), PixelOps.redOf(to), t),
        lerp(PixelOps.greenOf(from), PixelOps.greenOf(to), t),
        lerp(PixelOps.blueOf(from), PixelOps.blueOf(to), t),
    )

    private fun lerp(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt().coerceIn(0, 255)
}
