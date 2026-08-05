package org.phioster.glyphsmith.state

import kotlin.random.Random
import org.phioster.glyphsmith.core.color.Palettes
import org.phioster.glyphsmith.core.dither.DitherMode
import org.phioster.glyphsmith.effects.EffectProviders
import org.phioster.glyphsmith.effects.EffectRoll
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.glyph.CharacterSets
import org.phioster.glyphsmith.render.ColorMode
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.core.color.PaletteProvider

/**
 * Surprise Me: a look rolled at random.
 *
 * A plain function of a base, a source of randomness and a mode, rather than something the
 * view model does to itself — the ranges below are the whole feature, and they are only worth
 * anything if they can be tested without a device.
 *
 * Deliberately narrow: a genuinely uniform roll over every parameter produces an empty or
 * unreadable image far more often than an interesting one, which makes the button useless.
 * Cell size, depth and the effect count are all kept inside the range that reliably yields
 * something worth looking at, and the effects are picked one at a time rather than all rolled
 * independently.
 *
 * Anything not named in the final copy is left as the caller had it. The roll moves the look,
 * not every knob in the app.
 *
 * **The effect ranges are not here.** They were, as a `when` over all seventeen slots, and that
 * made this file — which is not part of the effect category — something a new effect had to be
 * added to before Surprise Me could reach it. Each effect declares its own roll beside its own
 * sliders now, and this asks [EffectProviders.randomisable] for whatever declared one. The
 * narrowness is unchanged; it simply lives with the ranges it narrows.
 *
 * It sits with the view model's other split-out halves rather than in `pipeline`, because it
 * rolls a *character set* as well as a dither and a palette, and the set library belongs to
 * Glyph Art. A settings generator that has to know both modules is not shared render
 * infrastructure — it is the layer above, which is exactly where knowing both is allowed. In
 * `pipeline` it was the one file keeping the shared pipeline dependent on the glyph module.
 */
object RandomLook {

    /**
     * Rolls a look on top of [base].
     *
     * [renderMode] defaults to [RenderMode.DEFAULT] — Pixel Dither — because this is the
     * general-purpose roll, and general-purpose means the general-purpose mode whatever the
     * session was sitting in. It stays a parameter so a glyph-flavoured roll is reachable
     * without a second copy of everything below.
     */
    fun roll(
        base: RenderSettings,
        random: Random = Random.Default,
        renderMode: RenderMode = RenderMode.DEFAULT,
    ): RenderSettings {
        val set = CharacterSets.all.random(random)
        val palette = Palettes.all.random(random)
        val dither = DitherMode.entries.random(random)

        // Two at most, picked one at a time: rolling all seventeen independently switches on
        // half the chain and the result is mud, whatever the individual ranges say.
        val roll = EffectRoll(random, palette)
        var effects = EffectStack()
        repeat(random.nextInt(0, 3)) {
            val provider = EffectProviders.randomisable.random(random)
            effects = provider.pass.rolled(effects, roll) ?: effects
        }

        return base.copy(
            renderMode = renderMode,
            charSetId = set.id,
            cellSize = random.nextInt(4, 13),
            depth = random.nextInt(3, 24),
            invert = random.nextBoolean(),
            ditherMode = dither,
            ditherStrength = random.nextInt(50, 101),
            modScale = random.nextInt(4, 16),
            modAngle = random.nextInt(0, 360),
            colorMode = ColorMode.entries.random(random),
            paletteId = PaletteProvider.wireIdOf(palette.id),
            paletteOverride = emptyList(),
            paletteLocks = emptyList(),
            rampOverride = "",
            effects = effects,
        )
    }
}
