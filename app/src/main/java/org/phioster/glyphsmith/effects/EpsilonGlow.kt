package org.phioster.glyphsmith.effects

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Threshold → anisotropic blur → add.
 *
 * The blur runs on a downscaled buffer along rotated axes (see [PixelOps.directionalBlur]),
 * with an inverse-power kernel `w(d) = 1 / ((d·scale)ⁿ + ε)`. Radius compensation keeps the
 * kernel's energy constant so changing the radius changes reach, not brightness.
 */
object EpsilonGlow {

    /** Longest edge of the buffer the blur actually runs on. */
    private const val WORK_MAX_SIDE = 360

    fun apply(source: Pixels, params: GlowParams): Pixels {
        if (!params.enabled || params.intensity <= 0 || params.radius <= 0) return source

        val scale = min(1f, WORK_MAX_SIDE.toFloat() / max(source.width, source.height))
        val workWidth = max(1, (source.width * scale).roundToInt())
        val workHeight = max(1, (source.height * scale).roundToInt())

        val small = PixelOps.scale(source, workWidth, workHeight)
        val bright = PixelOps.brightPass(small, params.threshold, params.thresholdSmoothing)

        val across = max(1, (params.radius * scale).roundToInt())
        val along = max(1, (across * (params.aspectRatio / 100f)).roundToInt())
        val blurred = PixelOps.directionalBlur(bright, params.direction.toFloat(), along, across) { radius ->
            PixelOps.inversePowerKernel(
                radius = radius,
                falloff = params.falloff / 10f,
                epsilon = params.epsilon / 100f,
                distanceScale = params.distanceScale / 150f,
                normalise = params.radiusCompensation,
            )
        }

        val upscaled = PixelOps.scale(blurred, source.width, source.height)
        PixelOps.addLight(source, upscaled, params.intensity / 500f)
        return source
    }
}
