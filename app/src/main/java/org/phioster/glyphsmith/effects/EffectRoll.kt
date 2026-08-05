package org.phioster.glyphsmith.effects

import kotlin.random.Random
import org.phioster.glyphsmith.core.color.Palette

/**
 * What an effect may consult while Surprise Me rolls it.
 *
 * Small on purpose. A roll needs a source of randomness, and one effect — tint — needs to know
 * which palette the rest of the look was rolled with, because a wash in an unrelated colour is
 * the one thing that makes a rolled look read as a mistake. Anything an effect wants beyond
 * these two belongs in its own params, not here.
 *
 * It exists so that the ranges an effect is rolled within can live *inside the effect*, next to
 * the sliders those ranges belong to. They used to be a seventeen-branch `when` in
 * `state/RandomLook`, which is a file outside the effect category — so adding an effect meant
 * either editing it or quietly shipping an effect Surprise Me could never reach.
 */
class EffectRoll(
    val random: Random,
    /** The palette the surrounding roll settled on, for an effect that has to match it. */
    val palette: Palette,
)
