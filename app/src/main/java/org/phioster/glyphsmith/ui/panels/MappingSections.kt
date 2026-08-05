package org.phioster.glyphsmith.ui.panels

import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.RenderModules
import org.phioster.glyphsmith.render.RenderSettings

/**
 * The line the mapping panel is split along.
 *
 * Everything between the source pixels and the quantised levels — tone, pre-dither adjustments,
 * the dither style and its own settings — happens in every render mode, so the panel offers it
 * in every render mode. Only the edge mapping is glyph-specific: it swaps a brightness-matched
 * glyph for a direction-matched one, and nothing but [org.phioster.glyphsmith.glyph.GlyphEngine]
 * ever reads the edge grid.
 *
 * Kept out of the composable, and free of Compose, so the rule is testable without a UI host.
 * The capability is asked of the module rather than compared against a mode, so a fourth mode
 * inherits the right answer from what it declares.
 */
internal object MappingSections {

    /** True when the panel's glyph half — the edge mapping — applies to [mode]. */
    fun showsGlyphMapping(mode: RenderMode): Boolean = RenderModules.of(mode).producesGlyphs

    /**
     * `reset mapping`, which restores what the panel is currently showing and nothing else.
     *
     * The edge settings survive a reset in a mode that does not show them: they are invisible
     * there, and silently rewriting a setting the user cannot see would turn a saved glyph look
     * into a different one the moment somebody reset the dither from pixel mode.
     *
     * The field list is the one the button has always written. `patternDensity`, `edgeSetId` and
     * `orbSeed` were never in it and are left out here too — splitting the panel is not the place
     * to change what the button does.
     */
    fun reset(params: RenderSettings): RenderSettings {
        val dither = params.copy(
            brightness = 0f,
            contrast = 1f,
            gamma = 1f,
            saturation = 100,
            midtones = 50,
            highlights = 50,
            hue = 0,
            preBlur = 0,
            denoise = 0,
            ditherMode = DitherMode.NONE,
            ditherStrength = 100,
            serpentine = true,
            ditherScale = 100,
            modScale = 8,
            modAngle = 0,
            modPhase = 0,
            orbCount = 1,
            orbSize = 100,
            orbIntensity = 0,
            orbRandom = 0,
            orbOffset = 0,
            orbDirection = 0,
        )
        return if (showsGlyphMapping(params.renderMode)) {
            dither.copy(
                edgeEnabled = false,
                edgeThreshold = 25,
                edgeOnly = false,
            )
        } else {
            dither
        }
    }
}
