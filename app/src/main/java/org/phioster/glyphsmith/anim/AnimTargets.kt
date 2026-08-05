package org.phioster.glyphsmith.anim

import org.phioster.glyphsmith.render.RenderSettings

/**
 * One animation target: which parameter it names, how its random curve is seeded, and the field
 * it writes.
 *
 * [salt] is deliberately a number rather than a position in a list. `Animator.sample` hashes the
 * frame against it, so it is a **rendering input**: a salt that moves is a saved animation that
 * renders differently, and the only symptom is an exported GIF nobody compares against last
 * week's. It used to be `target.ordinal + 1`, which worked only for as long as targets were an
 * enum whose order nobody touched. Writing it down is what makes that safe.
 *
 * [write] is the branch that used to live in `Animator.apply` — eleven of them, in a `when` a
 * long way from the parameter each named. The clamps came across with them, including the two
 * that deliberately have none.
 */
class AnimTargetProvider(
    val target: AnimTarget,
    val salt: Int,
    val write: (RenderSettings, Int) -> RenderSettings,
)

/**
 * Every animation target this build offers.
 *
 * Six of these write a plain setting and five reach into the effect stack. That split is the
 * reason this file is a step on the way rather than the destination: an effect's parameter is
 * described here, in `anim/`, which means adding an animatable effect parameter still means
 * editing a file outside the effect category. The next step moves those five declarations into
 * the effects that own them, and this registry collects them instead of listing them.
 */
object AnimTargets {

    val all: List<AnimTargetProvider> = listOf(
        AnimTargetProvider(AnimTarget.DEPTH, 1) { p, v ->
            p.copy(depth = v.coerceIn(1, RenderSettings.MAX_DEPTH))
        },
        // The offset wraps anyway, so it is never clamped to the ramp length here.
        AnimTargetProvider(AnimTarget.CHARACTER_OFFSET, 2) { p, v -> p.copy(offset = v) },
        AnimTargetProvider(AnimTarget.DITHER_STRENGTH, 3) { p, v ->
            p.copy(ditherStrength = v.coerceIn(0, 100))
        },
        // Left unclamped: the phase wraps inside the pattern, so a track that runs past 100
        // simply keeps travelling instead of stalling at the end of its range.
        AnimTargetProvider(AnimTarget.MOD_PHASE, 4) { p, v -> p.copy(modPhase = v) },
        AnimTargetProvider(AnimTarget.PATTERN_DENSITY, 5) { p, v ->
            p.copy(patternDensity = v.coerceIn(0, 100))
        },
        AnimTargetProvider(AnimTarget.EDGE_THRESHOLD, 6) { p, v ->
            p.copy(edgeThreshold = v.coerceIn(0, 100))
        },
        AnimTargetProvider(AnimTarget.GLITCH_SEED, 7) { p, v ->
            p.copy(effects = p.effects.copy(jpegGlitch = p.effects.jpegGlitch.copy(seed = v)))
        },
        AnimTargetProvider(AnimTarget.CHROMATIC_OFFSET, 8) { p, v ->
            p.copy(
                effects = p.effects.copy(
                    chromatic = p.effects.chromatic.copy(maxDisplace = v.coerceIn(0, 50)),
                ),
            )
        },
        AnimTargetProvider(AnimTarget.GLOW_DIRECTION, 9) { p, v ->
            p.copy(effects = p.effects.copy(glow = p.effects.glow.copy(direction = v)))
        },
        AnimTargetProvider(AnimTarget.STARS_ANGLE, 10) { p, v ->
            p.copy(effects = p.effects.copy(stars = p.effects.stars.copy(angle = v)))
        },
        AnimTargetProvider(AnimTarget.MODULATION_PHASE, 11) { p, v ->
            p.copy(
                effects = p.effects.copy(
                    modulationLines = p.effects.modulationLines.copy(phase = v),
                ),
            )
        },
    )

    /**
     * Indexed by ordinal rather than scanned: every frame of an animation asks for one of these
     * per enabled track, and a preview at thirty frames a second asks often enough to matter.
     */
    private val bySlot: Array<AnimTargetProvider> =
        AnimTarget.entries.map { t -> all.first { it.target == t } }.toTypedArray()

    fun of(target: AnimTarget): AnimTargetProvider = bySlot[target.ordinal]
}
