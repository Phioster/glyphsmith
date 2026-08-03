package org.phioster.glyphsmith.export

import org.phioster.glyphsmith.ascii.GlyphGrid
import org.phioster.glyphsmith.ascii.RenderSettings
import java.util.Locale

/**
 * The grid as text that keeps its colour.
 *
 * The `.txt` export throws the colour away, which is right for pasting into a README and
 * wrong for everything else. These two are the places ASCII art actually lives: a web page,
 * and a terminal.
 */
object TextExporters {

    /**
     * A self-contained HTML page: one `<pre>` with a `<span>` per run of same-coloured
     * glyphs.
     *
     * Runs rather than a span per glyph, because a 200×120 grid is 24,000 cells and a span
     * each would be a megabyte of markup for an image that is mostly flat colour.
     */
    fun html(art: GlyphGrid, params: RenderSettings): String {
        val out = StringBuilder(art.cols * art.rows * 4)
        val background = if (params.transparentBackground) "transparent" else hex(params.backgroundColor)

        out.append("<!doctype html>\n<html><head><meta charset=\"utf-8\">\n")
        out.append("<title>glyphsmith</title>\n<style>\n")
        out.append("body{margin:0;background:").append(background).append(";}\n")
        out.append("pre{font-family:'DejaVu Sans Mono',monospace;font-size:12px;")
        out.append("line-height:1.0;letter-spacing:0;margin:0;padding:16px;}\n")
        out.append("</style></head><body><pre>")

        for (row in 0 until art.rows) {
            var col = 0
            while (col < art.cols) {
                val color = art.colorAt(col, row) ?: params.inkColor
                val run = StringBuilder()
                var end = col
                while (end < art.cols && (art.colorAt(end, row) ?: params.inkColor) == color) {
                    escapeHtml(run, art.glyphAt(end, row))
                    end++
                }
                out.append("<span style=\"color:").append(hex(color)).append("\">")
                out.append(run).append("</span>")
                col = end
            }
            if (row < art.rows - 1) out.append('\n')
        }

        out.append("</pre></body></html>\n")
        return out.toString()
    }

    /**
     * The grid as terminal output, using 24-bit colour escapes.
     *
     * A colour code is only emitted when the colour actually changes — repeating it per
     * glyph triples the file for no visible difference. The reset at the end of each line
     * matters: without it a terminal keeps painting the last colour across everything that
     * follows, including the shell prompt.
     */
    fun ansi(art: GlyphGrid, params: RenderSettings): String {
        val out = StringBuilder(art.cols * art.rows * 2)
        for (row in 0 until art.rows) {
            var current = -1
            for (col in 0 until art.cols) {
                val color = art.colorAt(col, row) ?: params.inkColor
                if (color != current) {
                    out.append(foreground(color))
                    current = color
                }
                out.append(art.glyphAt(col, row))
            }
            out.append(RESET)
            if (row < art.rows - 1) out.append('\n')
        }
        return out.toString()
    }

    /** 0x1B. Written as an escape rather than a raw byte, which editors and diffs mangle. */
    private const val ESC = "\u001B"

    private const val RESET = "$ESC[0m"

    private fun foreground(color: Int): String = String.format(
        Locale.US,
        "$ESC[38;2;%d;%d;%dm",
        (color shr 16) and 0xFF,
        (color shr 8) and 0xFF,
        color and 0xFF,
    )

    private fun hex(color: Int): String = String.format(Locale.US, "#%06X", color and 0xFFFFFF)

    private fun escapeHtml(out: StringBuilder, glyph: Char) {
        when (glyph) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            else -> out.append(glyph)
        }
    }
}
