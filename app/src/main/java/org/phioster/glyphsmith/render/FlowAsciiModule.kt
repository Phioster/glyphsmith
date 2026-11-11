package org.phioster.glyphsmith.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Flow ASCII: characters or hairs in rows that follow the forms of the picture.
 *
 * Where the marks go is decided by [FlowAscii] on a working picture of fixed size, so a preview
 * and an export set the same marks and differ only in how large they are drawn. This draws them.
 */
object FlowAsciiModule : RenderModule {

    /** Output pixels per working cell, at most: past this the characters only get blurrier. */
    private const val MAX_SCALE = 6f
    private const val TEXT_SHARE = 1.15f
    private const val HAIR_WIDTH = 0.5f
    private const val DEGREES = 57.29578f

    override fun render(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: RenderSettings,
        maxSide: Int,
    ): ModuleRender {
        val cell = FlowAscii.cellFor(sourceWidth, sourceHeight)
        val grid = CellSampler.sample(pixels, sourceWidth, sourceHeight, params, cell, cell)
        val marks = FlowAscii.marks(grid, params)

        val scale = scaleFor(grid.cols, grid.rows, maxSide)
        val bitmap = draw(marks, scale, params)
        val exportScale = scaleFor(grid.cols, grid.rows, RenderBudget.MAX_OUTPUT_SIDE)
        return ModuleRender(
            bitmap = bitmap,
            cols = grid.cols,
            rows = grid.rows,
            outputWidth = max(1, (grid.cols * exportScale).toInt()),
            outputHeight = max(1, (grid.rows * exportScale).toInt()),
        )
    }

    /** As large as [maxSide] allows, inside the pixel budget every export keeps to. */
    private fun scaleFor(cols: Int, rows: Int, maxSide: Int): Float {
        val fill = maxSide.toFloat() / max(cols, rows)
        val budget = sqrt(RenderBudget.MAX_OUTPUT_PIXELS.toFloat() / (cols.toFloat() * rows))
        return min(min(fill, budget), MAX_SCALE).coerceAtLeast(0.25f)
    }

    private fun draw(marks: FlowMarks, scale: Float, params: RenderSettings): Bitmap {
        val width = max(1, (marks.cols * scale).toInt())
        val height = max(1, (marks.rows * scale).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (!params.transparentBackground) bitmap.eraseColor(params.backgroundColor or OPAQUE)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = marks.size * scale * TEXT_SHARE
            textAlign = Paint.Align.CENTER
            strokeWidth = max(1f, scale * HAIR_WIDTH)
            strokeCap = Paint.Cap.ROUND
        }
        // Text is drawn from its baseline; this lifts a character so its middle sits on the mark.
        val lift = -(paint.ascent() + paint.descent()) / 2f
        // One string per character, made once: drawText wants a string, and there are few characters.
        val texts = HashMap<Char, String>()
        for (i in 0 until marks.count) {
            val x = marks.x[i] * scale
            val y = marks.y[i] * scale
            paint.color = marks.color[i]
            val glyph = marks.glyph[i]
            if (glyph == FlowMarks.HAIR) {
                val dx = cos(marks.angle[i]) * marks.half[i] * scale
                val dy = sin(marks.angle[i]) * marks.half[i] * scale
                canvas.drawLine(x - dx, y - dy, x + dx, y + dy, paint)
            } else {
                val text = texts.getOrPut(glyph) { glyph.toString() }
                val turn = marks.angle[i] * DEGREES
                if (turn == 0f) {
                    canvas.drawText(text, x, y + lift, paint)
                } else {
                    canvas.save()
                    canvas.rotate(turn, x, y)
                    canvas.drawText(text, x, y + lift, paint)
                    canvas.restore()
                }
            }
        }
        return bitmap
    }

    private const val OPAQUE = 0xFF000000.toInt()
}
