package org.phioster.glyphsmith.anim

import org.phioster.glyphsmith.render.RenderSettings

/**
 * A whole movement behind one tap: the tracks it needs switched on and set, and the effect they
 * drive switched on with them. Everything it sets is an ordinary setting, so the tracks below
 * show what was done and can be changed from there.
 */
class QuickMotion(val label: String, val about: String, private val change: (RenderSettings) -> RenderSettings) {

    /** [params] moving this way and no other: whatever moved before is stilled first. */
    fun applyTo(params: RenderSettings): RenderSettings = change(QuickMotions.stilled(params))

    /** True when [params] already move exactly like this. */
    fun wornBy(params: RenderSettings): Boolean = applyTo(params) == params
}

object QuickMotions {

    /** [params] with nothing moving. An effect a motion switched on stays: it is part of the look now. */
    fun stilled(params: RenderSettings): RenderSettings = params.copy(
        animation = params.animation.copy(
            enabled = false,
            tracks = params.animation.tracks.map { it.copy(enabled = false) }.sortedBy { it.target.ordinal },
        ),
        temporal = params.temporal.copy(enabled = false),
    )

    private fun moving(params: RenderSettings, vararg tracks: AnimTrack): RenderSettings = params.copy(
        animation = tracks.fold(params.animation.copy(enabled = true)) { animation, track -> animation.withTrack(track) },
    )

    private fun track(target: AnimTarget, from: Int, to: Int, curve: AnimCurve = AnimCurve.SINE) =
        AnimTrack(target = target, enabled = true, curve = curve, from = from, to = to)

    val all: List<QuickMotion> = listOf(
        QuickMotion("still", "nothing moves") { it },
        QuickMotion("breathe", "the picture dims and comes back, and the dither re-forms with it") {
            moving(it, track(AnimTarget.SOURCE_BRIGHTNESS, -18, 18))
        },
        QuickMotion("shimmer", "the dither itself never quite holds still") {
            it.copy(temporal = it.temporal.copy(enabled = true))
        },
        QuickMotion("tube", "the glass bulges and settles, like a screen warming up") {
            moving(
                it.copy(effects = it.effects.copy(crtWarp = it.effects.crtWarp.copy(enabled = true))),
                track(AnimTarget.WARP_CURVATURE, 0, 45),
            )
        },
        QuickMotion("fringe", "colour edges drift apart and close again") {
            moving(
                it.copy(effects = it.effects.copy(chromatic = it.effects.chromatic.copy(enabled = true))),
                track(AnimTarget.CHROMATIC_OFFSET, 0, 14),
            )
        },
        QuickMotion("tear", "a torn field crawls through the lines and back") {
            moving(
                it.copy(effects = it.effects.copy(interlace = it.effects.interlace.copy(enabled = true))),
                track(AnimTarget.INTERLACE_SHIFT, 0, 60, AnimCurve.TRIANGLE),
            )
        },
        QuickMotion("slices", "strips of the picture slide out of place and back") {
            moving(
                it.copy(effects = it.effects.copy(sliceShift = it.effects.sliceShift.copy(enabled = true))),
                track(AnimTarget.SLICE_OFFSET, 0, 40, AnimCurve.TRIANGLE),
            )
        },
        QuickMotion("reveal", "the look gives way block by block until the photo is there; once through, not a loop") {
            moving(it, track(AnimTarget.REVEAL, 0, 100, AnimCurve.EASE_IN_OUT))
        },
        QuickMotion("melt", "sorted streaks eat into the picture and let go") {
            moving(
                it.copy(effects = it.effects.copy(pixelSort = it.effects.pixelSort.copy(enabled = true))),
                track(AnimTarget.SORT_BAND, 30, 100),
            )
        },
    )
}
