package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.anim.AnimationParams

/** How a layer is combined with what is already underneath it. */
enum class LayerBlend(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    SOFT_LIGHT("Soft Light"),
    ADD("Add"),
    DIFFERENCE("Difference"),
}

/**
 * A second rendering stacked on the first: of the same picture, or of one the layer brings.
 *
 * The reference app's layer stack is how one image ends up carrying two treatments at
 * once — a coarse ramp underneath, a fine one on top, offset and screened together. This is
 * the same idea: a layer holds a complete set of settings of its own, renders separately,
 * and is then transformed and blended over the result so far.
 *
 * [params] is an entire [RenderSettings], which means it has a `layers` list of its own. That
 * list is **not** recursed into — a layer inside a layer would multiply the render cost
 * without adding anything you could not express by adding another layer at the top level.
 *
 * Layers are captured from the settings in force rather than edited in place. A layer with
 * its own full editor would mean a second copy of every panel in the app; taking a snapshot
 * of a look you have already dialled in is both simpler and how you actually work.
 */
@Serializable
data class Layer(
    val name: String = "layer",
    val enabled: Boolean = true,
    val params: RenderSettings = RenderSettings(),
    val blend: LayerBlend = LayerBlend.SCREEN,
    /** 0..100. */
    val opacity: Int = 100,
    /** Offset as a percentage of the output size, so it survives a change of resolution. */
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    /** 10..400 %, about the layer's own centre. */
    val scale: Int = 100,
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    /**
     * The id of a picture of the layer's own, or empty for the picture underneath. A layer
     * with its own picture is fitted inside the frame at 100 % scale and keeps the picture's
     * transparency.
     */
    val image: String = "",
    /**
     * Shows the layer's own picture as it is, in its own colours, instead of rendering it through
     * [params]. Light handed over from Flaresmith arrives like this. Without a picture of its
     * own a layer has nothing to show as it is, and this is ignored.
     */
    val asIs: Boolean = false,
) {
    companion object {
        val OPACITY_RANGE = 0..100
        val SCALE_RANGE = 10..400
        val OFFSET_RANGE = -100..100
    }
}

/** The settings the panels edit: layer [index]'s own, or these when there is no such layer. */
fun RenderSettings.edited(index: Int): RenderSettings = layers.getOrNull(index)?.params ?: this

/**
 * These settings with [changed] put where [edited] read from. A layer never carries a stack of
 * its own, so whatever layers [changed] holds are dropped on the way into one.
 *
 * Changing a layer's settings also ends [Layer.asIs]: a preset or a slider that did nothing
 * visible because the layer was still shown untouched would look broken.
 */
fun RenderSettings.withEdited(index: Int, changed: RenderSettings): RenderSettings =
    if (index in layers.indices) {
        copy(
            layers = layers.mapIndexed { i, layer ->
                val own = changed.copy(layers = emptyList())
                if (i == index) layer.copy(params = own, asIs = layer.asIs && own == layer.params) else layer
            },
        )
    } else {
        changed
    }

/**
 * The animation the panel shows while layer [index] is edited: the picture's clock — one loop
 * for everything — with the layer's own tracks and segments on it.
 */
fun RenderSettings.animationShown(index: Int): AnimationParams {
    val own = layers.getOrNull(index)?.params?.animation ?: return animation
    return animation.copy(tracks = own.tracks, segments = own.segments)
}

/** [changed] taken apart again: its clock goes to the picture, its tracks to layer [index]. */
fun RenderSettings.withAnimationEdited(index: Int, changed: AnimationParams): RenderSettings {
    val layer = layers.getOrNull(index) ?: return copy(animation = changed)
    val clock = changed.copy(tracks = animation.tracks, segments = animation.segments)
    val own = layer.params.animation.copy(enabled = true, tracks = changed.tracks, segments = changed.segments)
    return copy(animation = clock).withEdited(index, layer.params.copy(animation = own))
}

object Layers {
    /** The name of the layer that holds light handed over from Flaresmith. */
    const val HANDED_LIGHT = "light"

    /**
     * [animation] set to play a loop of [frames] lasting [loopMs] as it was made: as long, and a
     * frame of the one for a frame of the other. Only where nothing is animated here yet — a
     * picture with its own animation keeps its timing, and the loop is fitted into that.
     */
    fun timedFor(animation: AnimationParams, frames: Int, loopMs: Int): AnimationParams {
        if (frames <= 1 || loopMs <= 0 || animation.enabled) return animation
        val count = frames.coerceIn(AnimationParams.FRAME_RANGE.first, AnimationParams.FRAME_RANGE.last)
        val fps = Math.round(count * 1000f / loopMs).coerceIn(AnimationParams.FPS_RANGE.first, AnimationParams.FPS_RANGE.last)
        return animation.copy(frames = count, fps = fps)
    }

    /**
     * [layers] with [light] on top, in place of any light handed over before. Told apart by name
     * and by having a picture of its own, so nothing new has to be stored in a look.
     */
    fun withHandedLight(layers: List<Layer>, light: Layer, asSent: Layer? = null): List<Layer> =
        layers.filterNot { (it.name == HANDED_LIGHT || it.name == HANDED_PICTURE) && it.image.isNotEmpty() } +
            listOfNotNull(asSent, light)

    /**
     * The picture as the sender showed it, colours and all, lying under the handed light and
     * over this app's own rendering of it. It is there until a look is chosen: a look is the
     * wish to see the picture rendered, and this layer would hide just that.
     */
    const val HANDED_PICTURE = "picture as sent"

    /**
     * The layer showing picture [image] as it was sent. It covers: screened like a light it
     * would only brighten the rendering under it, and the two would show through each other.
     */
    fun pictureAsSent(params: RenderSettings, image: String): Layer = Layer(
        name = HANDED_PICTURE,
        params = params.copy(layers = emptyList()),
        blend = LayerBlend.NORMAL,
        image = image,
        asIs = true,
    )

    /** [layers] without the picture as it was sent. */
    fun rendered(layers: List<Layer>): List<Layer> =
        layers.filterNot { it.name == HANDED_PICTURE && it.image.isNotEmpty() }
}
