package org.phioster.glyphsmith.effects

import android.graphics.Bitmap

/**
 * Runs the whole chain over a rendered bitmap, in the order the stack asks for.
 *
 * Everything works on one [Pixels] buffer that is handed from effect to effect, so an
 * enabled-but-neutral effect costs a function call rather than a bitmap copy. Effects that
 * can't work in place (channel separation, JPEG round-trip) return a new buffer; the rest
 * mutate and return the same one — which is why the loop below always reassigns.
 */
object EffectPipeline {

    fun apply(bitmap: Bitmap, stack: EffectStack): Bitmap {
        if (stack.activeCount == 0) return bitmap

        var pixels = Pixels.of(bitmap)
        for (id in stack.effectiveOrder()) {
            pixels = step(pixels, id, stack)
        }

        bitmap.recycle()
        return pixels.toBitmap()
    }

    private fun step(pixels: Pixels, id: EffectId, stack: EffectStack): Pixels = when (id) {
        EffectId.POST -> PostProcessing.apply(pixels, stack.postProcessing)
        EffectId.BLUR -> BlurSharpen.apply(pixels, stack.blurSharpen)
        EffectId.TINT -> Tint.apply(pixels, stack.tint)
        EffectId.CHROMATIC -> Chromatic.apply(pixels, stack.chromatic)
        EffectId.GLITCH -> JpegGlitch.apply(pixels, stack.jpegGlitch)
        EffectId.STARS -> DiffractionStars.apply(pixels, stack.stars)
        EffectId.GLOW -> EpsilonGlow.apply(pixels, stack.glow)
    }
}
