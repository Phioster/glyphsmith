package org.phioster.glyphsmith.effects

/**
 * Which pass each slot of the chain runs, in one table.
 *
 * This is the only place a `when (EffectId)` over the *execution* of an effect is meant to
 * survive, for the same reason [org.phioster.glyphsmith.core.dither.DitherAlgorithms] keeps one:
 * something has to map a constant to the thing behind it, and doing it once — exhaustively, so
 * that the compiler refuses a slot that was added without saying what runs in it — is what lets
 * the other dispatches go away. Everything else asks the provider.
 *
 * Note how little is on each line. The pass is declared inside the effect's own object, beside
 * the code it runs, and that is what makes the wiring checkable: the lambda calls the enclosing
 * object's own `apply`, which pins the params type, and no two effects share one. A slot wired to
 * another effect's controls does not compile.
 *
 * This table and [org.phioster.glyphsmith.ui.panels.EffectControls] are the only two places a new
 * effect has to be named outside its own file. Everything a pass *is* travels with it: the stable
 * id and the label on the [EffectId] constant, the params slice, the toggle, the random roll and
 * the code on its `EffectPass`. Both remaining tables are exhaustive `when`s, so neither can be
 * forgotten — the build stops rather than the effect quietly never running.
 */
internal object EffectPasses {

    /**
     * Built once, indexed by ordinal.
     *
     * Every render asks this once per slot per frame, and a preview at thirty frames a second
     * asks it five hundred times a second. Nothing here is stateful: two renders that ask for the
     * same slot get the same object.
     */
    private val bySlot: Array<EffectPass<*>> =
        Array(EffectId.entries.size) { declare(EffectId.entries[it]) }

    fun of(effect: EffectId): EffectPass<*> = bySlot[effect.ordinal]

    private fun declare(effect: EffectId): EffectPass<*> = when (effect) {
        EffectId.POST -> PostProcessing.pass
        EffectId.BLUR -> BlurSharpen.pass
        EffectId.TINT -> Tint.pass
        EffectId.CHROMATIC -> Chromatic.pass
        EffectId.GLITCH -> JpegGlitch.pass
        EffectId.SORT -> PixelSort.pass
        EffectId.SLICE -> SliceShift.pass
        EffectId.INTERLACE -> Interlace.pass
        EffectId.MODULATION -> ModulationLines.pass
        EffectId.STARS -> DiffractionStars.pass
        EffectId.SUBTEXTURE -> Subtexture.pass
        EffectId.PRINT -> SpotColorPrint.pass
        EffectId.CMYK -> CmykHalftone.pass
        EffectId.DEPTH -> ColorDepth.pass
        EffectId.DITHER -> BlueNoiseDither.pass
        EffectId.GLOW -> EpsilonGlow.pass
        EffectId.WARP -> CrtWarp.pass
    }
}
