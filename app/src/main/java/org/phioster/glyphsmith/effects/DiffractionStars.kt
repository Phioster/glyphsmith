package org.phioster.glyphsmith.effects

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Star flares on the brightest points.
 *
 * A directional blur smears both ways at once, so each pass produces two opposing rays —
 * `rays / 2` passes give the requested number of points. Rays are accumulated on a
 * downscaled buffer and added back as light, same as the glow.
 */
object DiffractionStars {

    private const val WORK_MAX_SIDE = 320

    fun apply(source: Pixels, params: DiffractionStarsParams): Pixels {
        if (!params.enabled || params.intensity <= 0 || params.length <= 0) return source

        val scale = min(1f, WORK_MAX_SIDE.toFloat() / max(source.width, source.height))
        val workWidth = max(1, (source.width * scale).roundToInt())
        val workHeight = max(1, (source.height * scale).roundToInt())

        val small = PixelOps.scale(source, workWidth, workHeight)
        val bright = PixelOps.brightPass(small, params.threshold, params.thresholdSmoothing)

        val axes = max(1, params.rays / 2)
        val step = 180f / axes
        val reach = max(1, (params.length * scale).roundToInt())
        val falloff = params.falloff / 10f

        val accumulated = bright.derive(bright.buffer(), workWidth, workHeight)
        for (axis in 0 until axes) {
            val degrees = params.angle + axis * step
            val ray = PixelOps.directionalBlur(bright, degrees, reach, 1) { radius ->
                PixelOps.inversePowerKernel(
                    radius = radius,
                    falloff = falloff,
                    epsilon = 0.35f,
                    distanceScale = 1f,
                    normalise = true,
                )
            }
            // Rays add rather than average: crossing rays are meant to be brighter.
            PixelOps.addLight(accumulated, ray, 1f)
        }

        val upscaled = PixelOps.scale(accumulated, source.width, source.height)
        PixelOps.addLight(source, upscaled, params.intensity / 500f)
        return source
    }
}
