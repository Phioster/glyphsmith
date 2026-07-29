package org.phioster.glyphsmith.data

import android.content.Context

/**
 * How much work the preview is allowed to do.
 *
 * Dither Boy's bottom bar carries five playback buttons — FULL, LIVE, ANIMATED, LOOPED,
 * RENDERED — but what each of them switches is nowhere documented, and guessing at five
 * meanings would produce five controls that do not quite do what their labels say. These
 * two are the distinctions that actually exist here, named for what they do.
 */
enum class PreviewQuality(val label: String, val maxSide: Int) {
    /** Half resolution. The pipeline is the whole cost of a frame, so this halves the wait. */
    LIVE("Live", 800),
    FULL("Full", 1600),
}

/**
 * The handful of choices that belong to the app rather than to an image.
 *
 * Kept out of `AsciiParams` on purpose: presets are meant to be shared, and a preset that
 * quietly repaints someone else's interface — or halves their preview resolution — when
 * they load it would be a surprise nobody asked for.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("glyphsmith", Context.MODE_PRIVATE)

    var themeId: String
        get() = prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var previewQuality: PreviewQuality
        get() = runCatching {
            PreviewQuality.valueOf(prefs.getString(KEY_QUALITY, null) ?: "")
        }.getOrDefault(PreviewQuality.FULL)
        set(value) = prefs.edit().putString(KEY_QUALITY, value.name).apply()

    var looped: Boolean
        get() = prefs.getBoolean(KEY_LOOPED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOOPED, value).apply()

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_QUALITY = "previewQuality"
        const val KEY_LOOPED = "looped"
        const val DEFAULT_THEME = "matrix"
    }
}
