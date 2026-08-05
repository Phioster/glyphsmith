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
 *
 * The table itself is now [EffectId.wireId], stated on the constant. It used to be an
 * exhaustive `when` here, which bought exactly one thing: the compiler refusing to build once
 * a slot was added without an id. A constructor argument refuses the same thing sooner, and it
 * removes a file from the list a new effect has to touch. Everything else — the format, the
 * uniqueness check, the legacy-name aliases, the refusal of an unknown id — is
 * [WireIdSerializer] and unchanged.
 */
object EffectIds : WireIdSerializer<EffectId>(
    category = "effect",
    values = EffectId.entries.toList(),
    idOf = EffectId::wireId,
)
