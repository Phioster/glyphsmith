package org.phioster.glyphsmith.data

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Size
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One camera frame, already in the packed ARGB the rest of the app speaks. */
class LiveFrame(val pixels: IntArray, val width: Int, val height: Int)

/**
 * The camera, as a stream of frames the ASCII pipeline can render.
 *
 * Three decisions carry this, and each is the difference between a live preview and a
 * slideshow that lags reality:
 *
 * **RGBA_8888 is requested from the analysis itself.** CameraX can hand frames over already
 * converted; the usual route delivers `YUV_420_888` and leaves the colour conversion to the
 * caller, which is a per-pixel loop on every frame — exactly the cost a preview cannot pay.
 *
 * **Frames are dropped while one is being rendered.** `STRATEGY_KEEP_ONLY_LATEST` alone is
 * not enough: it keeps the queue at one, but that one still waits for the renderer, so the
 * picture drifts steadily behind the camera. The gate makes a frame arriving mid-render
 * disappear instead of queueing, so what is shown is always recent rather than complete.
 *
 * **Resolution is capped low.** A still can afford 1600 px; a live frame has about 60 ms
 * for the whole pipeline. The cap is on the *camera*, so nothing large is ever allocated.
 */
class LiveCamera(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null

    /**
     * Starts delivering frames to [onFrame], which is called on a background thread.
     *
     * [onFrame] must be synchronous — it returning is what re-opens the gate. Handing the
     * work to another thread and returning immediately would defeat the whole arrangement.
     */
    fun start(owner: LifecycleOwner, front: Boolean, onFrame: (LiveFrame) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            provider = cameraProvider

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(MAX_SIDE, MAX_SIDE),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            ),
                        )
                        .build(),
                )
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                if (!busy.compareAndSet(false, true)) {
                    // Still rendering the previous one. Letting this through would queue
                    // work faster than it can be done, and the preview would fall behind
                    // and stay behind.
                    proxy.close()
                    return@setAnalyzer
                }
                try {
                    onFrame(proxy.toLiveFrame())
                } finally {
                    proxy.close()
                    busy.set(false)
                }
            }

            val selector = if (front) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(owner, selector, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        runCatching { provider?.unbindAll() }
        provider = null
        busy.set(false)
    }

    fun release() {
        stop()
        executor.shutdown()
    }

    companion object {
        /** The camera is asked for this, so nothing larger is ever allocated or converted. */
        const val MAX_SIDE = 480
    }
}

/**
 * Copies an RGBA_8888 frame into a packed ARGB array, rotated upright.
 *
 * The row stride is honoured rather than assumed: CameraX pads rows to a hardware-friendly
 * width, so a frame read as `width * height` contiguous pixels comes out sheared — the
 * classic symptom being an image that leans further with every row.
 */
private fun ImageProxy.toLiveFrame(): LiveFrame {
    val plane = planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    val src = IntArray(width * height)
    val row = ByteArray(rowStride)
    var index = 0
    for (y in 0 until height) {
        buffer.position(y * rowStride)
        val length = minOf(rowStride, buffer.remaining())
        buffer.get(row, 0, length)
        for (x in 0 until width) {
            val o = x * pixelStride
            val r = row[o].toInt() and 0xFF
            val g = row[o + 1].toInt() and 0xFF
            val b = row[o + 2].toInt() and 0xFF
            val a = row[o + 3].toInt() and 0xFF
            src[index++] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    return rotate(src, width, height, imageInfo.rotationDegrees)
}

/**
 * Turns the frame upright.
 *
 * The sensor is mounted at a fixed angle that has nothing to do with how the phone is being
 * held, so a frame taken in portrait arrives on its side. Rotating here rather than at draw
 * time means the grid is measured against the picture the user is actually looking at.
 */
private fun rotate(src: IntArray, width: Int, height: Int, degrees: Int): LiveFrame = when (
    Math.floorMod(degrees, 360)
) {
    90 -> LiveFrame(
        IntArray(src.size) { i ->
            val x = i % height
            val y = i / height
            src[(height - 1 - x) * width + y]
        },
        height,
        width,
    )

    180 -> LiveFrame(IntArray(src.size) { src[src.size - 1 - it] }, width, height)

    270 -> LiveFrame(
        IntArray(src.size) { i ->
            val x = i % height
            val y = i / height
            src[x * width + (width - 1 - y)]
        },
        height,
        width,
    )

    else -> LiveFrame(src, width, height)
}
