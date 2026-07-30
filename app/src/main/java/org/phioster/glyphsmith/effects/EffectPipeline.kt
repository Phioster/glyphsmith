package org.phioster.glyphsmith.effects

import android.graphics.Bitmap
import org.phioster.glyphsmith.core.pipeline.NodePipeline
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.core.image.Pixels

/**
 * The bitmap-facing edge of the effect chain.
 *
 * The chain itself is [NodePipeline] over the nodes [EffectNodes] builds; all this adds is the
 * conversion at either end, which is the only part that needs an Android type. Keeping the
 * conversion here rather than inside the chain is what lets every effect stay unit-testable.
 *
 * Effects that can't work in place (channel separation, JPEG round-trip) return a new buffer;
 * the rest mutate and return the same one, and the chain reassigns either way.
 *
 * The empty-stack case returns the bitmap untouched — not a copy, and not recycled, because
 * the caller goes on to use it.
 */
object EffectPipeline {

    fun apply(bitmap: Bitmap, stack: EffectStack, ctx: RenderContext): Bitmap {
        if (stack.activeCount == 0) return bitmap

        val out = NodePipeline.run(Pixels.of(bitmap, ctx.pool), EffectNodes.of(stack), ctx)

        // The pixels were copied into the buffer on the way in, so nothing is lost by
        // releasing the bitmap here. It never left this function, which is what makes that
        // safe — a bitmap that has reached the UI must never be recycled.
        bitmap.recycle()
        return out.toBitmap()
    }
}
