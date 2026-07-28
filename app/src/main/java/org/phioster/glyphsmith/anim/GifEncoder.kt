package org.phioster.glyphsmith.anim

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * A GIF89a writer with LZW compression — pure Kotlin, no Android types, so the whole
 * animation export path can be unit-tested on the JVM.
 *
 * One global palette is built across every frame so colours can't shift mid-loop, and a
 * reserved index carries transparency when the render has a transparent background.
 */
object GifEncoder {

    private const val MAX_COLORS = 256
    private const val MAX_CODE = 4095
    private const val BLOCK_SIZE = 255

    /**
     * @param delayCentis frame delay in hundredths of a second — GIF's native unit.
     * @param loop true for an endless loop (the Netscape extension).
     */
    fun encode(
        frames: List<IntArray>,
        width: Int,
        height: Int,
        delayCentis: Int,
        out: OutputStream,
        loop: Boolean = true,
    ) {
        require(frames.isNotEmpty()) { "nothing to encode" }
        require(width > 0 && height > 0) { "empty canvas" }

        val hasTransparency = frames.any { frame -> frame.any { !ColorQuantizer.isOpaque(it) } }
        // Transparency costs one palette slot, so the colour palette gets one fewer.
        val colorLimit = if (hasTransparency) MAX_COLORS - 1 else MAX_COLORS
        val colors = ColorQuantizer.palette(frames, colorLimit)
        val transparentIndex = if (hasTransparency) colors.size else -1
        val paletteSize = colors.size + if (hasTransparency) 1 else 0

        // GIF colour tables are a power of two, at least two entries.
        var bits = 1
        while ((1 shl bits) < paletteSize) bits++
        val tableSize = 1 shl bits

        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x80 or (bits - 1)) // global table present, `bits` bits per entry
        out.write(0) // background colour index
        out.write(0) // no pixel aspect ratio

        for (i in 0 until tableSize) {
            val color = colors.getOrElse(i) { 0 }
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }

        if (loop) writeNetscapeLoop(out)

        val mapper = ColorQuantizer.Mapper(colors)
        frames.forEach { frame ->
            writeGraphicControl(out, delayCentis, transparentIndex)
            writeImageDescriptor(out, width, height)
            val indices = ByteArray(frame.size) { i ->
                val pixel = frame[i]
                if (!ColorQuantizer.isOpaque(pixel) && transparentIndex >= 0) {
                    transparentIndex.toByte()
                } else {
                    mapper.indexOf(pixel).toByte()
                }
            }
            writeLzw(out, indices, maxOf(2, bits))
        }

        out.write(0x3B) // trailer
        out.flush()
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun writeNetscapeLoop(out: OutputStream) {
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShort(out, 0) // 0 = forever
        out.write(0)
    }

    private fun writeGraphicControl(out: OutputStream, delayCentis: Int, transparentIndex: Int) {
        out.write(0x21)
        out.write(0xF9)
        out.write(4)
        // Disposal 2 (restore to background) so a transparent frame doesn't smear onto the next.
        val disposal = if (transparentIndex >= 0) 2 else 1
        out.write((disposal shl 2) or if (transparentIndex >= 0) 1 else 0)
        writeShort(out, delayCentis.coerceAtLeast(1))
        out.write(if (transparentIndex >= 0) transparentIndex else 0)
        out.write(0)
    }

    private fun writeImageDescriptor(out: OutputStream, width: Int, height: Int) {
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        out.write(0) // no local colour table, not interlaced
    }

    /**
     * GIF's variable-width LZW. Codes are packed least-significant-bit first and emitted in
     * sub-blocks of at most 255 bytes, which is the format's own framing.
     */
    private fun writeLzw(out: OutputStream, indices: ByteArray, minCodeSize: Int) {
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1

        val packed = ByteArrayOutputStream()
        var bitBuffer = 0
        var bitCount = 0

        fun emit(code: Int, codeSize: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                packed.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        var codeSize = minCodeSize + 1
        var nextCode = endCode + 1
        var dictionary = HashMap<Int, Int>()

        emit(clearCode, codeSize)

        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val next = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or next
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            emit(prefix, codeSize)
            if (nextCode <= MAX_CODE) {
                dictionary[key] = nextCode
                nextCode++
                // Grow once the next code no longer fits in the current width.
                if (nextCode > (1 shl codeSize) - 1 && codeSize < 12) codeSize++
            } else {
                emit(clearCode, codeSize)
                dictionary = HashMap()
                codeSize = minCodeSize + 1
                nextCode = endCode + 1
            }
            prefix = next
        }
        emit(prefix, codeSize)
        emit(endCode, codeSize)
        if (bitCount > 0) packed.write(bitBuffer and 0xFF)

        val bytes = packed.toByteArray()
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(BLOCK_SIZE, bytes.size - offset)
            out.write(length)
            out.write(bytes, offset, length)
            offset += length
        }
        out.write(0) // block terminator
    }
}
