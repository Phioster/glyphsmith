package org.phioster.glyphsmith.render

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Which way the picture runs at every cell: along the edge of a finger, round an eye, with the
 * folds of cloth. Flow ASCII sets its characters along these directions instead of on a grid.
 *
 * Read off the structure tensor — the gradients' products, averaged over a neighbourhood — which
 * gives a direction without an arrowhead and says how sure it is. Free of Android types.
 */
class FlowField(
    val cols: Int,
    val rows: Int,
    /** Radians, along the forms. Where the picture has no direction it lies flat, like a line of text. */
    val angle: FloatArray,
    /** 0..1 — how clearly the picture has a direction here. */
    val sure: FloatArray,
    /** 0..1 — the strength of the edge at this cell. */
    val edge: FloatArray,
    /** 0..1 — how steeply the shading changes around here: low faces the viewer, high turns away. */
    val tilt: FloatArray,
) {
    fun index(x: Float, y: Float): Int =
        min(rows - 1, max(0, y.toInt())) * cols + min(cols - 1, max(0, x.toInt()))

    companion object {
        private const val PRE_BLUR = 1.5f
        private const val SMOOTH_SHARE = 1f / 120f
        private const val MIN_SMOOTH = 2f
        private const val TOP_SHARE = 0.99f
        private const val WIDE_SHARE = 1f / 14f

        fun of(luma: FloatArray, cols: Int, rows: Int): FlowField {
            val n = cols * rows
            val soft = blur(luma, cols, rows, PRE_BLUR)
            val gx = FloatArray(n)
            val gy = FloatArray(n)
            for (y in 0 until rows) {
                val up = max(0, y - 1) * cols
                val mid = y * cols
                val down = min(rows - 1, y + 1) * cols
                for (x in 0 until cols) {
                    val l = max(0, x - 1)
                    val r = min(cols - 1, x + 1)
                    // Sobel.
                    gx[mid + x] = soft[up + r] - soft[up + l] + 2f * (soft[mid + r] - soft[mid + l]) + soft[down + r] - soft[down + l]
                    gy[mid + x] = soft[down + l] - soft[up + l] + 2f * (soft[down + x] - soft[up + x]) + soft[down + r] - soft[up + r]
                }
            }
            // The neighbourhood a direction is averaged over grows with the picture.
            val smooth = max(MIN_SMOOTH, min(cols, rows) * SMOOTH_SHARE)
            val jxx = blur(FloatArray(n) { gx[it] * gx[it] }, cols, rows, smooth)
            val jxy = blur(FloatArray(n) { gx[it] * gy[it] }, cols, rows, smooth)
            val jyy = blur(FloatArray(n) { gy[it] * gy[it] }, cols, rows, smooth)

            val edge = FloatArray(n) { sqrt(gx[it] * gx[it] + gy[it] * gy[it]) }
            scaleToTop(edge, 1f)
            // The running sums of the blur can leave a flat area a hair below zero, and the root
            // of that is not a number — which then travels into every angle downstream.
            val energy = FloatArray(n) { sqrt(max(0f, jxx[it] + jyy[it])) }
            scaleToTop(energy, TOP_SHARE)

            val sure = FloatArray(n) { i ->
                val spread = sqrt((jxx[i] - jyy[i]) * (jxx[i] - jyy[i]) + 4f * jxy[i] * jxy[i])
                val coherence = spread / (max(0f, jxx[i] + jyy[i]) + 1e-9f)
                ((coherence - 0.25f) / 0.35f).coerceIn(0f, 1f) * ((energy[i] - 0.06f) / 0.14f).coerceIn(0f, 1f)
            }
            val calm = blur(sure, cols, rows, smooth * 0.66f)

            // A second, much wider look. Inside a plain form — a black cat, a bare wall — the
            // fine look finds nothing, and rows that then lie flat read as ruled lines. The wide
            // look still feels the form's outline from far inside it, so the rows bend with the
            // body instead.
            val wide = max(smooth * 2f, min(cols, rows) * WIDE_SHARE)
            val wxx = blur(jxx, cols, rows, wide)
            val wxy = blur(jxy, cols, rows, wide)
            val wyy = blur(jyy, cols, rows, wide)
            val wideEnergy = FloatArray(n) { sqrt(max(0f, wxx[it] + wyy[it])) }
            scaleToTop(wideEnergy, TOP_SHARE)
            val angle = FloatArray(n) { i ->
                // Along the edge, not across it — hence the quarter turn, which doubled is a half.
                val along = atan2(2f * jxy[i], jxx[i] - jyy[i]) + PI
                val wideAlong = atan2(2f * wxy[i], wxx[i] - wyy[i]) + PI
                val spread = sqrt((wxx[i] - wyy[i]) * (wxx[i] - wyy[i]) + 4f * wxy[i] * wxy[i])
                val wideSure = ((spread / (max(0f, wxx[i] + wyy[i]) + 1e-9f) - 0.08f) / 0.25f).coerceIn(0f, 1f) *
                    ((wideEnergy[i] - 0.01f) / 0.05f).coerceIn(0f, 1f)
                // Mixed as doubled angles: directions without an arrowhead cannot be averaged
                // any other way. Fine first, wide where fine is unsure, flat where both are.
                val fine = calm[i]
                val far = (1f - fine) * wideSure
                val c = fine * cos(along) + far * cos(wideAlong) + (1f - fine - far)
                val s = fine * sin(along) + far * sin(wideAlong)
                0.5f * atan2(s, c)
            }
            val tilt = blur(edge, cols, rows, smooth * 0.5f)
            scaleToTop(tilt, TOP_SHARE)
            return FlowField(cols, rows, angle, calm, edge, tilt)
        }

        private const val PI = 3.1415927f

        /** Divides [values] by the value [share] of the way up their sorted order, capped at 1. */
        private fun scaleToTop(values: FloatArray, share: Float) {
            val sorted = values.copyOf().also { it.sort() }
            val top = sorted[((sorted.size - 1) * share).toInt()].takeIf { it > 0f } ?: return
            for (i in values.indices) values[i] = min(1f, values[i] / top)
        }

        /** A Gaussian blur as three running averages, which costs the same at any radius. */
        internal fun blur(source: FloatArray, cols: Int, rows: Int, sigma: Float): FloatArray {
            // The box width whose three passes have the variance of the Gaussian.
            val radius = max(1, (sqrt(sigma * sigma * 4f + 1f) / 2f).toInt())
            var a = source.copyOf()
            var b = FloatArray(source.size)
            repeat(3) {
                boxRows(a, b, cols, rows, radius)
                boxColumns(b, a, cols, rows, radius)
            }
            return a
        }

        private fun boxRows(from: FloatArray, to: FloatArray, cols: Int, rows: Int, radius: Int) {
            val span = radius * 2 + 1
            for (y in 0 until rows) {
                val row = y * cols
                var sum = 0f
                for (k in -radius..radius) sum += from[row + k.coerceIn(0, cols - 1)]
                for (x in 0 until cols) {
                    to[row + x] = sum / span
                    sum += from[row + min(cols - 1, x + radius + 1)] - from[row + max(0, x - radius)]
                }
            }
        }

        private fun boxColumns(from: FloatArray, to: FloatArray, cols: Int, rows: Int, radius: Int) {
            val span = radius * 2 + 1
            for (x in 0 until cols) {
                var sum = 0f
                for (k in -radius..radius) sum += from[k.coerceIn(0, rows - 1) * cols + x]
                for (y in 0 until rows) {
                    to[y * cols + x] = sum / span
                    sum += from[min(rows - 1, y + radius + 1) * cols + x] - from[max(0, y - radius) * cols + x]
                }
            }
        }
    }
}
