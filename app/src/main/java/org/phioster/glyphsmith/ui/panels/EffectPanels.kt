package org.phioster.glyphsmith.ui.panels

import androidx.compose.runtime.Composable
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectStack

/**
 * Which controls each slot of the chain shows, in one table.
 *
 * This is the second of the two `when (EffectId)` blocks the effect category keeps on purpose —
 * the other is [org.phioster.glyphsmith.effects.EffectPasses], which says what each slot *runs*.
 * Everything else about an effect now travels with the effect: its stable id and its label are on
 * the constant, its params slice, its toggle, its random roll and its code are on its
 * `EffectPass`.
 *
 * These two cannot follow, and the reason is not the same in both cases.
 *
 * A panel is a `@Composable`, and Compose is the UI. An `EffectPass` that carried its own panel
 * would put `androidx.compose` inside `effects/`, which is the layering rule read backwards: the
 * effect would then need a UI toolkit to be unit-tested, and every pass would drag the Compose
 * runtime into the render path. So the binding of a slot to its controls stays on the UI side of
 * that line, and this is where it lives.
 *
 * It is an exhaustive `when` rather than a map for the same reason
 * [org.phioster.glyphsmith.AppRenderModules] is: a map is missing an entry until the first person
 * opens the FX tab in the one build nobody tried, whereas a `when` refuses to compile until a new
 * slot says what it looks like. A new effect therefore needs exactly two lines outside its own
 * file — the one that runs it and the one that shows it — and both are compiler-enforced.
 */
@Composable
internal fun EffectControls(
    id: EffectId,
    fx: EffectStack,
    move: MoveHandler,
    onChange: (EffectStack) -> Unit,
) {
    fun update(block: EffectStack.() -> EffectStack) = onChange(fx.block())

    when (id) {
        EffectId.POST ->
            PostProcessingSection(fx.postProcessing, move) { p -> update { copy(postProcessing = p) } }

        EffectId.BLUR ->
            BlurSharpenSection(fx.blurSharpen, move) { p -> update { copy(blurSharpen = p) } }

        EffectId.TINT -> TintSection(fx.tint, move) { p -> update { copy(tint = p) } }

        EffectId.CHROMATIC ->
            ChromaticSection(fx.chromatic, move) { p -> update { copy(chromatic = p) } }

        EffectId.GLITCH ->
            GlitchSection(fx.jpegGlitch, move) { p -> update { copy(jpegGlitch = p) } }

        EffectId.SORT ->
            PixelSortSection(fx.pixelSort, move) { p -> update { copy(pixelSort = p) } }

        EffectId.SLICE ->
            SliceShiftSection(fx.sliceShift, move) { p -> update { copy(sliceShift = p) } }

        EffectId.INTERLACE ->
            InterlaceSection(fx.interlace, move) { p -> update { copy(interlace = p) } }

        EffectId.MODULATION ->
            ModulationLinesSection(fx.modulationLines, move) { p ->
                update { copy(modulationLines = p) }
            }

        EffectId.STARS -> StarsSection(fx.stars, move) { p -> update { copy(stars = p) } }

        EffectId.SUBTEXTURE ->
            SubtextureSection(fx.subtexture, move) { p -> update { copy(subtexture = p) } }

        EffectId.PRINT ->
            SpotPrintSection(fx.spotPrint, move) { p -> update { copy(spotPrint = p) } }

        EffectId.CMYK -> CmykSection(fx.cmyk, move) { p -> update { copy(cmyk = p) } }

        EffectId.DEPTH ->
            ColorDepthSection(fx.colorDepth, move) { p -> update { copy(colorDepth = p) } }

        EffectId.DITHER ->
            BlueNoiseSection(fx.blueNoise, move) { p -> update { copy(blueNoise = p) } }

        EffectId.GLOW -> GlowSection(fx.glow, move) { p -> update { copy(glow = p) } }

        EffectId.WARP -> CrtWarpSection(fx.crtWarp, move) { p -> update { copy(crtWarp = p) } }
    }
}
