package org.phioster.glyphsmith.anim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The LZW writer is the one piece of this app that can't be eyeballed — a subtly wrong bit
 * stream still produces a file, it just decodes to garbage. So the test decodes it back.
 */
class GifEncoderTest {

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun encode(frames: List<IntArray>, width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            GifEncoder.encode(frames, width, height, delayCentis = 8, out = out)
            out.toByteArray()
        }

    @Test
    fun `writes a well formed GIF89a container`() {
        val frame = IntArray(16) { red }
        val bytes = encode(listOf(frame), 4, 4)

        assertEquals("GIF89a", String(bytes, 0, 6, Charsets.US_ASCII))
        assertEquals("missing trailer", 0x3B.toByte(), bytes.last())
        assertTrue(
            "missing the loop extension",
            String(bytes, Charsets.ISO_8859_1).contains("NETSCAPE2.0"),
        )
    }

    @Test
    fun `a flat frame survives the round trip`() {
        val frame = IntArray(64) { green }
        val decoded = decodeFirstFrame(encode(listOf(frame), 8, 8))
        assertArrayEquals(frame, decoded)
    }

    @Test
    fun `a patterned frame survives the round trip`() {
        // Stripes and a diagonal: enough repetition for LZW to build a dictionary, enough
        // variation that an off-by-one in the bit packing would show up.
        val width = 16
        val height = 16
        val frame = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            when {
                x == y -> blue
                y % 2 == 0 -> red
                else -> green
            }
        }
        val decoded = decodeFirstFrame(encode(listOf(frame), width, height))
        assertArrayEquals(frame, decoded)
    }

    @Test
    fun `a frame larger than one sub-block survives the round trip`() {
        // Over 255 bytes of compressed data, so the sub-block framing gets exercised.
        val width = 64
        val height = 64
        val frame = IntArray(width * height) { i ->
            when ((i * 7 + i / width * 13) % 3) {
                0 -> red
                1 -> green
                else -> blue
            }
        }
        val decoded = decodeFirstFrame(encode(listOf(frame), width, height))
        assertArrayEquals(frame, decoded)
    }

    @Test
    fun `every frame is written`() {
        val frames = listOf(IntArray(16) { red }, IntArray(16) { green }, IntArray(16) { blue })
        val bytes = encode(frames, 4, 4)
        // One image descriptor (0x2C) per frame.
        assertEquals(3, bytes.count { it == 0x2C.toByte() })
    }

    @Test
    fun `transparent pixels get their own palette slot`() {
        val frame = IntArray(16) { if (it < 8) red else 0x00000000 }
        val decoded = decodeFirstFrameRaw(encode(listOf(frame), 4, 4))
        // The transparent half must not be mapped onto the red entry.
        assertTrue(decoded.indices.take(8).map { decoded[it] }.distinct().size == 1)
        assertTrue(decoded[0] != decoded[15])
    }

    // --- a minimal GIF reader, just enough to verify what we wrote --------------------

    private fun decodeFirstFrame(bytes: ByteArray): IntArray {
        val reader = Reader(bytes)
        val palette = reader.readHeader()
        val indices = reader.readFirstImage()
        return IntArray(indices.size) { 0xFF000000.toInt() or palette[indices[it]] }
    }

    private fun decodeFirstFrameRaw(bytes: ByteArray): IntArray {
        val reader = Reader(bytes)
        reader.readHeader()
        return reader.readFirstImage()
    }

    private class Reader(private val bytes: ByteArray) {
        private var pos = 0

        private fun u8(): Int = bytes[pos++].toInt() and 0xFF
        private fun u16(): Int = u8() or (u8() shl 8)

        fun readHeader(): IntArray {
            pos = 6
            u16(); u16()
            val packed = u8()
            u8(); u8()
            val size = 1 shl ((packed and 0x07) + 1)
            return IntArray(size) { (u8() shl 16) or (u8() shl 8) or u8() }
        }

        fun readFirstImage(): IntArray {
            while (true) {
                when (u8()) {
                    0x21 -> {
                        u8() // extension label
                        skipBlocks()
                    }

                    0x2C -> {
                        u16(); u16()
                        val width = u16()
                        val height = u16()
                        u8() // no local table in what we write
                        val minCodeSize = u8()
                        val data = readBlocks()
                        return lzwDecode(data, minCodeSize, width * height)
                    }

                    else -> error("unexpected block")
                }
            }
        }

        private fun skipBlocks() {
            while (true) {
                val length = u8()
                if (length == 0) return
                pos += length
            }
        }

        private fun readBlocks(): ByteArray {
            val out = ByteArrayOutputStream()
            while (true) {
                val length = u8()
                if (length == 0) return out.toByteArray()
                out.write(bytes, pos, length)
                pos += length
            }
        }

        private fun lzwDecode(data: ByteArray, minCodeSize: Int, expected: Int): IntArray {
            val clear = 1 shl minCodeSize
            val eoi = clear + 1
            var codeSize = minCodeSize + 1
            var dictionary = ArrayList<IntArray>()

            fun reset() {
                dictionary = ArrayList()
                for (i in 0 until clear) dictionary.add(intArrayOf(i))
                dictionary.add(IntArray(0)) // clear
                dictionary.add(IntArray(0)) // end of information
                codeSize = minCodeSize + 1
            }
            reset()

            val out = ArrayList<Int>(expected)
            var bitPos = 0
            var previous: IntArray? = null

            while (bitPos + codeSize <= data.size * 8) {
                var code = 0
                for (i in 0 until codeSize) {
                    val index = bitPos + i
                    val bit = (data[index ushr 3].toInt() shr (index and 7)) and 1
                    code = code or (bit shl i)
                }
                bitPos += codeSize

                if (code == clear) {
                    reset()
                    previous = null
                    continue
                }
                if (code == eoi) break

                val entry = when {
                    code < dictionary.size -> dictionary[code]
                    // The KwKwK case: the encoder used a code it defined on this very step.
                    previous != null -> previous + previous[0]
                    else -> error("bad first code")
                }
                entry.forEach { out.add(it) }

                if (previous != null) {
                    dictionary.add(previous + entry[0])
                    if (dictionary.size > (1 shl codeSize) - 1 && codeSize < 12) codeSize++
                }
                previous = entry
            }
            return out.toIntArray()
        }
    }
}
