package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RenderContext
import org.phioster.glyphsmith.core.pipeline.RowParallel
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** What colour the plotted dots take. */
@Serializable
enum class ModulationColorMode {
    /** The colour the sample was lifted from — the image, plotted. */
    SOURCE,

    /** One ink colour throughout, so the line drawing reads as a drawing. */
    INK,

    /** Brightness mapped onto the terminal green, which is where this app's look comes from. */
    PHOSPHOR,
}

/**
 * Controls for turning an image into a field of horizontal lines that ride over it.
 *
 * `amplitude` is in pixels rather than a percentage because the displacement has to be readable
 * against `lineSpacing` — a percentage of the image height would mean the look changed every time
 * the preview budget did.
 */
@Serializable
data class ModulationLinesParams(
    val enabled: Boolean = false,
    /** Pixels between lines, 2..64. Fewer lines read as a graph, many as a texture. */
    val lineSpacing: Int = 8,
    /** Peak vertical displacement in pixels, 0..64. */
    val amplitude: Int = 6,
    /** 0..100 — how fast the wave travels over an animation. 0 holds it still. */
    val waveSpeed: Int = 30,
    /** Dot diameter in pixels, 1..8. */
    val dotSize: Int = 2,
    /** 0..100 % of a cycle, added to the animated phase so a still also has a look. */
    val phase: Int = 0,
    /** 0..100 — how much of the displacement comes from the wave rather than from luminance. */
    val waveMix: Int = 40,
    val colorMode: ModulationColorMode = ModulationColorMode.SOURCE,
    val inkColor: Int = 0xFF00FF41.toInt(),
    val backgroundColor: Int = 0xFF000000.toInt(),
)

/**
 * Luminance becomes vertical displacement along horizontal lines, plotted as dots.
 *
 * Two things move a dot away from its line: the brightness under it, and a travelling sine. The
 * first is what makes the result an image rather than a pattern; the second is what makes it move,
 * and it is scaled by `waveMix` so the two can be balanced rather than fighting.
 *
 * The output is drawn onto a cleared buffer, not over the source — a line drawing needs the space
 * between the lines to be empty, or the plot disappears into the picture it was made from.
 *
 * Parallel over bands of *destination* rows. Each band owns its own rows of the output and reads
 * anywhere it likes in the source, so no two workers ever write the same pixel. The dots are drawn
 * by walking source columns per band rather than by scattering, which is what keeps that true.
 */
object ModulationLines {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::modulationLines,
        { copy(modulationLines = it) },
        ModulationLinesParams::enabled,
        randomise = { roll ->
            copy(
                enabled = true,
                lineSpacing = roll.random.nextInt(4, 16),
                amplitude = roll.random.nextInt(3, 20),
                colorMode = ModulationColorMode.entries.random(roll.random),
            )
        },
    ) { pixels, params, ctx -> apply(pixels, params, ctx) }

    fun apply(source: Pixels, params: ModulationLinesParams, ctx: RenderContext): Pixels {
        if (!params.enabled) return source
        if (params.amplitude == 0 && params.waveMix == 0) return source

        val width = source.width
        val height = source.height
        // Taken before the fork: the pool is single-threaded by contract.
        val out = source.buffer()
        val spacing = params.lineSpacing.coerceIn(2, 64)
        val amplitude = params.amplitude.coerceIn(0, 64).toFloat()
        val radius = params.dotSize.coerceIn(1, 8) / 2f
        val waveMix = params.waveMix.coerceIn(0, 100) / 100f
        // The animated phase and the manual one are the same quantity, so they simply add.
        val phase = (ctx.time * params.waveSpeed.coerceIn(0, 100) / 10f + params.phase / 100f) *
            2f * PI.toFloat()
        val background = params.backgroundColor

        RowParallel.rows(height) { band ->
            for (y in band) {
                val rowStart = y * width
                for (x in 0 until width) out[rowStart + x] = background
            }
            // Every line whose dots could reach this band, plus the amplitude either side.
            val reach = amplitude + radius + 1f
            val firstLine = ((band.first - reach) / spacing).toInt() - 1
            val lastLine = ((band.last + reach) / spacing).toInt() + 1

            for (line in firstLine..lastLine) {
                val baseY = line * spacing
                if (baseY < 0 || baseY >= height) continue
                for (x in 0 until width) {
                    val sample = source.data[baseY * width + x]
                    val luma = PixelOps.luminance(sample)
                    // Bright pushes up, which is the convention a plotted waveform reads by.
                    val fromLuma = (luma - 0.5f) * 2f * amplitude * (1f - waveMix)
                    val fromWave = sin(phase + x * 2f * PI.toFloat() / (spacing * 4f)) *
                        amplitude * waveMix
                    val dotY = baseY - fromLuma - fromWave
                    val colour = colourFor(params, sample, luma)
                    plot(out, width, height, band, x, dotY, radius, colour)
                }
            }
        }
        return source.derive(out)
    }

    /** Fills the dot's pixels, clipped to this band so bands never write into each other. */
    @Suppress("LongParameterList")
    private fun plot(
        out: IntArray,
        width: Int,
        height: Int,
        band: IntRange,
        x: Int,
        centreY: Float,
        radius: Float,
        colour: Int,
    ) {
        val from = (centreY - radius).roundToInt().coerceAtLeast(band.first)
        val until = (centreY + radius).roundToInt().coerceAtMost(minOf(band.last, height - 1))
        var y = from
        while (y <= until) {
            out[y * width + x] = colour
            y++
        }
    }

    private fun colourFor(params: ModulationLinesParams, sample: Int, luma: Float): Int =
        when (params.colorMode) {
            ModulationColorMode.SOURCE -> sample or (0xFF shl 24)
            ModulationColorMode.INK -> params.inkColor
            ModulationColorMode.PHOSPHOR -> {
                val level = (luma * 255f).toInt().coerceIn(0, 255)
                PixelOps.argb(255, level / 4, level, level / 3)
            }
        }
}
