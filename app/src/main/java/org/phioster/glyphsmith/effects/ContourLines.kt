package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.phioster.glyphsmith.core.image.Pixels
import org.phioster.glyphsmith.core.pipeline.RowParallel

/**
 * Contour lines around whatever is bright, like the height lines of a map.
 *
 * The bright parts of the picture are spread into a soft field that falls away around them, and
 * a line is drawn wherever that field crosses one of a set of evenly spaced levels. The picture
 * itself can be cut into a few hard neon tones underneath. Played as a loop the levels move, so
 * the lines travel outwards or inwards.
 */
@Serializable
data class ContourLinesParams(
    val enabled: Boolean = false,
    /** 4..80 — how many levels, and so how densely the lines lie. */
    val lines: Int = 34,
    /** 0..100 — line width, relative to the picture. */
    val thickness: Int = 35,
    /** 0..100 — how far around the subject the lines reach. */
    val reach: Int = 50,
    /** 0..100 — where brightness starts to count as subject. */
    val threshold: Int = 25,
    /** 0..100 — how far the subject is drawn out sideways before the field is made. */
    val smear: Int = 50,
    /** 0..100 — how wavy the lines are. */
    val wave: Int = 60,
    /** 0..100 — how much of the picture is replaced by hard neon tones. */
    val neon: Int = 85,
    /** 0..359 — the lines' colour. */
    val hue: Int = 120,
    /** -4..4 — how many levels the lines travel over one loop; negative runs inwards. */
    val flow: Int = 1,
)

object ContourLines {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::contourLines,
        { copy(contourLines = it) },
        ContourLinesParams::enabled,
        randomise = { roll ->
            copy(
                enabled = true,
                lines = roll.random.nextInt(12, 50),
                hue = roll.random.nextInt(0, 360),
                wave = roll.random.nextInt(0, 90),
            )
        },
    ) { pixels, params, ctx -> apply(pixels, params, ctx.time) }

    /** The field is smooth, so it is made small and read back between its points. */
    private const val FIELD_SIDE = 384
    private const val TWO_PI = (2 * PI).toFloat()

    /** Dark to bright: what the picture's tones are cut into. */
    private val NEON = intArrayOf(0x000000, 0x0D0005, 0xD90D40, 0xFF1AE6, 0x8CFF1A, 0xFFFF33)
    private const val INNER_RED = 0xFF
    private const val INNER_GREEN = 0x1A
    private const val INNER_BLUE = 0x4D
    private const val HALO_RED = 0.75f
    private const val HALO_GREEN = 0.05f
    private const val HALO_BLUE = 0.12f

    fun apply(source: Pixels, params: ContourLinesParams, time: Float = 0f): Pixels {
        val w = source.width
        val h = source.height
        if (!params.enabled || w < 16 || h < 16) return source

        val scale = minOf(1f, FIELD_SIDE.toFloat() / maxOf(w, h))
        val fw = (w * scale).roundToInt().coerceAtLeast(8)
        val fh = (h * scale).roundToInt().coerceAtLeast(8)
        val t = time - floor(time)
        val lines = params.lines.coerceIn(4, 80).toFloat()

        // The subject: what is bright, drawn out sideways.
        val start = params.threshold.coerceIn(0, 100) / 100f
        val subject = FloatArray(fw * fh) { i ->
            val x = ((i % fw + 0.5f) / fw * w).toInt().coerceAtMost(w - 1)
            val y = ((i / fw + 0.5f) / fh * h).toInt().coerceAtMost(h - 1)
            ((PixelOps.luminance(source.data[y * w + x]) - start) * 2.2f).coerceIn(0f, 1f)
        }
        val drawn = subject.copyOf()
        soften(drawn, fw, fh, (fw * 0.06f * params.smear / 100f).toInt(), across = true, down = false)
        for (i in subject.indices) subject[i] = maxOf(subject[i], drawn[i] * 1.3f).coerceAtMost(1f)

        // The field falls away around it; a waver keeps the lines from being perfect shells.
        val field = subject.copyOf()
        soften(field, fw, fh, (fw * 0.17f * params.reach / 100f).toInt(), across = true, down = true)
        val wave = params.wave / 100f * 0.012f
        if (wave > 0f) {
            for (i in field.indices) {
                val u = (i % fw).toFloat() / fw
                val v = (i / fw).toFloat() / fw
                val both = sin(TWO_PI * (v * 14 + u * 5 + t)) + sin(TWO_PI * (u * 23 - v * 9 - t))
                field[i] += wave * both * (1.5f - field[i] * 2f).coerceIn(0f, 1f)
            }
        }

        val shift = t * params.flow
        val thick = params.thickness / 100f * w / 367f + 0.5f
        val neon = params.neon.coerceIn(0, 100) / 100f
        val line = hueColour(params.hue)
        val data = source.data

        RowParallel.rows(h) { band ->
            for (y in band) {
                val fy = ((y + 0.5f) / h * fh - 0.5f).coerceIn(0f, fh - 1.001f)
                val y0 = fy.toInt()
                val ty = fy - y0
                val y1 = minOf(y0 + 1, fh - 1)
                for (x in 0 until w) {
                    val fx = ((x + 0.5f) / w * fw - 0.5f).coerceIn(0f, fw - 1.001f)
                    val x0 = fx.toInt()
                    val tx = fx - x0
                    val x1 = minOf(x0 + 1, fw - 1)
                    val a = field[y0 * fw + x0]
                    val b = field[y0 * fw + x1]
                    val c = field[y1 * fw + x0]
                    val d = field[y1 * fw + x1]
                    val f = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
                    // The slope within this cell, per output pixel: it turns a level into a width.
                    val gx = ((b - a) * (1 - ty) + (d - c) * ty) * fw / w
                    val gy = ((c - a) * (1 - tx) + (d - b) * tx) * fh / h
                    val slope = sqrt(gx * gx + gy * gy) * lines + 1e-4f
                    val level = f * lines - shift
                    val away = abs(level - floor(level) - 0.5f) / slope
                    val s = (subject[y0 * fw + x0] * (1 - tx) + subject[y0 * fw + x1] * tx) * (1 - ty) +
                        (subject[y1 * fw + x0] * (1 - tx) + subject[y1 * fw + x1] * tx) * ty
                    val ink = (thick - away).coerceIn(0f, 1f) * (f * 8f).coerceIn(0f, 1f) *
                        (1.2f - s * 1.5f).coerceIn(0f, 1f)

                    val p = data[y * w + x]
                    val lum = PixelOps.luminance(p)
                    val tone = NEON[(lum * 1.25f * NEON.size).toInt().coerceIn(0, NEON.size - 1)]
                    val halo = (f * 1.6f - 0.25f).coerceIn(0f, 1f) * (1f - lum * 3f).coerceIn(0f, 1f) * neon * 255f
                    var r = PixelOps.redOf(p) * (1 - neon) + PixelOps.redOf(tone) * neon + halo * HALO_RED
                    var g = PixelOps.greenOf(p) * (1 - neon) + PixelOps.greenOf(tone) * neon + halo * HALO_GREEN
                    var bl = PixelOps.blueOf(p) * (1 - neon) + PixelOps.blueOf(tone) * neon + halo * HALO_BLUE
                    if (ink > 0f) {
                        // Close to the subject the lines turn red, as if heated by it.
                        val inner = (f * 2.2f - 0.9f).coerceIn(0f, 1f)
                        val lr = PixelOps.redOf(line) * (1 - inner) + INNER_RED * inner
                        val lg = PixelOps.greenOf(line) * (1 - inner) + INNER_GREEN * inner
                        val lb = PixelOps.blueOf(line) * (1 - inner) + INNER_BLUE * inner
                        r = r * (1 - ink) + lr * ink
                        g = g * (1 - ink) + lg * ink
                        bl = bl * (1 - ink) + lb * ink
                    }
                    data[y * w + x] = PixelOps.argb(
                        PixelOps.alphaOf(p),
                        r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), bl.toInt().coerceIn(0, 255),
                    )
                }
            }
        }
        return source
    }

    /** Three box blurs in place, which is as good as a Gaussian for a field nobody sees. */
    private fun soften(field: FloatArray, w: Int, h: Int, radius: Int, across: Boolean, down: Boolean) {
        if (radius < 1) return
        val line = FloatArray(maxOf(w, h))
        repeat(3) {
            if (across) for (y in 0 until h) box(field, y * w, 1, w, radius, line)
            if (down) for (x in 0 until w) box(field, x, w, h, radius, line)
        }
    }

    private fun box(field: FloatArray, start: Int, step: Int, count: Int, radius: Int, line: FloatArray) {
        for (i in 0 until count) line[i] = field[start + i * step]
        var sum = 0f
        for (i in -radius..radius) sum += line[i.coerceIn(0, count - 1)]
        val size = 2 * radius + 1
        for (i in 0 until count) {
            field[start + i * step] = sum / size
            sum += line[(i + radius + 1).coerceAtMost(count - 1)] - line[(i - radius).coerceAtLeast(0)]
        }
    }

    /** A full, slightly soft colour at [hue] degrees. */
    private fun hueColour(hue: Int): Int {
        val h = Math.floorMod(hue, 360) / 60f
        val x = 1f - abs(h % 2f - 1f)
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(1f, x, 0f)
            1 -> Triple(x, 1f, 0f)
            2 -> Triple(0f, 1f, x)
            3 -> Triple(0f, x, 1f)
            4 -> Triple(x, 0f, 1f)
            else -> Triple(1f, 0f, x)
        }
        val low = 0.1f
        return PixelOps.argb(
            255,
            ((low + (1 - low) * r) * 255).toInt(), ((low + (1 - low) * g) * 255).toInt(), ((low + (1 - low) * b) * 255).toInt(),
        )
    }
}
