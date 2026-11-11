package org.phioster.glyphsmith.pipeline

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * The pictures layers bring with them, decoded and held by id.
 *
 * A layer names its picture rather than carrying it: settings travel as text and are copied on
 * every slider move. Whoever loads a picture puts it here, and the pipeline looks it up when it
 * renders that layer. A name with nothing behind it — a preset from another device — simply
 * leaves that layer out.
 */
object LayerImages {

    class Picture(val pixels: IntArray, val width: Int, val height: Int)

    private val held = ConcurrentHashMap<String, Picture>()

    operator fun get(id: String): Picture? = held[id]

    fun put(id: String, picture: Picture) {
        held[id] = picture
    }

    fun has(id: String): Boolean = held.containsKey(id)

    /** Forgets every picture not named in [ids], so a removed layer stops costing memory. */
    fun keepOnly(ids: Collection<String>) {
        held.keys.retainAll(ids.toSet())
    }

    /** The longest side a [width]×[height] picture may be rendered at to fit inside the frame. */
    fun sideInside(width: Int, height: Int, frameWidth: Int, frameHeight: Int): Int {
        val fit = min(frameWidth.toFloat() / width, frameHeight.toFloat() / height)
        return max(1, (max(width, height) * fit).toInt())
    }

    /**
     * Cuts a rendering back to its picture's silhouette: where the picture was transparent the
     * rendering becomes transparent too, whatever the dither and the effects drew there.
     */
    fun cutOut(rendered: IntArray, width: Int, height: Int, picture: Picture) {
        for (y in 0 until height) {
            val sy = (y.toLong() * picture.height / height).toInt().coerceIn(0, picture.height - 1)
            for (x in 0 until width) {
                val sx = (x.toLong() * picture.width / width).toInt().coerceIn(0, picture.width - 1)
                val alpha = picture.pixels[sy * picture.width + sx] ushr 24
                if (alpha == OPAQUE) continue
                val i = y * width + x
                val own = rendered[i] ushr 24
                rendered[i] = (min(own, alpha) shl 24) or (rendered[i] and 0xFFFFFF)
            }
        }
    }

    private const val OPAQUE = 0xFF
}
