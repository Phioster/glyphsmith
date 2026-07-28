package org.phioster.glyphsmith.ascii

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.max

/** Glyph cell geometry for a given font size and ramp. */
data class CellMetrics(
    val width: Int,
    val height: Int,
    val baseline: Float,
    /** height / width — fed back into the engine so sampling matches the output grid. */
    val aspect: Float,
)

/**
 * Paints a character grid onto a bitmap.
 *
 * The cell is sized by the *widest* glyph in the active ramp, so sets with wide glyphs
 * (CJK, braille) still land on a true grid instead of drifting out of column.
 */
object AsciiRenderer {

    /** Hard ceiling on either output dimension — beyond this a bitmap allocation fails. */
    const val MAX_OUTPUT_SIDE = 8192

    private fun typefaceFor(style: FontStyle): Typeface = Typeface.create(
        Typeface.MONOSPACE,
        when (style) {
            FontStyle.REGULAR -> Typeface.NORMAL
            FontStyle.BOLD -> Typeface.BOLD
            FontStyle.ITALIC -> Typeface.ITALIC
            FontStyle.BOLD_ITALIC -> Typeface.BOLD_ITALIC
        },
    )

    private fun paintFor(fontSizePx: Float, style: FontStyle) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = typefaceFor(style)
        textSize = fontSizePx
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }

    fun metrics(fontSizePx: Int, ramp: String, style: FontStyle = FontStyle.REGULAR): CellMetrics {
        val paint = paintFor(fontSizePx.toFloat(), style)
        val widths = FloatArray(1)
        var widest = 0f
        for (glyph in ramp.toSet()) {
            paint.getTextWidths(glyph.toString(), widths)
            widest = max(widest, widths[0])
        }
        if (widest <= 0f) widest = fontSizePx * 0.6f
        val fm = paint.fontMetrics
        val width = ceil(widest).toInt().coerceAtLeast(1)
        val height = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        return CellMetrics(width, height, -fm.ascent, height.toFloat() / width)
    }

    /**
     * Largest font size at which a [cols]×[rows] grid still fits inside [maxSide], never
     * above [requested]. Used both to keep previews cheap and to stop a huge grid from
     * asking for a bitmap Android can't allocate.
     */
    fun fitFontSize(
        cols: Int,
        rows: Int,
        ramp: String,
        requested: Int,
        maxSide: Int,
        style: FontStyle = FontStyle.REGULAR,
    ): Int {
        var size = requested.coerceIn(AsciiParams.FONT_SIZE_RANGE)
        while (size > AsciiParams.FONT_SIZE_RANGE.first) {
            val cell = metrics(size, ramp, style)
            if (cols * cell.width <= maxSide && rows * cell.height <= maxSide) return size
            size--
        }
        return AsciiParams.FONT_SIZE_RANGE.first
    }

    fun render(art: AsciiArt, params: AsciiParams, fontSizePx: Int): Bitmap {
        val ramp = params.effectiveRamp().ifEmpty { " " }
        val size = fitFontSize(art.cols, art.rows, ramp, fontSizePx, MAX_OUTPUT_SIDE, params.fontStyle)
        val cell = metrics(size, ramp, params.fontStyle)
        val width = (art.cols * cell.width).coerceIn(1, MAX_OUTPUT_SIDE)
        val height = (art.rows * cell.height).coerceIn(1, MAX_OUTPUT_SIDE)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(
            if (params.transparentBackground) Color.TRANSPARENT else params.backgroundColor,
        )

        val paint = paintFor(size.toFloat(), params.fontStyle)
        // measureText per cell would dominate the render; the ramp is small, so cache it.
        val widthCache = HashMap<Char, Float>(ramp.length * 2)
        val single = CharArray(1)
        val widths = FloatArray(1)

        for (row in 0 until art.rows) {
            val y = row * cell.height + cell.baseline
            for (col in 0 until art.cols) {
                val glyph = art.glyphAt(col, row)
                if (glyph == ' ') continue
                paint.color = art.colorAt(col, row) ?: params.inkColor
                val glyphWidth = widthCache.getOrPut(glyph) {
                    single[0] = glyph
                    paint.getTextWidths(single, 0, 1, widths)
                    widths[0]
                }
                val x = col * cell.width + (cell.width - glyphWidth) / 2f
                single[0] = glyph
                canvas.drawText(single, 0, 1, x, y, paint)
            }
        }
        return bitmap
    }
}
