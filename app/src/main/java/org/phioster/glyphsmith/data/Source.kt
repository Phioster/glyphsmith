package org.phioster.glyphsmith.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.roundToLong
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Where the pixels come from.
 *
 * A still and a video differ only in whether [pixelsAt] cares about the position it is
 * given, which is why the rest of the app can treat them the same: the preview asks for one
 * position, an export walks the loop asking for each in turn, and neither has to know what
 * it is looking at.
 */
sealed interface Source {
    val width: Int
    val height: Int

    /** True when the position actually selects something — that is, when this is a video. */
    val isMoving: Boolean

    /** Pixels at a normalised position in 0..1. A still ignores it and returns the image. */
    fun pixelsAt(position: Float): IntArray

    fun close()
}

class StillSource(
    private val pixels: IntArray,
    override val width: Int,
    override val height: Int,
) : Source {
    override val isMoving: Boolean get() = false
    override fun pixelsAt(position: Float): IntArray = pixels
    override fun close() = Unit
}

/**
 * A video, decoded one frame at a time on demand.
 *
 * Frames are *not* held in memory. A 60-frame clip at 720² would be 124 MB of `IntArray`,
 * and the render pipeline needs its own headroom on top of that. Decoding on demand costs a
 * seek per frame, which an offline export can afford and a preview never notices because of
 * the single-frame cache below — during a slider drag the same position is asked for over
 * and over.
 *
 * [MediaMetadataRetriever] rather than MediaCodec: it seeks to arbitrary positions, applies
 * the file's rotation itself, and works the same on every device. The cost is speed, and for
 * a few dozen frames behind a progress line that is the right trade.
 */
class VideoSource private constructor(
    private val retriever: MediaMetadataRetriever,
    override val width: Int,
    override val height: Int,
    private val durationUs: Long,
) : Source {

    override val isMoving: Boolean get() = true

    private var cachedKey = Long.MIN_VALUE
    private var cached: IntArray? = null
    private var closed = false

    override fun pixelsAt(position: Float): IntArray {
        val timeUs = (durationUs * position.coerceIn(0f, 1f).toDouble()).roundToLong()
            .coerceIn(0L, durationUs)
        cached?.let { if (timeUs == cachedKey) return it }

        val frame = decode(timeUs) ?: return cached ?: IntArray(width * height)
        val pixels = IntArray(width * height)
        // The decoder is asked for our exact size, but a codec may round to its own
        // alignment, so the frame is read through its own dimensions and never assumed.
        val w = minOf(frame.width, width)
        val h = minOf(frame.height, height)
        frame.getPixels(pixels, 0, width, 0, 0, w, h)
        frame.recycle()

        cachedKey = timeUs
        cached = pixels
        return pixels
    }

    private fun decode(timeUs: Long): Bitmap? {
        if (closed) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    width,
                    height,
                )
            } else {
                // OPTION_CLOSEST rather than CLOSEST_SYNC: snapping to keyframes would
                // repeat frames wherever the clip has a long GOP, which on a short loop is
                // the difference between motion and a slideshow.
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?.let { frame ->
                        val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
                        if (scaled != frame) frame.recycle()
                        scaled
                    }
            }
        }.getOrNull()
    }

    override fun close() {
        if (closed) return
        closed = true
        cached = null
        runCatching { retriever.release() }
    }

    companion object {
        /** Longest side the frames are decoded at — the grid samples far below this anyway. */
        const val MAX_SOURCE_SIDE = 1080

        /**
         * Opens [uri] as a video, or returns null when it holds no decodable video track.
         *
         * The size comes from an actually decoded frame rather than from the metadata,
         * because the retriever rotates what it hands back while `VIDEO_WIDTH` reports the
         * stored orientation. Trusting the metadata puts every portrait clip on its side.
         */
        fun open(context: Context, uri: Uri, maxSide: Int = MAX_SOURCE_SIDE): VideoSource? {
            val retriever = MediaMetadataRetriever()
            return runCatching {
                retriever.setDataSource(context, uri)
                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val probe = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: error("no decodable frame")

                val scale = minOf(1f, maxSide.toFloat() / max(probe.width, probe.height))
                val width = max(1, (probe.width * scale).toInt())
                val height = max(1, (probe.height * scale).toInt())
                probe.recycle()

                VideoSource(retriever, width, height, durationMs * 1000L)
            }.getOrElse {
                runCatching { retriever.release() }
                null
            }
        }
    }
}
