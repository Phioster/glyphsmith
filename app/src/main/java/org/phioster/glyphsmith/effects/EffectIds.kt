package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.core.serial.WireIdSerializer

/**
 * What each effect is called in a saved preset.
 *
 * This one matters more than it looks: the effect *order* is a list of these, so every preset
 * that has ever touched the chain carries all of them. The constants are one-word slot names
 * (`POST`, `DEPTH`, `DITHER`, `WARP`) that read as almost anything; the ids say what the pass
 * actually is, and they are deliberately not the class names either — `effect.glow` outlives
 * whatever `EpsilonGlow` is eventually called.
 */
object EffectIds : WireIdSerializer<EffectId>(
    category = "effect",
    values = EffectId.entries.toList(),
    idOf = { effect ->
        when (effect) {
            EffectId.POST -> "effect.post-processing"
            EffectId.BLUR -> "effect.blur-sharpen"
            EffectId.TINT -> "effect.tint"
            EffectId.CHROMATIC -> "effect.chromatic-aberration"
            EffectId.GLITCH -> "effect.jpeg-glitch"
            EffectId.SORT -> "effect.pixel-sort"
            EffectId.SLICE -> "effect.slice-shift"
            EffectId.INTERLACE -> "effect.interlace"
            EffectId.MODULATION -> "effect.modulation-lines"
            EffectId.STARS -> "effect.diffraction-stars"
            EffectId.SUBTEXTURE -> "effect.subtexture"
            EffectId.PRINT -> "effect.spot-color-print"
            EffectId.CMYK -> "effect.cmyk-halftone"
            EffectId.DEPTH -> "effect.color-depth"
            EffectId.DITHER -> "effect.blue-noise-dither"
            EffectId.GLOW -> "effect.glow"
            EffectId.WARP -> "effect.crt-warp"
        }
    },
)
