package org.phioster.glyphsmith.anim

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * H.264 in MP4 via MediaCodec.
 *
 * Frames are fed through `getInputImage()` rather than a raw ByteBuffer: with a flexible
 * YUV format the actual plane layout differs between devices, and the Image API reports the
 * strides instead of leaving us to guess.
 */
object Mp4Encoder {

    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val TIMEOUT_US = 10_000L
    private const val BITS_PER_PIXEL_PER_FRAME = 0.25f

    /** H.264 needs even dimensions; odd ones are cropped by a pixel rather than padded. */
    fun evenSize(value: Int): Int = value - (value % 2)

    private const val TAG = "Mp4Encoder"

    /** Returns null on success, or a short reason to show the user. */
    fun encode(frames: List<Bitmap>, fps: Int, output: File): String? {
        if (frames.isEmpty()) return "no frames"
        val width = evenSize(frames.first().width)
        val height = evenSize(frames.first().height)
        if (width <= 0 || height <= 0) return "frame is too small"

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        return try {
            val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                )
                setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    (width * height * fps * BITS_PER_PIXEL_PER_FRAME).toInt().coerceAtLeast(1_000_000),
                )
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                // Every frame a keyframe: these clips are short and loop, and seeking to an
                // arbitrary frame should never depend on decoding the whole thing.
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val info = MediaCodec.BufferInfo()
            var frameIndex = 0

            fun drain(endOfStream: Boolean) {
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }

                        index >= 0 -> {
                            val buffer = codec.getOutputBuffer(index)
                            if (buffer != null && info.size > 0 && muxerStarted) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                muxer.writeSampleData(trackIndex, buffer, info)
                            }
                            codec.releaseOutputBuffer(index, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            while (frameIndex < frames.size) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val image = codec.getInputImage(inputIndex)
                    if (image != null) {
                        fillYuv(image.planes, frames[frameIndex], width, height)
                    } else {
                        // Some encoders hand back a plain buffer instead of an Image; write
                        // planar I420 by hand rather than dropping the frame silently.
                        val buffer = codec.getInputBuffer(inputIndex)
                            ?: return "no input buffer from the encoder"
                        fillPlanarI420(buffer, frames[frameIndex], width, height)
                    }
                    val presentationTimeUs = frameIndex * 1_000_000L / fps
                    codec.queueInputBuffer(inputIndex, 0, imageSize(width, height), presentationTimeUs, 0)
                    frameIndex++
                }
                drain(false)
            }

            val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US * 5)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(
                    inputIndex, 0, 0,
                    frames.size * 1_000_000L / fps,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
            }
            drain(true)
            if (!muxerStarted) "the encoder produced no output" else null
        } catch (e: Exception) {
            // Without this the only symptom was "nothing happened" — MediaCodec failures
            // are device-specific, so the message is worth surfacing rather than swallowing.
            Log.e(TAG, "encode failed at ${width}x$height", e)
            output.delete()
            e.message?.take(90) ?: e.javaClass.simpleName
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun imageSize(width: Int, height: Int) = width * height * 3 / 2

    /** Fallback path: tightly packed I420 straight into the codec's input buffer. */
    private fun fillPlanarI420(buffer: ByteBuffer, bitmap: Bitmap, width: Int, height: Int) {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, width, height)
        buffer.clear()

        for (i in 0 until width * height) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.put((((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235).toByte())
        }
        val chroma = ArrayList<Byte>(width * height / 4)
        for (y in 0 until height / 2) {
            for (x in 0 until width / 2) {
                val pixel = pixels[(y * 2) * width + x * 2]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                buffer.put((((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(16, 240).toByte())
                chroma.add((((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(16, 240).toByte())
            }
        }
        chroma.forEach { buffer.put(it) }
    }

    /** BT.601 ARGB → planar YUV, written through the plane strides the codec reports. */
    private fun fillYuv(planes: Array<android.media.Image.Plane>, bitmap: Bitmap, width: Int, height: Int) {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, width, height)

        writeLuma(planes[0], pixels, width, height)
        writeChroma(planes[1], planes[2], pixels, width, height)
    }

    private fun writeLuma(plane: android.media.Image.Plane, pixels: IntArray, width: Int, height: Int) {
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (y in 0 until height) {
            var offset = y * rowStride
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                buffer.put(offset, luma.coerceIn(16, 235).toByte())
                offset += pixelStride
            }
        }
    }

    private fun writeChroma(
        uPlane: android.media.Image.Plane,
        vPlane: android.media.Image.Plane,
        pixels: IntArray,
        width: Int,
        height: Int,
    ) {
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        for (y in 0 until height / 2) {
            var uOffset = y * uPlane.rowStride
            var vOffset = y * vPlane.rowStride
            for (x in 0 until width / 2) {
                // One chroma sample per 2×2 block, averaged so fine glyph detail doesn't
                // alias into colour fringes.
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val pixel = pixels[(y * 2 + dy) * width + (x * 2 + dx)]
                        r += (pixel shr 16) and 0xFF
                        g += (pixel shr 8) and 0xFF
                        b += pixel and 0xFF
                    }
                }
                r /= 4
                g /= 4
                b /= 4
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                uBuffer.put(uOffset, u.coerceIn(16, 240).toByte())
                vBuffer.put(vOffset, v.coerceIn(16, 240).toByte())
                uOffset += uPlane.pixelStride
                vOffset += vPlane.pixelStride
            }
        }
    }
}
