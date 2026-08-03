package org.phioster.glyphsmith.glyph

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import org.phioster.glyphsmith.render.GlyphFont
import org.phioster.glyphsmith.render.FontStyle

/**
 * The chosen face plus what it can't draw, so the UI can say so instead of silently
 * rendering a grid of tofu boxes.
 */
data class FontChoice(
    val typeface: Typeface,
    val label: String,
    val missing: String,
) {
    val complete: Boolean get() = missing.isEmpty()
}

/**
 * Bundled faces, and the logic that decides which one a ramp gets.
 *
 * The system monospace font is not enough on its own: Android falls back to a *proportional*
 * face for braille, kana, runic and friends, so those sets sit unevenly in their cells. Two
 * subsetted faces ship instead — DejaVu Sans Mono (323 of the 482 glyphs the built-in sets
 * use, in four real styles) and GNU Unifont (all 482, one style, pixel outlines). Subsetting
 * to exactly those glyphs keeps both under 170 KB together.
 *
 * Because the faces are subsets, anything typed into Inject Characters is likely *not* in
 * them — [resolve] notices and hands back the system face, which does have fallback.
 */
object Fonts {

    private const val ASSET_DIR = "fonts"

    private var dejavu: Map<FontStyle, Typeface> = emptyMap()
    private var unifont: Typeface? = null
    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        val assets = context.applicationContext.assets
        fun load(name: String): Typeface? =
            runCatching { Typeface.createFromAsset(assets, "$ASSET_DIR/$name") }.getOrNull()

        dejavu = buildMap {
            load("dejavu_mono.ttf")?.let { put(FontStyle.REGULAR, it) }
            load("dejavu_mono_bold.ttf")?.let { put(FontStyle.BOLD, it) }
            load("dejavu_mono_italic.ttf")?.let { put(FontStyle.ITALIC, it) }
            load("dejavu_mono_bold_italic.ttf")?.let { put(FontStyle.BOLD_ITALIC, it) }
        }
        unifont = load("unifont.ttf")
        initialised = true
    }

    private fun systemFace(style: FontStyle): Typeface = Typeface.create(Typeface.MONOSPACE, androidStyle(style))

    private fun androidStyle(style: FontStyle): Int = when (style) {
        FontStyle.REGULAR -> Typeface.NORMAL
        FontStyle.BOLD -> Typeface.BOLD
        FontStyle.ITALIC -> Typeface.ITALIC
        FontStyle.BOLD_ITALIC -> Typeface.BOLD_ITALIC
    }

    /** Unifont ships one weight; Android synthesises the other three from it. */
    private fun unifontFace(style: FontStyle): Typeface? =
        unifont?.let { if (style == FontStyle.REGULAR) it else Typeface.create(it, androidStyle(style)) }

    /** Characters of [ramp] that [typeface] has no glyph for. */
    fun missingGlyphs(typeface: Typeface, ramp: String): String {
        val paint = Paint().apply { this.typeface = typeface }
        return ramp.toSet()
            .filterNot { it == ' ' || paint.hasGlyph(it.toString()) }
            .joinToString("")
    }

    /**
     * Best face for this ramp: the preferred one when it can draw every glyph, otherwise
     * the next one that can. Completeness beats preference on purpose — a missing glyph is
     * a visible hole in the image, while a different-but-complete face is just a different
     * look.
     */
    fun resolve(preferred: GlyphFont, style: FontStyle, ramp: String): FontChoice {
        val candidates: List<Pair<String, Typeface?>> = when (preferred) {
            GlyphFont.DEJAVU -> listOf("dejavu" to dejavu[style])
            GlyphFont.UNIFONT -> listOf("unifont" to unifontFace(style))
            GlyphFont.SYSTEM -> listOf("system" to systemFace(style))
            GlyphFont.AUTO -> listOf(
                "dejavu" to dejavu[style],
                "unifont" to unifontFace(style),
            )
        }

        candidates.forEach { (label, typeface) ->
            if (typeface != null) {
                val missing = missingGlyphs(typeface, ramp)
                if (missing.isEmpty()) return FontChoice(typeface, label, "")
            }
        }

        // Nothing bundled covers it — the system face is the only one with real fallback.
        val system = systemFace(style)
        val systemMissing = missingGlyphs(system, ramp)
        if (preferred == GlyphFont.AUTO || systemMissing.isEmpty()) {
            return FontChoice(system, "system", systemMissing)
        }

        // An explicit choice that can't cover the ramp is still honoured, but reported.
        val (label, typeface) = candidates.first()
        val face = typeface ?: system
        return FontChoice(face, label, missingGlyphs(face, ramp))
    }
}
