package org.phioster.glyphsmith.effects

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Databending, for real: encode to JPEG, corrupt bytes in the compressed scan, decode.
 *
 * The characteristic block smears come from the decoder losing its place in the entropy-coded
 * stream, so they can't be faked convincingly by drawing rectangles — but the same mechanism
 * means a decode can fail outright. Corruption therefore starts *after* the SOS marker (the
 * headers stay intact) and the whole thing retries with fewer damaged bytes before finally
 * giving the untouched image back.
 */
object JpegGlitch {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::jpegGlitch,
        { copy(jpegGlitch = it) },
        JpegGlitchParams::enabled,
        randomise = { roll -> copy(enabled = true, corruption = roll.random.nextInt(20, 140)) },
    ) { pixels, params, _ -> apply(pixels, params) }

    private const val MAX_ATTEMPTS = 4

    fun apply(source: Pixels, params: JpegGlitchParams): Pixels {
        if (!params.enabled || params.corruption <= 0) return source

        val bitmap = source.toBitmap()
        val encoded = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, params.quality.coerceIn(1, 100), stream)
            stream.toByteArray()
        }
        bitmap.recycle()

        val scanStart = scanStart(encoded)
        if (scanStart >= encoded.size - 4) return source

        var corruption = params.corruption
        repeat(MAX_ATTEMPTS) {
            val damaged = corrupt(encoded, scanStart, corruption, params)
            val decoded = BitmapFactory.decodeByteArray(damaged, 0, damaged.size)
            if (decoded != null) {
                val result = Pixels.of(decoded)
                decoded.recycle()
                return restoreAlpha(source, result)
            }
            corruption /= 2
            if (corruption <= 0) return source
        }
        return source
    }

    /** Byte after the start-of-scan header — everything before it must stay readable. */
    private fun scanStart(data: ByteArray): Int {
        var i = 2
        while (i + 3 < data.size) {
            if (data[i] != 0xFF.toByte()) {
                i++
                continue
            }
            val marker = data[i + 1].toInt() and 0xFF
            if (marker == 0xDA) {
                val length = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                return i + 2 + length
            }
            i++
        }
        return data.size / 4
    }

    private fun corrupt(
        source: ByteArray,
        scanStart: Int,
        corruption: Int,
        params: JpegGlitchParams,
    ): ByteArray {
        val data = source.copyOf()
        val random = Random(params.seed)
        val skip = ((data.size - scanStart) * (params.startOffset / 100f)).toInt()
        val from = (scanStart + skip).coerceAtMost(data.size - 2)
        val span = (data.size - 2) - from
        if (span <= 0) return data

        repeat(corruption) {
            val index = from + random.nextInt(span)
            // 0xFF would look like a marker and end the scan early; keep the damage inside
            // the entropy-coded data instead of restructuring the file.
            var value = random.nextInt(256)
            if (value == 0xFF) value = 0xFE
            data[index] = value.toByte()
        }
        return data
    }

    /** JPEG has no alpha; put the original's back so a transparent background survives. */
    private fun restoreAlpha(original: Pixels, decoded: Pixels): Pixels {
        if (decoded.width != original.width || decoded.height != original.height) return decoded
        for (i in decoded.data.indices) {
            val alpha = PixelOps.alphaOf(original.data[i])
            decoded.data[i] = (decoded.data[i] and 0x00FFFFFF) or (alpha shl 24)
        }
        return decoded
    }
}
