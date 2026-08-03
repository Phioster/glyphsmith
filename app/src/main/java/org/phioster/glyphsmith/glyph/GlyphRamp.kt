package org.phioster.glyphsmith.glyph

import org.phioster.glyphsmith.render.RenderSettings

/**
 * The character ramp a [RenderSettings] asks for.
 *
 * Extensions rather than members, and that is the whole point of the file. The settings object
 * is shared by both render modules, so it may not reach into this package — but the questions
 * answered here only mean anything once glyphs are involved, and the answers need the character
 * library to give them. Putting them on this side of the line keeps the settings free of the
 * glyph module while leaving every call site reading exactly as it did: `params.effectiveRamp()`
 * is still `params.effectiveRamp()`, one import further along.
 *
 * The pixel module never calls any of this, which before was only true by convention.
 */

/** The character set named by [RenderSettings.charSetId]. */
val RenderSettings.charSet: CharacterSet get() = CharacterSets.byId(charSetId)

/** The glyphs the ramp is built from: the override when set, else the chosen set. */
fun RenderSettings.baseGlyphs(): String = rampOverride.ifEmpty { charSet.glyphs }

/**
 * The ramp actually used for mapping: the set narrowed to [RenderSettings.depth] levels, then
 * the injected characters, then optionally reversed.
 *
 * Injection lands at the dense end on purpose — injected glyphs show up in the brightest areas,
 * which is predictable and keeps the tonal ramp below it intact.
 */
fun RenderSettings.effectiveRamp(): String {
    val base = baseGlyphs()
    val levels = effectiveDepth
    val narrowed = when {
        levels >= base.length -> base
        levels == 1 -> base.takeLast(1)
        else -> buildString {
            for (i in 0 until levels) {
                val index = (i * (base.length - 1)) / (levels - 1)
                append(base[index])
            }
        }
    }
    val injected = narrowed + injection.take(RenderSettings.MAX_INJECTION)
    return if (invert) injected.reversed() else injected
}

/** Upper bound the offset slider runs to — the offset wraps at the ramp length. */
fun RenderSettings.offsetMax(): Int = effectiveRamp().length.coerceAtLeast(1)
