package org.phioster.glyphsmith.anim

import org.phioster.glyphsmith.core.serial.WireIdSerializer

/**
 * What each animation target is called in a saved preset.
 *
 * The last identity in the format that was still a Kotlin constant name. Render modes, dither
 * styles, effects and palettes were given stable ids in schema versions 3 and 4; an `AnimTrack`
 * went on storing `GLOW_DIRECTION`, so renaming that constant would have changed what an
 * existing animation drives — silently, on somebody else's device, in an export they would only
 * notice after it finished rendering.
 *
 * The ids are on the constants themselves rather than in a `when` here, for the reason
 * [org.phioster.glyphsmith.effects.EffectIds] gives: a constructor argument refuses a target
 * without an id sooner than an exhaustive `when` does, and it is one fewer file to remember.
 *
 * Reading is unchanged by any of this — [WireIdSerializer] accepts the old constant names as
 * aliases, so every animation ever saved still loads. What the ids stop is the old spelling
 * being *written back out* for ever, which is what would have kept the constants load-bearing.
 */
object AnimTargetIds : WireIdSerializer<AnimTarget>(
    category = "anim",
    values = AnimTarget.entries.toList(),
    idOf = AnimTarget::wireId,
)
