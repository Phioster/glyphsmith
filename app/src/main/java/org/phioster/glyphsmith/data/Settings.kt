package org.phioster.glyphsmith.data

import android.content.Context

/**
 * The handful of choices that belong to the app rather than to an image.
 *
 * Kept out of `AsciiParams` on purpose: presets are meant to be shared, and a preset that
 * quietly repaints someone else's interface when they load it would be a surprise nobody
 * asked for.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("glyphsmith", Context.MODE_PRIVATE)

    var themeId: String
        get() = prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    private companion object {
        const val KEY_THEME = "theme"
        const val DEFAULT_THEME = "matrix"
    }
}
