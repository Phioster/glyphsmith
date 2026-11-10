package org.phioster.glyphsmith.effects

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import org.phioster.glyphsmith.core.image.Pixels

/**
 * Overlay textures, generated rather than shipped.
 *
 * The vendor sells texture packs for this — VHS, scanner trash, grunge paper — but their own
 * CRT tutorial builds the texture *out of the input image* instead of laying one on top, and
 * that is the approach taken here. It needs no third-party assets, it costs nothing to
 * ship, and a texture derived from the picture sits on it better than a stock scan does.
 */
enum class TextureKind(val label: String) {
    CRT_STRIPES("CRT Stripes"),
    CRT_APERTURE("CRT Aperture"),
    HALFTONE("Halftone"),
    PAPER_GRAIN("Paper Grain"),
    PAPER_FIBRE("Paper Fibre"),
    SCANNER_STREAKS("Scanner Streaks"),
    STATIC("Static"),
    VHS_BANDS("VHS Bands"),

    // The three below colour each channel separately: phosphor stripes, a slot mask, and colour
    // noise. They bring red, green and blue into a monochrome dither the way a tube does.
    RGB_TRIAD("RGB Triad"),
    RGB_SLOT_MASK("RGB Slot Mask"),
    RGB_NOISE("RGB Noise"),
    ;

    /** Whether this texture has its own value per colour channel rather than one for all. */
    val perChannel: Boolean get() = this == RGB_TRIAD || this == RGB_SLOT_MASK || this == RGB_NOISE
}

enum class BlendMode(val label: String) {
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    SOFT_LIGHT("Soft Light"),
}

@Serializable
data class SubtextureParams(
    val enabled: Boolean = false,
    val kind: TextureKind = TextureKind.CRT_STRIPES,
    val blend: BlendMode = BlendMode.MULTIPLY,
    /** Feature size in pixels. */
    val scale: Int = 4,
    /** 0..100 — how much of the texture is mixed in. */
    val intensity: Int = 50,
    val angle: Int = 0,
    val seed: Int = 1,
    /**
     * Modulate the texture by the image's own local detail instead of laying it on flat.
     * The texture then bites where the picture already has structure and leaves flat areas
     * alone, which is what stops it reading as a sticker.
     */
    val fromSource: Boolean = false,
)

object Subtexture {

    /** Where the chain reaches this pass, and what switches it on. See [EffectPass]. */
    val pass = EffectPass(
        EffectStack::subtexture,
        { copy(subtexture = it) },
        SubtextureParams::enabled,
        randomise = { roll ->
            copy(
                enabled = true,
                kind = TextureKind.entries.random(roll.random),
                intensity = roll.random.nextInt(20, 60),
            )
        },
    ) { pixels, params, _ -> apply(pixels, params) }

    private const val TAU = 2.0 * PI

    fun apply(source: Pixels, params: SubtextureParams): Pixels {
        if (!params.enabled || params.intensity == 0) return source

        val amount = (params.intensity / 100f).coerceIn(0f, 1f)
        val scale = params.scale.coerceAtLeast(1).toFloat()
        val radians = params.angle * PI / 180.0
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()

        // The high-pass is only computed when it is actually going to be used — it costs a
        // full separable blur, which is the most expensive thing in this file by far.
        val detail = if (params.fromSource) detailOf(source, params.scale) else null

        val centreX = source.width / 2f
        val centreY = source.height / 2f

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val index = y * source.width + x
                val dx = x - centreX
                val dy = y - centreY
                val u = (dx * c + dy * s) / scale
                val v = (-dx * s + dy * c) / scale

                val pixel = source.data[index]
                if (params.kind.perChannel) {
                    val at = Spot(u, v, x, y, detail?.get(index))
                    source.data[index] = blendRgb(pixel, params, at, amount)
                    continue
                }
                var value = texture(params.kind, u, v, x, y, params.seed)
                if (detail != null) value = 0.5f + (value - 0.5f) * detail[index]

                source.data[index] = PixelOps.argb(
                    PixelOps.alphaOf(pixel),
                    blendChannel(PixelOps.redOf(pixel), value, params.blend, amount),
                    blendChannel(PixelOps.greenOf(pixel), value, params.blend, amount),
                    blendChannel(PixelOps.blueOf(pixel), value, params.blend, amount),
                )
            }
        }
        return source
    }

    /**
     * Per-pixel local contrast in 0..1: the image minus its own blur, normalised.
     *
     * This is the "make the texture out of the input" idea in one line — where the picture
     * has edges the value approaches 1 and the texture comes through at full strength; where
     * it is flat the value falls to 0 and the texture fades out.
     */
    private fun detailOf(source: Pixels, radius: Int): FloatArray {
        val blurred = PixelOps.blurRgba(source, radius.coerceIn(1, 12))
        val out = FloatArray(source.data.size)
        for (i in out.indices) {
            val difference = abs(
                PixelOps.luminance(source.data[i]) - PixelOps.luminance(blurred.data[i]),
            )
            // ×4 because local contrast in a rendered glyph grid is small in absolute terms;
            // without it the modulation would switch the texture off almost everywhere.
            out[i] = (difference * 4f).coerceIn(0f, 1f)
        }
        return out
    }

    // A lit phosphor and an unlit one. Their mean sits a little under neutral 0.5, so the
    // stripes colour the picture without the whole of it brightening under Multiply.
    private const val LIT = 0.85f
    private const val DARK = 0.2f

    /** Where a pixel sits in texture space, and the source's local detail there if it is used. */
    private class Spot(val u: Float, val v: Float, val x: Int, val y: Int, val detail: Float?)

    private fun blendRgb(pixel: Int, params: SubtextureParams, at: Spot, amount: Float): Int =
        PixelOps.argb(
            PixelOps.alphaOf(pixel),
            blendChannel(PixelOps.redOf(pixel), channelValue(params, at, 0), params.blend, amount),
            blendChannel(PixelOps.greenOf(pixel), channelValue(params, at, 1), params.blend, amount),
            blendChannel(PixelOps.blueOf(pixel), channelValue(params, at, 2), params.blend, amount),
        )

    private fun channelValue(params: SubtextureParams, at: Spot, channel: Int): Float {
        val value = rgbTexture(params.kind, at.u, at.v, at.x, at.y, params.seed, channel)
        return if (at.detail == null) value else 0.5f + (value - 0.5f) * at.detail
    }

    /**
     * One channel's value of a per-channel texture. A triad is one [SubtextureParams.scale]
     * wide: red, green and blue thirds side by side.
     */
    @Suppress("LongParameterList")
    internal fun rgbTexture(
        kind: TextureKind,
        u: Float,
        v: Float,
        x: Int,
        y: Int,
        seed: Int,
        channel: Int,
    ): Float {
        val third = Math.floorMod(floor(u * 3f).toInt(), 3)
        return when (kind) {
            TextureKind.RGB_TRIAD -> if (third == channel) LIT else DARK

            // Triads broken into slots by a dark gap, every other triad half a slot lower,
            // the way the mask of a consumer tube is cut.
            TextureKind.RGB_SLOT_MASK -> {
                val column = floor(u).toInt()
                val shifted = v + if (Math.floorMod(column, 2) == 0) 0f else 0.5f
                if (frac(shifted) > 0.8f) DARK else if (third == channel) LIT else DARK
            }

            TextureKind.RGB_NOISE -> {
                val cell = hash(floor(u).toInt(), floor(v).toInt(), seed * 3 + channel)
                0.5f + (cell - 0.5f) * 0.9f
            }

            else -> texture(kind, u, v, x, y, seed)
        }
    }

    /** Texture value in 0..1, where 0.5 is neutral for every blend mode below. */
    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    private fun texture(
        kind: TextureKind,
        u: Float,
        v: Float,
        x: Int,
        y: Int,
        seed: Int,
    ): Float = when (kind) {
        TextureKind.CRT_STRIPES -> 0.5f + 0.5f * sin(TAU * u).toFloat()

        // A slot mask: vertical triads broken by a horizontal gap, offset every other row.
        TextureKind.CRT_APERTURE -> {
            val row = floor(v).toInt()
            val shift = if (Math.floorMod(row, 2) == 0) 0f else 0.5f
            val stripe = 0.5f + 0.5f * sin(TAU * (u + shift)).toFloat()
            val gap = 0.5f + 0.5f * sin(TAU * v * 0.5f).toFloat()
            stripe * 0.7f + gap * 0.3f
        }

        // Rotated dot grid — the same radial-within-a-cell shape the orb dither uses.
        TextureKind.HALFTONE -> {
            val dx = frac(u) - 0.5f
            val dy = frac(v) - 0.5f
            (hypot(dx, dy) / 0.70710678f).coerceIn(0f, 1f)
        }

        TextureKind.PAPER_GRAIN -> hash(x, y, seed)

        // Long horizontal filaments: noise stretched hard along one axis.
        TextureKind.PAPER_FIBRE -> {
            val a = hash(floor(u * 0.2f).toInt(), floor(v * 3f).toInt(), seed)
            val b = hash(floor(u * 0.05f).toInt(), floor(v * 7f).toInt(), seed + 17)
            (a * 0.6f + b * 0.4f)
        }

        // Full-height streaks with a constant value per column — a dirty scanner bar.
        TextureKind.SCANNER_STREAKS -> {
            val column = hash(floor(u).toInt(), 0, seed)
            val fine = hash(floor(u * 4f).toInt(), 0, seed + 3)
            (column * 0.7f + fine * 0.3f)
        }

        TextureKind.STATIC -> {
            // Rows of noise rather than a field of it: tape dropout is horizontal.
            val row = hash(0, y, seed)
            if (row > 0.85f) hash(x, y, seed + 1) else 0.5f
        }

        // Wide soft bands with a faint wobble, the way a worn tape breathes.
        // Seen as one value — only when something asks for a single channel — a per-channel
        // texture is its mean, which is a flat, slightly dark grey.
        TextureKind.RGB_TRIAD, TextureKind.RGB_SLOT_MASK -> (LIT + 2f * DARK) / 3f
        TextureKind.RGB_NOISE -> hash(floor(u).toInt(), floor(v).toInt(), seed * 3)

        TextureKind.VHS_BANDS -> {
            val band = 0.5f + 0.5f * sin(TAU * v * 0.25f).toFloat()
            val wobble = 0.5f + 0.5f * sin(TAU * (v * 0.03f + u * 0.01f)).toFloat()
            band * 0.75f + wobble * 0.25f
        }
    }

    private fun blendChannel(base: Int, texture: Float, mode: BlendMode, amount: Float): Int {
        val b = base / 255f
        val blended = when (mode) {
            BlendMode.MULTIPLY -> b * texture * 2f
            BlendMode.SCREEN -> 1f - (1f - b) * (1f - texture)
            BlendMode.OVERLAY ->
                if (b < 0.5f) 2f * b * texture else 1f - 2f * (1f - b) * (1f - texture)

            // Pegtop's soft light: continuous, and cheaper than the W3C piecewise form.
            BlendMode.SOFT_LIGHT ->
                (1f - 2f * texture) * b * b + 2f * texture * b
        }
        val mixed = b + (blended.coerceIn(0f, 1f) - b) * amount
        return (mixed * 255f).toInt().coerceIn(0, 255)
    }

    private fun frac(value: Float): Float = value - floor(value)

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
    }
}
