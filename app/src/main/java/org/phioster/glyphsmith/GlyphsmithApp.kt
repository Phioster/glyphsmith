package org.phioster.glyphsmith

import android.app.Application
import org.phioster.glyphsmith.glyph.Fonts

/**
 * Loads the bundled faces once at start-up. Doing it here rather than lazily keeps
 * `Typeface.createFromAsset` — which touches the asset manager — off the render path.
 */
class GlyphsmithApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Fonts.init(this)
    }
}
