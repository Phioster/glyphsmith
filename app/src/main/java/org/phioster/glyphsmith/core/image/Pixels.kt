package org.phioster.glyphsmith.core.image

import android.graphics.Bitmap
import org.phioster.glyphsmith.core.pipeline.BufferPool

/**
 * A loose pixel buffer — the currency every effect in this package trades in.
 *
 * Carries the [BufferPool] of the render it belongs to, so an effect that needs a second buffer
 * asks [buffer] for one instead of allocating. That is the whole opt-in: one line per effect, and
 * the pool travels with the data rather than through every signature.
 */
class Pixels(
    val data: IntArray,
    val width: Int,
    val height: Int,
    private val pool: BufferPool? = null,
) {

    fun copy(): Pixels = Pixels(data.copyOf(), width, height, pool)

    fun toBitmap(): Bitmap = Bitmap.createBitmap(data, width, height, Bitmap.Config.ARGB_8888)

    /** A working buffer, pooled when this render has a pool. Contents are undefined. */
    fun buffer(size: Int = data.size): IntArray = pool?.take(size) ?: IntArray(size)

    /** Another [Pixels] from the same render, so the pool is not lost along the chain. */
    fun derive(data: IntArray, width: Int = this.width, height: Int = this.height): Pixels =
        Pixels(data, width, height, pool)

    companion object {
        fun of(bitmap: Bitmap, pool: BufferPool? = null): Pixels {
            val data = pool?.take(bitmap.width * bitmap.height)
                ?: IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return Pixels(data, bitmap.width, bitmap.height, pool)
        }
    }
}
