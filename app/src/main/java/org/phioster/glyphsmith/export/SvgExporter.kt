package org.phioster.glyphsmith.export

import android.graphics.Path
import android.graphics.PathMeasure
import org.phioster.glyphsmith.glyph.GlyphGrid
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.glyph.GlyphRenderer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

enum class SvgMode(val label: String) {
    TEXT("text"),
    OUTLINES("outlines"),
}

/**
 * The character grid as SVG.
 *
 * This is the one export where the vectors are *exact* rather than traced: the grid already
 * is a list of glyphs at known positions, so nothing has to be recovered from pixels.
 * Effects are not included — they operate on rendered pixels, and there is nothing
 * meaningful to vectorise about a blur.
 *
 * Two modes, because they answer different questions:
 *
 * - [SvgMode.TEXT] keeps the glyphs as text. Tiny files, still editable as type, but the
 *   viewer needs a font holding the characters — which for the exotic sets it usually isn't.
 * - [SvgMode.OUTLINES] converts each glyph to a real path. Larger, no longer editable as
 *   text, but it renders identically everywhere. This is the one print and embroidery want.
 */
object SvgExporter {

    private const val DEFS_PREFIX = "g"

    /**
     * Where the glyphs sit, without saying what draws them.
     *
     * Separating this from [GlyphRenderer.GridLayout] is what lets the text mode — and its
     * tests — run without a Typeface. Only the outline mode genuinely needs the font.
     */
    data class Geometry(
        val width: Int,
        val height: Int,
        val originX: Float,
        val originY: Float,
        val cellWidth: Int,
        val cellHeight: Int,
        val baseline: Float,
        val fontSize: Int,
    )

    fun geometryOf(layout: GlyphRenderer.GridLayout) = Geometry(
        width = layout.width,
        height = layout.height,
        originX = layout.originX,
        originY = layout.originY,
        cellWidth = layout.cell.width,
        cellHeight = layout.cell.height,
        baseline = layout.cell.baseline,
        fontSize = layout.fontSize,
    )

    fun build(art: GlyphGrid, params: RenderSettings, fontSizePx: Int, mode: SvgMode): String {
        val layout = GlyphRenderer.layout(art, params, fontSizePx)
        val geometry = geometryOf(layout)
        return when (mode) {
            SvgMode.TEXT -> buildText(art, params, geometry)
            SvgMode.OUTLINES -> buildOutlines(art, params, layout, geometry)
        }
    }

    fun buildText(art: GlyphGrid, params: RenderSettings, geometry: Geometry): String {
        val out = StringBuilder(art.cols * art.rows * 6)
        openDocument(out, params, geometry)
        appendText(out, art, params, geometry)
        out.append("</svg>\n")
        return out.toString()
    }

    private fun buildOutlines(
        art: GlyphGrid,
        params: RenderSettings,
        layout: GlyphRenderer.GridLayout,
        geometry: Geometry,
    ): String {
        val out = StringBuilder(art.cols * art.rows * 24)
        openDocument(out, params, geometry)
        appendOutlines(out, art, params, layout, geometry)
        out.append("</svg>\n")
        return out.toString()
    }

    private fun openDocument(out: StringBuilder, params: RenderSettings, geometry: Geometry) {
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        out.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
        out.append("width=\"${geometry.width}\" height=\"${geometry.height}\" ")
        out.append("viewBox=\"0 0 ${geometry.width} ${geometry.height}\">\n")
        if (!params.transparentBackground) {
            out.append("<rect width=\"100%\" height=\"100%\" fill=\"")
            out.append(hex(params.backgroundColor)).append("\"/>\n")
        }
    }

    /**
     * One `<text>` per run of neighbouring cells sharing a colour, with an explicit x for
     * every glyph. Letting the viewer advance the text itself would leave the grid at the
     * mercy of whatever font it substitutes; an x-list pins each glyph to its own cell.
     */
    private fun appendText(
        out: StringBuilder,
        art: GlyphGrid,
        params: RenderSettings,
        geometry: Geometry,
    ) {
        out.append("<g font-family=\"DejaVu Sans Mono, monospace\" ")
        out.append("font-size=\"${geometry.fontSize}\" text-anchor=\"middle\">\n")

        for (row in 0 until art.rows) {
            val y = geometry.originY + row * geometry.cellHeight + geometry.baseline
            var col = 0
            while (col < art.cols) {
                if (art.glyphAt(col, row) == ' ') {
                    col++
                    continue
                }
                val color = art.colorAt(col, row) ?: params.inkColor
                val xs = StringBuilder()
                val chars = StringBuilder()
                var end = col
                while (end < art.cols) {
                    val glyph = art.glyphAt(end, row)
                    if (glyph == ' ' || (art.colorAt(end, row) ?: params.inkColor) != color) break
                    if (end > col) xs.append(' ')
                    xs.append(
                        num(geometry.originX + end * geometry.cellWidth + geometry.cellWidth / 2f),
                    )
                    escape(chars, glyph)
                    end++
                }
                out.append("<text x=\"").append(xs).append("\" y=\"").append(num(y))
                out.append("\" fill=\"").append(hex(color)).append("\">")
                out.append(chars).append("</text>\n")
                col = end
            }
        }
        out.append("</g>\n")
    }

    /**
     * Each distinct glyph is flattened once into a `<defs>` path at the origin and then
     * placed with `<use>`. A grid repeats the same handful of shapes thousands of times, so
     * writing the outline out every time would multiply the file by roughly the ramp length
     * for nothing. Both `href` and `xlink:href` are emitted: older consumers only know the
     * latter, newer ones only promise the former.
     */
    private fun appendOutlines(
        out: StringBuilder,
        art: GlyphGrid,
        params: RenderSettings,
        layout: GlyphRenderer.GridLayout,
        geometry: Geometry,
    ) {
        val paint = GlyphRenderer.paintFor(layout)
        val widths = FloatArray(1)
        val single = CharArray(1)

        val ids = HashMap<Char, String>()
        val glyphWidths = HashMap<Char, Float>()
        val defs = StringBuilder()

        // Finer than this stops being visible and starts being file size. It scales with the
        // glyph so small type doesn't pay for detail it cannot show.
        val step = max(0.35f, geometry.fontSize * 0.02f)

        for (cell in art.glyphs.toSortedSet()) {
            if (cell == ' ' || ids.containsKey(cell)) continue
            single[0] = cell
            paint.getTextWidths(single, 0, 1, widths)

            val path = Path()
            paint.getTextPath(single, 0, 1, 0f, 0f, path)
            val d = flatten(path, step)
            if (d.isEmpty()) continue

            val id = DEFS_PREFIX + ids.size
            ids[cell] = id
            glyphWidths[cell] = widths[0]
            defs.append("<path id=\"").append(id).append("\" d=\"").append(d).append("\"/>\n")
        }

        out.append("<defs>\n").append(defs).append("</defs>\n")

        for (row in 0 until art.rows) {
            val y = geometry.originY + row * geometry.cellHeight + geometry.baseline
            for (col in 0 until art.cols) {
                val glyph = art.glyphAt(col, row)
                val id = ids[glyph] ?: continue
                val width = glyphWidths[glyph] ?: 0f
                val x = geometry.originX + col * geometry.cellWidth +
                    (geometry.cellWidth - width) / 2f
                val color = art.colorAt(col, row) ?: params.inkColor
                out.append("<use href=\"#").append(id).append("\" xlink:href=\"#").append(id)
                out.append("\" x=\"").append(num(x)).append("\" y=\"").append(num(y))
                out.append("\" fill=\"").append(hex(color)).append("\"/>\n")
            }
        }
    }

    /**
     * A glyph outline as path data, one closed subpath per contour.
     *
     * [PathMeasure] rather than `Path.approximate`, because approximate concatenates every
     * contour into a single point list: the counter of an 'o' would be joined to its outside
     * by a stray segment, and every closed shape would leak.
     */
    private fun flatten(path: Path, step: Float): String {
        val measure = PathMeasure(path, false)
        val position = FloatArray(2)
        val out = StringBuilder()

        do {
            val length = measure.length
            if (length <= 0f) continue
            val segments = max(3, ceil(length / step).toInt())
            for (i in 0 until segments) {
                if (!measure.getPosTan(length * i / segments, position, null)) break
                out.append(if (i == 0) 'M' else 'L')
                out.append(num(position[0])).append(' ').append(num(position[1]))
                if (i < segments - 1) out.append(' ')
            }
            out.append('Z')
        } while (measure.nextContour())

        return out.toString()
    }

    /** Two decimals sits well under any printer's resolution and halves the file against six. */
    private fun num(value: Float): String = String.format(Locale.US, "%.2f", value)

    private fun hex(color: Int): String = String.format(Locale.US, "#%06X", color and 0xFFFFFF)

    private fun escape(out: StringBuilder, glyph: Char) {
        when (glyph) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            else -> out.append(glyph)
        }
    }
}
