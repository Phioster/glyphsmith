package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.core.pipeline.RowParallel

/** Controls for the tube geometry. */
@Serializable
data class CrtWarpParams(
    val enabled: Boolean = false,
    /** 0..100 — how far the glass bulges. 0 is a flat panel. */
    val warpCurvature: Int = 25,
    /** 0..100 — corner shadow, the falloff that comes with the curvature. */
    val vignetteIntensity: Int = 35,
    /** 0..100 — how far past the glass edge is black rather than stretched. */
    val bezelBleed: Int = 0,
)

/**
 * Barrel distortion, as a curved screen actually behaves.
 *
 * Mapped **inverse**: the loop walks the destination and asks where in the source each output pixel
 * came from. Doing it forwards — pushing source pixels outwards — leaves gaps wherever the
 * expansion is more than one pixel, and filling those is a harder problem than not creating them.
 *
 * Sampling is **bilinear**. [Chromatic] has a nearest-neighbour sampler that would be cheaper, but
 * a warp resamples on a smooth curve, and nearest-neighbour under a smooth curve produces visible
 * staircase artefacts along every edge — exactly where a curved screen should look smoothest.
 *
 * Note the overlap: [PostProcessing] already has a vignette. This one belongs to the tube — it
 * follows the same curvature the warp uses — so enabling both darkens the corners twice.
 *
 * Parallel over destination rows, which is trivially safe here: each output pixel is written once
 * by the band that owns its row, and the source is only ever read.
 */
object CrtWarp {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(EffectStack::crtWarp, CrtWarpParams::enabled) { pixels, params, ctx ->
        apply(pixels, params, ctx)
    }

    fun apply(source: Pixels, params: CrtWarpParams, ctx: RenderContext): Pixels {
        if (!params.enabled) return source
        if (params.warpCurvature == 0 && params.vignetteIntensity == 0) return source

        val width = source.width
        val height = source.height
        if (width < 2 || height < 2) return source

        val out = source.buffer()
        // A curvature of 100 bows the edge in by about a third of the frame, which is roughly
        // where a real tube of this era sat before it started reading as a fisheye.
        val curve = params.warpCurvature.coerceIn(0, 100) / 100f * 0.35f
        val vignette = params.vignetteIntensity.coerceIn(0, 100) / 100f
        val bleed = params.bezelBleed.coerceIn(0, 100) / 100f
        val halfW = (width - 1) / 2f
        val halfH = (height - 1) / 2f

        RowParallel.rows(height) { band ->
            for (y in band) {
                // Normalised to -1..1 so the curvature means the same thing at any output size.
                val ny = (y - halfH) / halfH
                val rowStart = y * width
                for (x in 0 until width) {
                    val nx = (x - halfW) / halfW
                    // The standard barrel form: displacement grows with the square of the radius,
                    // so the centre stays put and the corners move most.
                    val radiusSq = nx * nx + ny * ny
                    val scale = 1f + curve * radiusSq
                    val sx = nx * scale * halfW + halfW
                    val sy = ny * scale * halfH + halfH

                    out[rowStart + x] = if (sx < 0f || sy < 0f || sx > width - 1f || sy > height - 1f) {
                        // Past the glass. Bleed lets the edge pixel stretch instead, which reads as
                        // a screen filling its bezel rather than as a black frame.
                        if (bleed > 0f) {
                            bilinear(
                                source.data, width, height,
                                sx.coerceIn(0f, width - 1f), sy.coerceIn(0f, height - 1f),
                            )
                        } else {
                            OPAQUE_BLACK
                        }
                    } else {
                        val sample = bilinear(source.data, width, height, sx, sy)
                        if (vignette > 0f) shade(sample, radiusSq, vignette) else sample
                    }
                }
            }
        }
        return source.derive(out)
    }

    /** Weighted average of the four neighbours, per channel, alpha included. */
    private fun bilinear(data: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceIn(0, width - 1)
        val y0 = y.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = x - x0
        val fy = y - y0

        val topLeft = data[y0 * width + x0]
        val topRight = data[y0 * width + x1]
        val bottomLeft = data[y1 * width + x0]
        val bottomRight = data[y1 * width + x1]

        fun channel(shift: Int): Int {
            val a = (topLeft shr shift) and 0xFF
            val b = (topRight shr shift) and 0xFF
            val c = (bottomLeft shr shift) and 0xFF
            val d = (bottomRight shr shift) and 0xFF
            val top = a + (b - a) * fx
            val bottom = c + (d - c) * fx
            return (top + (bottom - top) * fy).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Corner falloff on the same radius the warp uses, so shadow and curvature agree. */
    private fun shade(pixel: Int, radiusSq: Float, intensity: Float): Int {
        val factor = (1f - intensity * (radiusSq / 2f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
        return PixelOps.argb(
            PixelOps.alphaOf(pixel),
            (PixelOps.redOf(pixel) * factor).toInt(),
            (PixelOps.greenOf(pixel) * factor).toInt(),
            (PixelOps.blueOf(pixel) * factor).toInt(),
        )
    }

    private const val OPAQUE_BLACK = 0xFF000000.toInt()
}
