package org.phioster.glyphsmith.pipeline

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
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

    /**
     * Pictures that are a loop of frames — light handed over moving — and how many each has.
     * The frames stay on file and are fetched as they are needed: an animation asks for them
     * in order, so a few at hand are enough, and a whole loop held would be most of the heap.
     */
    private val loops = ConcurrentHashMap<String, Int>()
    private val recent = LinkedHashMap<String, Picture>()

    /** Fetches frame [Int] of the loop named; set by whoever keeps the files. */
    @Volatile
    var frameLoader: ((String, Int) -> Picture?)? = null

    fun setLoop(id: String, frames: Int) {
        if (frames > 1) loops[id] = frames else loops.remove(id)
        synchronized(recent) { recent.keys.removeAll { it.startsWith("$id/") } }
    }

    /** How many frames the picture [id] has; 1 for a still. */
    fun frames(id: String): Int = loops[id] ?: 1

    /** Which of [count] frames shows at [time] of a loop; the loop runs once over 0..1 and wraps. */
    fun frameAt(time: Float, count: Int): Int {
        if (count <= 1 || time.isNaN()) return 0
        return ((time - floor(time)) * count).toInt().coerceIn(0, count - 1)
    }

    /**
     * The picture [id] as it stands at [time] of the loop, or the still it also is. A moment
     * between two frames is the two mixed: an animation here seldom has as many frames as the
     * loop, and the nearest frame alone held one twice and the next once, which stuttered.
     */
    fun at(id: String, time: Float): Picture? {
        val count = loops[id] ?: return held[id]
        if (time.isNaN()) return frame(id, 0) ?: held[id]
        val exact = (time - floor(time)) * count
        val index = exact.toInt().coerceIn(0, count - 1)
        val first = frame(id, index) ?: return held[id]
        val share = exact - index
        if (share < NEAR_ENOUGH) return first
        val second = frame(id, (index + 1) % count) ?: return first
        return mixed(first, second, share)
    }

    private fun frame(id: String, index: Int): Picture? {
        val key = "$id/$index"
        synchronized(recent) { recent[key] }?.let { return it }
        val loaded = frameLoader?.invoke(id, index) ?: return null
        synchronized(recent) {
            recent[key] = loaded
            while (recent.size > RECENT_FRAMES) recent.remove(recent.keys.first())
        }
        return loaded
    }

    /**
     * [a] with [share] (0..1) of [b] in it. Colour is weighed by how solid each pixel is, so
     * light fading in does not drag the black of an empty pixel along with it.
     */
    fun mixed(a: Picture, b: Picture, share: Float): Picture {
        if (a.width != b.width || a.height != b.height) return a
        val t = share.coerceIn(0f, 1f)
        val out = IntArray(a.pixels.size)
        for (i in out.indices) {
            val p = a.pixels[i]
            val q = b.pixels[i]
            if (p == q) {
                out[i] = p
                continue
            }
            val pa = (p ushr 24) * (1f - t)
            val qa = (q ushr 24) * t
            val alpha = pa + qa
            if (alpha < 0.5f) continue
            val r = ((p shr 16 and 0xFF) * pa + (q shr 16 and 0xFF) * qa) / alpha
            val g = ((p shr 8 and 0xFF) * pa + (q shr 8 and 0xFF) * qa) / alpha
            val bl = ((p and 0xFF) * pa + (q and 0xFF) * qa) / alpha
            out[i] = ((alpha + 0.5f).toInt().coerceAtMost(OPAQUE) shl 24) or
                ((r + 0.5f).toInt() shl 16) or ((g + 0.5f).toInt() shl 8) or (bl + 0.5f).toInt()
        }
        return Picture(out, a.width, a.height)
    }

    /** So close to a frame that it is shown as it is. */
    private const val NEAR_ENOUGH = 0.04f

    private const val RECENT_FRAMES = 3

    fun put(id: String, picture: Picture) {
        held[id] = picture
    }

    fun has(id: String): Boolean = held.containsKey(id)

    /** Forgets every picture not named in [ids], so a removed layer stops costing memory. */
    fun keepOnly(ids: Collection<String>) {
        held.keys.retainAll(ids.toSet())
        loops.keys.retainAll(ids.toSet())
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
