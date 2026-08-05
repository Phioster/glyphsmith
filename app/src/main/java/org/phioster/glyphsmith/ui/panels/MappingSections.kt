package org.phioster.glyphsmith.ui.panels

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
     * The rule is *every* control the panel shows, which is why the list has to be read against
     * the panel rather than extended by habit — `patternDensity` and `edgeSetId` sat under
     * visible sliders for a long time without being in it, and `screenOverride` arrived with a
     * load button and a clear button and was missed on the same day. `orbSeed` is the one stored
     * value the panel does not show, and it is not reset.
     *
     * The test that guards this compares the *whole* settings object, which is the only shape of
     * assertion a forgotten field cannot slip past — but only for fields its fixture actually
     * sets. A new control belongs in `mangled()` before it belongs here.
     *
     * The values come from a default [RenderSettings] rather than from literals repeated here,
     * so "reset" cannot drift away from what a new session starts with.
     */
    fun reset(params: RenderSettings): RenderSettings {
        val d = RenderSettings()
        val dither = params.copy(
            brightness = d.brightness,
            contrast = d.contrast,
            gamma = d.gamma,
            saturation = d.saturation,
            midtones = d.midtones,
            highlights = d.highlights,
            hue = d.hue,
            preBlur = d.preBlur,
            denoise = d.denoise,
            ditherMode = d.ditherMode,
            ditherStrength = d.ditherStrength,
            serpentine = d.serpentine,
            ditherScale = d.ditherScale,
            modScale = d.modScale,
            modAngle = d.modAngle,
            modPhase = d.modPhase,
            patternDensity = d.patternDensity,
            orbCount = d.orbCount,
            orbSize = d.orbSize,
            orbIntensity = d.orbIntensity,
            orbRandom = d.orbRandom,
            orbOffset = d.orbOffset,
            orbDirection = d.orbDirection,
            screenOverride = d.screenOverride,
        )
        return if (showsGlyphMapping(params.renderMode)) {
            dither.copy(
                edgeEnabled = d.edgeEnabled,
                edgeThreshold = d.edgeThreshold,
                edgeSetId = d.edgeSetId,
                edgeOnly = d.edgeOnly,
            )
        } else {
            dither
        }
    }
}
