package org.phioster.glyphsmith.effects

import android.graphics.Bitmap

/**
 * Runs the whole chain over a rendered bitmap.
 *
 * Everything works on one [Pixels] buffer that is handed from effect to effect, so an
 * enabled-but-neutral effect costs a function call rather than a bitmap copy. Effects that
 * can't work in place (channel separation, JPEG round-trip) return a new buffer; the rest
 * mutate and return the same one.
 */
object EffectPipeline {

    fun apply(bitmap: Bitmap, stack: EffectStack): Bitmap {
        if (stack.activeCount == 0) return bitmap

        var pixels = Pixels.of(bitmap)
        pixels = PostProcessing.apply(pixels, stack.postProcessing)
        pixels = Tint.apply(pixels, stack.tint)
        pixels = Chromatic.apply(pixels, stack.chromatic)
        pixels = JpegGlitch.apply(pixels, stack.jpegGlitch)
        pixels = DiffractionStars.apply(pixels, stack.stars)
        pixels = EpsilonGlow.apply(pixels, stack.glow)

        bitmap.recycle()
        return pixels.toBitmap()
    }
}
