package org.phioster.glyphsmith.ascii

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * How much ink a glyph actually puts on the page.
 *
 * [CharacterSet] requires its glyphs to be ordered by increasing coverage, and everything
 * downstream leans on that — the engine maps luminance straight onto a ramp index. Until now
 * every ramp was ordered *by hand*, which is a guess, and for characters typed into Inject
 * Characters it could not even be a guess: those land at the dense end unmeasured.
 *
 * Measuring is straightforward — draw the glyph and count what it covered — and the answer
 * is exact for the face that will actually be used, which a hand-ordered list never is.
 *
 * Not unit-tested: it needs a real Typeface and a real Canvas, neither of which a JVM test
 * has. The pure part — what the app does with the resulting order — is tested through
 * [RenderSettings.effectiveRamp].
 */
object GlyphCoverage {

    /**
     * Cell the glyph is measured in. Large enough that thin strokes survive rasterisation,
     * small enough that measuring a 70-glyph ramp is not something the user waits for.
     */
    private const val CELL = 48
    private const val TEXT_SIZE = 36f

    /**
     * Read from [kotlinx.coroutines.Dispatchers.Default] by the preview and the preset thumbnails
     * at the same time, so a plain HashMap could resize under a concurrent read and lose an entry
     * or spin. Concurrent because the work is idempotent: two threads measuring the same glyph
     * get the same answer, and whichever stores it last is right.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, Float>()

    /** Covered fraction of the cell, 0..1. Cached per face and character. */
    fun measure(glyph: Char, typeface: Typeface): Float {
        val key = (typeface.hashCode().toLong() shl 32) or glyph.code.toLong()
        cache[key]?.let { return it }

        val coverage = rasterise(glyph, typeface)
        cache[key] = coverage
        return coverage
    }

    private fun rasterise(glyph: Char, typeface: Typeface): Float {
        // ALPHA_8 because only coverage matters; it is a quarter of the memory of ARGB and
        // the alpha channel is exactly the quantity being summed.
        val bitmap = Bitmap.createBitmap(CELL, CELL, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = TEXT_SIZE
            textAlign = Paint.Align.CENTER
            color = Color.BLACK
        }
        val metrics = paint.fontMetrics
        val baseline = CELL / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(glyph.toString(), CELL / 2f, baseline, paint)

        val pixels = IntArray(CELL * CELL)
        bitmap.getPixels(pixels, 0, CELL, 0, 0, CELL, CELL)
        bitmap.recycle()

        // Anti-aliased edges land between 0 and 255, so summing the alpha is a truer measure
        // of ink than counting pixels that happen to clear some threshold.
        var sum = 0L
        for (pixel in pixels) sum += (pixel ushr 24) and 0xFF
        return (sum.toFloat() / (pixels.size * 255f)).coerceIn(0f, 1f)
    }

    /**
     * The same characters, ordered lightest first — the order [CharacterSet] asks for.
     *
     * Duplicates are dropped: two identical glyphs occupy two ramp levels that render the
     * same, which is a wasted tonal step rather than a useful one.
     */
    fun sort(glyphs: String, typeface: Typeface): String =
        glyphs.toList()
            .distinct()
            .sortedBy { measure(it, typeface) }
            .joinToString("")

    /** Measured coverage per character, in the order given — what the ramp editor displays. */
    fun profile(glyphs: String, typeface: Typeface): List<Pair<Char, Float>> =
        glyphs.map { it to measure(it, typeface) }
}
