package org.phioster.glyphsmith.effects

import org.phioster.glyphsmith.core.pipeline.ImageProcessorNode
import org.phioster.glyphsmith.core.pipeline.RenderContext

/**
 * A node built from one entry of an [EffectStack].
 *
 * The effect itself is a lambda rather than a subclass per effect: all twelve already share
 * the signature `(Pixels, Params) -> Pixels`, so there is nothing for twelve classes to say
 * that this one cannot. The [enabled] flag is read once when the node is built, which is
 * correct because a node lives exactly as long as the render it was built for.
 */
private class EffectNode(
    override val id: String,
    override val enabled: Boolean,
    private val apply: (Pixels) -> Pixels,
) : ImageProcessorNode {
    override fun process(input: Pixels, ctx: RenderContext): Pixels = apply(input)
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
        EffectNode(id.name, stack.enabledOf(id)) { pixels ->
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
            }
        }
}
