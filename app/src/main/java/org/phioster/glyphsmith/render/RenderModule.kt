package org.phioster.glyphsmith.render

import android.graphics.Bitmap

/**
 * Whatever a render module produced besides pixels.
 *
 * Deliberately empty. The shared pipeline carries this value from the module that made it to
 * the caller that asked for the render, and is not allowed to know what is inside it — that is
 * the whole point: a character grid and the typeface it was drawn with are Glyph Art's own
 * business, and the pipeline used to name both types to pass them along. ARCHITECTURE.md puts
 * it as "shared code must not assume that every render module provides glyph output"; a marker
 * the module defines its own implementation of is how that assumption is made unstateable
 * rather than merely discouraged.
 *
 * A caller that does want the glyph half asks for it by type — see the view model, which is
 * allowed to know both modules because composing them is its job.
 */
interface RenderModuleOutput

/**
 * A render, before the effect chain and before any layers.
 *
 * [cols] and [rows] are the grid the module worked on, and [outputWidth] / [outputHeight] the
 * size the same settings would produce at full export scale. Both are carried for every module
 * because the readouts and the status line need them whether or not there are characters
 * involved.
 */
class ModuleRender(
    val bitmap: Bitmap,
    val cols: Int,
    val rows: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    /** Null when the module has nothing to hand on beyond the pixels. */
    val output: RenderModuleOutput? = null,
)

/**
 * One way of turning a source image into a render.
 *
 * The seam ARCHITECTURE.md calls a Render Module, stated as the *execution* half of it. The
 * declarative half — id, display name, capabilities — is [RenderModuleProvider], and the two are
 * deliberately not the same object: the provider list is read by the UI and by the id tests,
 * both of which run without an Android runtime, while an implementation rasterises.
 *
 * Effects and layers are not the module's business. Every module used to end by applying the
 * effect chain itself, identically, which meant three places could drift apart on the order
 * effects run in. They now run once, in the pipeline, on whatever the module produced.
 */
interface RenderModule {

    @Suppress("LongParameterList")
    fun render(
        pixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        params: RenderSettings,
        maxSide: Int,
    ): ModuleRender
}

/**
 * Which module serves which mode.
 *
 * The binding lives outside the shared render core on purpose. Something has to name both the
 * pixel module and the glyph module in the same breath, and whatever does that is by definition
 * not neutral — so it is the application that assembles them and hands the result to the
 * pipeline, rather than the pipeline reaching into Glyph Art to fetch one.
 */
fun interface RenderModuleSet {
    fun moduleFor(mode: RenderMode): RenderModule
}
