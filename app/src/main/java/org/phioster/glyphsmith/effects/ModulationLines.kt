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
 * Which way the lines run.
 *
 * [HORIZONTAL] is what this pass drew before the axis existed, so it is the default and no
 * saved preset moves. Reusing the spelling of [SortAxis] rather than inventing a second word
 * for the same idea.
 */
@Serializable
enum class LineAxis(val label: String) {
    HORIZONTAL("Horizontal"),
    VERTICAL("Vertical"),
}

/**
 * Controls for turning an image into a field of lines that ride over it.
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
    /** Which way the lines run. Displacement is always across them. */
    val axis: LineAxis = LineAxis.HORIZONTAL,
    /** Peak displacement in pixels, across the line, 0..64. */
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
 * Luminance becomes displacement across a field of lines, plotted as dots.
 *
 * Two things move a dot away from its line: the brightness under it, and a travelling sine. The
 * first is what makes the result an image rather than a pattern; the second is what makes it move,
 * and it is scaled by `waveMix` so the two can be balanced rather than fighting.
 *
 * The output is drawn onto a cleared buffer, not over the source — a line drawing needs the space
 * between the lines to be empty, or the plot disappears into the picture it was made from.
 *
 * Parallel over bands of *destination* rows either way, and that is why both axes are safe. A
 * band owns its own rows of the output and reads anywhere it likes in the source, so no two
 * workers ever write the same pixel. Horizontal lines have to reach for the lines above and below
 * the band, because a dot displaced far enough lands in it; vertical lines never do, since their
 * displacement moves along the row the dot is already on.
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
            when (params.axis) {
                LineAxis.HORIZONTAL -> {
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
                            plotDown(out, width, height, band, x, dotY, radius, colour)
                        }
                    }
                }

                // No reach to compute: a vertical line displaces its dot along the row the dot is
                // already on, so a band only ever needs the lines that cross it — which is all of
                // them — and never touches a row it does not own.
                LineAxis.VERTICAL -> {
                    val lastLine = width / spacing + 1
                    for (line in 0..lastLine) {
                        val baseX = line * spacing
                        if (baseX < 0 || baseX >= width) continue
                        for (y in band) {
                            val sample = source.data[y * width + baseX]
                            val luma = PixelOps.luminance(sample)
                            // Bright pushes left, the same "towards the origin" convention.
                            val fromLuma = (luma - 0.5f) * 2f * amplitude * (1f - waveMix)
                            val fromWave = sin(phase + y * 2f * PI.toFloat() / (spacing * 4f)) *
                                amplitude * waveMix
                            val dotX = baseX - fromLuma - fromWave
                            val colour = colourFor(params, sample, luma)
                            plotAcross(out, width, y, dotX, radius, colour)
                        }
                    }
                }
            }
        }
        return source.derive(out)
    }

    /** Fills a horizontal line's dot, clipped to this band so bands never write into each other. */
    @Suppress("LongParameterList")
    private fun plotDown(
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

    /**
     * Fills a vertical line's dot: one row, clipped to the image.
     *
     * No band argument, and that is the point — the dot never leaves row [y], which the calling
     * band owns by construction.
     */
    private fun plotAcross(
        out: IntArray,
        width: Int,
        y: Int,
        centreX: Float,
        radius: Float,
        colour: Int,
    ) {
        val from = (centreX - radius).roundToInt().coerceAtLeast(0)
        val until = (centreX + radius).roundToInt().coerceAtMost(width - 1)
        var x = from
        while (x <= until) {
            out[y * width + x] = colour
            x++
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
