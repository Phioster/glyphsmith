package org.phioster.glyphsmith.ascii

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import org.phioster.glyphsmith.effects.PixelOps

/**
 * Stacks the extra layers over a rendered base.
 *
 * Each layer is rendered at the base's exact size and then transformed into place, rather
 * than rendered at its own size and scaled. That way a layer's glyph grid is sized against
 * the output it will actually occupy, and scaling it up afterwards enlarges the *glyphs*
 * rather than resampling a smaller picture of them — which is the difference between a
 * coarse layer and a blurry one.
 */
object LayerCompositor {

    fun composite(
        base: Bitmap,
        layers: List<Layer>,
        renderLayer: (Layer) -> Bitmap?,
    ): Bitmap {
        val active = layers.filter { it.enabled && it.opacity > 0 }
        if (active.isEmpty()) return base

        var result = base
        active.forEach { layer ->
            val rendered = renderLayer(layer) ?: return@forEach
            val placed = place(rendered, result.width, result.height, layer)
            rendered.recycle()
            result = blend(result, placed, layer)
            placed.recycle()
        }
        return result
    }

    /**
     * Draws the layer into a canvas the size of the base, with its transform applied about
     * its own centre. Anything outside the frame is simply lost, and the space it leaves
     * behind stays transparent so the layers below show through.
     */
    private fun place(source: Bitmap, width: Int, height: Int, layer: Layer): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = Matrix()

        val scale = layer.scale.coerceIn(Layer.SCALE_RANGE) / 100f
        val flipX = if (layer.flipHorizontal) -1f else 1f
        val flipY = if (layer.flipVertical) -1f else 1f

        // Order matters: flip and scale about the centre, then rotate about it, then move.
        // Translating first would make the rotation swing the layer around the frame instead
        // of turning it in place.
        matrix.postTranslate(-source.width / 2f, -source.height / 2f)
        matrix.postScale(scale * flipX, scale * flipY)
        matrix.postRotate(layer.rotation.toFloat())
        matrix.postTranslate(
            width / 2f + width * layer.offsetX / 100f,
            height / 2f + height * layer.offsetY / 100f,
        )

        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /**
     * Blends [top] onto [bottom] and returns the bottom, mutated.
     *
     * The layer's own alpha gates everything: where a layer drew nothing — the gaps between
     * glyphs, or the frame it was moved out of — it must leave what is underneath alone,
     * whatever the blend mode would otherwise do with black.
     */
    private fun blend(bottom: Bitmap, top: Bitmap, layer: Layer): Bitmap {
        val width = bottom.width
        val height = bottom.height
        val under = IntArray(width * height)
        val over = IntArray(width * height)
        bottom.getPixels(under, 0, width, 0, 0, width, height)
        top.getPixels(over, 0, width, 0, 0, width, height)

        val opacity = layer.opacity.coerceIn(Layer.OPACITY_RANGE) / 100f

        for (i in under.indices) {
            val src = over[i]
            val alpha = PixelOps.alphaOf(src) / 255f * opacity
            if (alpha <= 0f) continue
            val dst = under[i]
            under[i] = PixelOps.argb(
                maxOf(PixelOps.alphaOf(dst), (PixelOps.alphaOf(src) * opacity).toInt()),
                mix(PixelOps.redOf(dst), PixelOps.redOf(src), layer.blend, alpha),
                mix(PixelOps.greenOf(dst), PixelOps.greenOf(src), layer.blend, alpha),
                mix(PixelOps.blueOf(dst), PixelOps.blueOf(src), layer.blend, alpha),
            )
        }

        bottom.setPixels(under, 0, width, 0, 0, width, height)
        return bottom
    }

    private fun mix(dst: Int, src: Int, blend: LayerBlend, alpha: Float): Int {
        val b = dst / 255f
        val s = src / 255f
        val blended = when (blend) {
            LayerBlend.NORMAL -> s
            LayerBlend.MULTIPLY -> b * s
            LayerBlend.SCREEN -> 1f - (1f - b) * (1f - s)
            LayerBlend.OVERLAY -> if (b < 0.5f) 2f * b * s else 1f - 2f * (1f - b) * (1f - s)
            // Pegtop's soft light: continuous, and cheaper than the W3C piecewise form.
            LayerBlend.SOFT_LIGHT -> (1f - 2f * s) * b * b + 2f * s * b
            LayerBlend.ADD -> b + s
            LayerBlend.DIFFERENCE -> kotlin.math.abs(b - s)
        }
        val mixed = b + (blended.coerceIn(0f, 1f) - b) * alpha
        return (mixed * 255f).toInt().coerceIn(0, 255)
    }
}
