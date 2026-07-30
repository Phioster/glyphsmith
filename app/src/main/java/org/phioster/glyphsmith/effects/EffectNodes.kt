package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.core.pipeline.ImageProcessorNode
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.core.image.Pixels

/**
 * A node built from one entry of an [EffectStack].
 *
 * The effect itself is a lambda rather than a subclass per effect: they all share the shape
 * `(Pixels, Params) -> Pixels`, so there is nothing for a dozen-odd classes to say that this one
 * cannot. The [enabled] flag is read once when the node is built, which is correct because a node
 * lives exactly as long as the render it was built for.
 *
 * The lambda takes the [RenderContext] as well, because the newer effects need the clock and the
 * output budget from it. The older ones ignore it.
 */
private class EffectNode(
    override val id: String,
    override val enabled: Boolean,
    private val apply: (Pixels, RenderContext) -> Pixels,
) : ImageProcessorNode {
    override fun process(input: Pixels, ctx: RenderContext): Pixels = apply(input, ctx)
}

/**
 * Turns an [EffectStack] into the node list the chain runs.
 *
 * The order comes from [EffectStack.effectiveOrder], which already handles a preset written
 * before an effect existed. This object is the only place that knows which object implements
 * which [EffectId] — the chain itself knows nothing about effects.
 */
object EffectNodes {

    fun of(stack: EffectStack): List<ImageProcessorNode> =
        stack.effectiveOrder().map { id -> nodeFor(id, stack) }

    private fun nodeFor(id: EffectId, stack: EffectStack): ImageProcessorNode =
        EffectNode(id.name, stack.enabledOf(id)) { pixels, ctx ->
            when (id) {
                EffectId.POST -> PostProcessing.apply(pixels, stack.postProcessing)
                EffectId.BLUR -> BlurSharpen.apply(pixels, stack.blurSharpen)
                EffectId.TINT -> Tint.apply(pixels, stack.tint)
                EffectId.CHROMATIC -> Chromatic.apply(pixels, stack.chromatic)
                EffectId.GLITCH -> JpegGlitch.apply(pixels, stack.jpegGlitch)
                EffectId.SORT -> PixelSort.apply(pixels, stack.pixelSort)
                EffectId.SLICE -> SliceShift.apply(pixels, stack.sliceShift)
                EffectId.INTERLACE -> Interlace.apply(pixels, stack.interlace)
                EffectId.STARS -> DiffractionStars.apply(pixels, stack.stars)
                EffectId.SUBTEXTURE -> Subtexture.apply(pixels, stack.subtexture)
                EffectId.CMYK -> CmykHalftone.apply(pixels, stack.cmyk)
                EffectId.GLOW -> EpsilonGlow.apply(pixels, stack.glow)
                // The newer effects take the context: the clock for anything that moves, and the
                // pool through the buffer they are handed.
                EffectId.MODULATION -> ModulationLines.apply(pixels, stack.modulationLines, ctx)
                EffectId.PRINT -> SpotColorPrint.apply(pixels, stack.spotPrint, ctx)
                EffectId.DEPTH -> ColorDepth.apply(pixels, stack.colorDepth, ctx)
                EffectId.DITHER -> BlueNoiseDither.apply(pixels, stack.blueNoise, ctx)
                EffectId.WARP -> CrtWarp.apply(pixels, stack.crtWarp, ctx)
            }
        }
}
