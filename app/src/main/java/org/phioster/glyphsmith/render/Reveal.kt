package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * The photo coming through the look, block by block.
 *
 * At 0 the render is untouched; at 100 only the photo is left. In between, blocks give way one
 * after the other, scattered but leaning from the top down, and a block that is about to go
 * flashes first. Animated from 0 to 100 it is the reveal: the picture arrives as characters or
 * dots and resolves into itself.
 */
@Serializable
data class RevealParams(
    /** 0..100 — how much of the picture has come through. */
    val amount: Int = 0,
    /** 2..80 — the side of a block, in thousandths of the picture's width. */
    val block: Int = 16,
    val seed: Int = 1,
)

object Reveal {

    /** How much of the order is chance; the rest is the way down the picture. */
    private const val SCATTER = 0.65f

    /** The share of the way a block spends flashing before it gives way. */
    private const val FLASH = 0.05f
    private const val FLASH_LIFT = 70

    /**
     * [rendered] with the [source] photo let through, in place. The photo is read at the
     * rendering's size, whatever its own.
     */
    @Suppress("LongParameterList")
    fun apply(
        rendered: IntArray, width: Int, height: Int,
        source: IntArray, sourceWidth: Int, sourceHeight: Int,
        params: RevealParams,
    ): IntArray {
        val amount = params.amount.coerceIn(0, 100) / 100f
        if (amount <= 0f || width < 1 || height < 1 || sourceWidth < 1 || sourceHeight < 1) return rendered
        val side = (params.block.coerceIn(2, 80) * width / 1000f).roundToInt().coerceAtLeast(1)
        val rows = (height + side - 1) / side
        for (y in 0 until height) {
            val by = y / side
            val sy = ((y + 0.5f) / height * sourceHeight).toInt().coerceAtMost(sourceHeight - 1)
            for (x in 0 until width) {
                val at = SCATTER * noise(x / side, by, params.seed) + (1f - SCATTER) * by / rows
                // Scaled so that the last block has gone, flash and all, when the amount is full.
                val reach = amount * (1f + FLASH)
                if (reach <= at) continue
                val sx = ((x + 0.5f) / width * sourceWidth).toInt().coerceAtMost(sourceWidth - 1)
                val photo = source[sy * sourceWidth + sx] or (0xFF shl 24)
                rendered[y * width + x] = if (reach - at < FLASH) lifted(rendered[y * width + x]) else photo
            }
        }
        return rendered
    }

    private fun lifted(pixel: Int): Int {
        val r = ((pixel shr 16 and 0xFF) + FLASH_LIFT).coerceAtMost(255)
        val g = ((pixel shr 8 and 0xFF) + FLASH_LIFT).coerceAtMost(255)
        val b = ((pixel and 0xFF) + FLASH_LIFT).coerceAtMost(255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** 0 up to but not 1 from a block and a seed, the same on every device. */
    private fun noise(a: Int, b: Int, seed: Int): Float {
        var n = a * 374761393 + b * 668265263 + seed * 362437
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65536f
    }
}
