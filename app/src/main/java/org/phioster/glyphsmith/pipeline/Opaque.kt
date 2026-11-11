package org.phioster.glyphsmith.pipeline

/**
 * [pixels] laid over black, in place. A base picture has no transparency to keep, and a clear
 * pixel still carries a colour: read as it stands, a cut-out light shows as bands of nonsense.
 */
fun onBlack(pixels: IntArray): IntArray {
    for (i in pixels.indices) {
        val p = pixels[i]
        val a = p ushr 24
        if (a == 0xFF) continue
        val r = (p shr 16 and 0xFF) * a / 0xFF
        val g = (p shr 8 and 0xFF) * a / 0xFF
        val b = (p and 0xFF) * a / 0xFF
        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return pixels
}
