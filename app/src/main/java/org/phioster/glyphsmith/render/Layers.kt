package org.phioster.glyphsmith.render

import kotlinx.serialization.Serializable

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
 */
fun RenderSettings.withEdited(index: Int, changed: RenderSettings): RenderSettings =
    if (index in layers.indices) {
        copy(layers = layers.mapIndexed { i, layer -> if (i == index) layer.copy(params = changed.copy(layers = emptyList())) else layer })
    } else {
        changed
    }
