package org.phioster.glyphsmith

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phioster.glyphsmith.ascii.AsciiArt
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.anim.Animator
import org.phioster.glyphsmith.anim.ColorQuantizer
import org.phioster.glyphsmith.anim.GifEncoder
import org.phioster.glyphsmith.anim.Mp4Encoder
import org.phioster.glyphsmith.ascii.AsciiRenderer
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.ascii.Palettes
import org.phioster.glyphsmith.ascii.Pipeline
import org.phioster.glyphsmith.ascii.FontChoice
import org.phioster.glyphsmith.ascii.GlyphCoverage
import org.phioster.glyphsmith.data.ImageLoader
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PresetStore
import org.phioster.glyphsmith.data.Settings
import org.phioster.glyphsmith.data.Source
import org.phioster.glyphsmith.data.StillSource
import org.phioster.glyphsmith.data.VideoSource
import org.phioster.glyphsmith.export.Exporter
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.export.SvgExporter
import org.phioster.glyphsmith.export.SvgMode
import org.phioster.glyphsmith.ui.theme.Term
import org.phioster.glyphsmith.ui.theme.TermThemes

data class UiState(
    val params: AsciiParams = AsciiParams(),
    val preview: Bitmap? = null,
    val hasImage: Boolean = false,
    /** The source is a video, so the preview can be scrubbed and playback has real frames. */
    val isVideo: Boolean = false,
    /** Where in a video the preview sits, 0..1. Meaningless for a still. */
    val previewPosition: Float = 0f,
    val cols: Int = 0,
    val rows: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val working: Boolean = false,
    val exportFormat: ImageFormat = ImageFormat.PNG,
    /** Which face the current ramp actually got, and what it can't draw. */
    val animPlaying: Boolean = false,
    val animFrames: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val fontLabel: String = "",
    val missingGlyphs: String = "",
    /** Measured ink coverage per glyph of the base ramp, for the ramp editor. */
    val rampCoverage: List<Float> = emptyList(),
    val presets: List<Preset> = emptyList(),
    val themeId: String = "matrix",
    val status: String = "no image loaded",
)

class GlyphsmithViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication<Application>()

    private val presetStore = PresetStore(app)
    private val settings = Settings(app)

    private val _state = MutableStateFlow(
        UiState(presets = presetStore.load(), themeId = settings.themeId),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val paramsFlow = MutableStateFlow(AsciiParams())

    // History is captured at the debounce point rather than in updateParams(): one slider
    // drag emits dozens of values but only ever settles once, so this turns a drag into a
    // single undo step without any gesture tracking.
    private val undoStack = ArrayDeque<AsciiParams>()
    private val redoStack = ArrayDeque<AsciiParams>()
    private var lastCommitted = AsciiParams()
    private var suppressHistory = false

    private var source: Source? = null
    private var art: AsciiArt? = null

    init {
        // Applied before the first frame so the app never flashes the default theme on top
        // of the one the user actually chose.
        Term.palette = TermThemes.byId(settings.themeId)
        viewModelScope.launch {
            paramsFlow.collectLatest { params ->
                // Coalesce slider spam: collectLatest cancels the previous pass, so a drag
                // only ever renders the value the finger came to rest on.
                delay(DEBOUNCE_MS)
                if (!suppressHistory && params != lastCommitted) {
                    undoStack.addLast(lastCommitted)
                    if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
                    redoStack.clear()
                }
                suppressHistory = false
                lastCommitted = params
                publishHistory()
                rebuild(params)
            }
        }
    }

    /**
     * Switches the interface theme and remembers it.
     *
     * [Term] is Compose state, so assigning it repaints everything reading it; the id is
     * mirrored into [UiState] purely so the picker can show which one is selected.
     */
    fun setTheme(id: String) {
        settings.themeId = id
        Term.palette = TermThemes.byId(id)
        _state.value = _state.value.copy(themeId = id)
    }

    fun updateParams(params: AsciiParams) {
        _state.value = _state.value.copy(params = params)
        paramsFlow.value = params
    }

    /**
     * Builds a palette out of the loaded image.
     *
     * The median-cut quantiser already exists for the GIF export, so the colours come from
     * exactly the same code that picks a GIF's 256 — no second, subtly different notion of
     * "the important colours in this image". The result is sorted darkest-first because
     * [Palettes.sample] maps luminance onto list position.
     */
    fun extractPalette(count: Int) = runExport("palette") {
        val pixels = currentPixels() ?: return@runExport "no image"
        val colors = withContext(Dispatchers.Default) {
            Palettes.fromColors(ColorQuantizer.palette(listOf(pixels), count).toList())
        }
        if (colors.isEmpty()) return@runExport "no colours found"
        updateParams(
            _state.value.params.copy(
                colorMode = ColorMode.PALETTE,
                paletteOverride = colors,
                paletteLocks = List(colors.size) { false },
            ),
        )
        "extracted ${colors.size} colours"
    }

    /**
     * Reorders the ramp by measured ink coverage.
     *
     * The sets ship in a hand-chosen order, which is a guess; anything typed into Inject
     * Characters is not even that, since it lands at the dense end unmeasured. Measuring
     * uses the face the ramp will actually be drawn with, so the answer is right for this
     * ramp rather than right in general.
     */
    fun autoOrderRamp() = runExport("ramp") {
        val params = _state.value.params
        val face = AsciiRenderer.faceFor(params, params.effectiveRamp().ifEmpty { " " })
        val sorted = withContext(Dispatchers.Default) {
            GlyphCoverage.sort(params.baseGlyphs() + params.injection, face.typeface)
        }
        if (sorted.isEmpty()) return@runExport "nothing to order"
        // The injected characters are now placed by measurement, so they must not also be
        // appended a second time by effectiveRamp().
        updateParams(params.copy(rampOverride = sorted, injection = ""))
        "ordered ${sorted.length} glyphs by coverage"
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(lastCommitted)
        restore(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(lastCommitted)
        restore(next)
    }

    private fun restore(params: AsciiParams) {
        suppressHistory = true
        lastCommitted = params
        _state.value = _state.value.copy(params = params)
        paramsFlow.value = params
        publishHistory()
    }

    private fun publishHistory() {
        _state.value = _state.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }

    fun exportPresets() = runExport("presets") {
        val json = presetStore.exportJson()
        val uri = withContext(Dispatchers.IO) {
            Exporter.saveJson(context, json, "glyphsmith-presets.json")
        }
        if (uri != null) "presets saved to Download/Glyphsmith" else "export failed"
    }

    fun importPresets(uri: Uri) = runExport("import") {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        } ?: return@runExport "could not read that file"
        val merged = presetStore.importJson(text)
            ?: return@runExport "that file isn't a preset export"
        _state.value = _state.value.copy(presets = merged)
        "${merged.size} presets after import"
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, status = "decoding…")
            val loaded = withContext(Dispatchers.IO) { ImageLoader.load(context, uri) }
            if (loaded == null) {
                _state.value = _state.value.copy(working = false, status = "could not decode that image")
                return@launch
            }
            val pixels = withContext(Dispatchers.Default) { ImageLoader.pixelsOf(loaded) }
            // Read the size before recycling — a recycled bitmap's dimensions are not
            // something to rely on.
            val width = loaded.width
            val height = loaded.height
            loaded.recycle()
            adopt(StillSource(pixels, width, height))
            _state.value = _state.value.copy(
                hasImage = true,
                isVideo = false,
                previewPosition = 0f,
                status = "loaded ${width}×$height",
            )
            rebuild(_state.value.params)
        }
    }

    /**
     * Loads a video as the source. Frames are decoded on demand rather than held, so a long
     * clip costs the same memory as a still — see [VideoSource].
     */
    fun loadVideo(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, status = "opening video…")
            val opened = withContext(Dispatchers.IO) { VideoSource.open(context, uri) }
            if (opened == null) {
                _state.value = _state.value.copy(
                    working = false,
                    status = "could not read that video",
                )
                return@launch
            }
            adopt(opened)
            _state.value = _state.value.copy(
                hasImage = true,
                isVideo = true,
                previewPosition = 0f,
                status = "video ${opened.width}×${opened.height}",
            )
            rebuild(_state.value.params)
        }
    }

    /** Scrubs the preview through a video. Ignored for a still, which has one frame. */
    fun setPreviewPosition(position: Float) {
        if (source?.isMoving != true) return
        _state.value = _state.value.copy(previewPosition = position.coerceIn(0f, 1f))
        viewModelScope.launch { rebuild(_state.value.params) }
    }

    /** Swaps in a new source and releases whatever the old one was holding. */
    private fun adopt(next: Source) {
        source?.close()
        source = next
    }

    private fun currentPixels(): IntArray? =
        source?.pixelsAt(_state.value.previewPosition)

    override fun onCleared() {
        source?.close()
        source = null
        super.onCleared()
    }

    private suspend fun rebuild(params: AsciiParams) {
        val current = source ?: return
        val pixels = current.pixelsAt(_state.value.previewPosition)
        _state.value = _state.value.copy(working = true)
        val result = withContext(Dispatchers.Default) {
            Pipeline.run(pixels, current.width, current.height, params, PREVIEW_MAX_SIDE)
        }
        art = result.art
        // Measured once per rebuild and cached inside GlyphCoverage, so a slider drag pays
        // for the rasterisation only the first time a glyph is seen in this face.
        val coverage = withContext(Dispatchers.Default) {
            GlyphCoverage.profile(params.baseGlyphs(), result.face.typeface).map { it.second }
        }
        // The old preview is deliberately not recycled: Compose may still be drawing it
        // this frame, and a recycled bitmap under the canvas is an instant crash.
        _state.value = _state.value.copy(
            preview = result.bitmap,
            cols = result.art.cols,
            rows = result.art.rows,
            outputWidth = result.outputWidth,
            outputHeight = result.outputHeight,
            fontLabel = result.face.label,
            missingGlyphs = result.face.missing,
            rampCoverage = coverage,
            working = false,
            status = "${result.art.cols}×${result.art.rows} cells",
        )
    }

    private suspend fun renderFullSize(): Bitmap? {
        val current = source ?: return null
        val pixels = current.pixelsAt(_state.value.previewPosition)
        val params = _state.value.params
        return withContext(Dispatchers.Default) {
            Pipeline.run(pixels, current.width, current.height, params, AsciiRenderer.MAX_OUTPUT_SIDE).bitmap
        }
    }

    fun setExportFormat(format: ImageFormat) {
        _state.value = _state.value.copy(exportFormat = format)
    }

    fun exportImage() = runExport("image") {
        val format = _state.value.exportFormat
        val bitmap = renderFullSize() ?: return@runExport "nothing to export"
        val uri = withContext(Dispatchers.IO) { Exporter.saveImage(context, bitmap, format) }
        bitmap.recycle()
        if (uri != null) "saved ${format.extension} to Pictures/Glyphsmith" else "save failed"
    }

    fun exportText() = runExport("txt") {
        val text = art?.toText() ?: return@runExport "nothing to export"
        val uri = withContext(Dispatchers.IO) { Exporter.saveText(context, text) }
        if (uri != null) "saved to Download/Glyphsmith" else "save failed"
    }

    /**
     * The grid as vectors. [AsciiRenderer.layout] always measures against the full output
     * size, so this is the export resolution even though `art` came from the preview pass —
     * the grid itself doesn't change with preview scale, only the glyph size does.
     */
    fun exportSvg(mode: SvgMode) = runExport("svg") {
        val grid = art ?: return@runExport "nothing to export"
        val params = _state.value.params
        val svg = withContext(Dispatchers.Default) {
            SvgExporter.build(grid, params, params.fontSizePx, mode)
        }
        val uri = withContext(Dispatchers.IO) { Exporter.saveSvg(context, svg) }
        if (uri != null) {
            "saved ${mode.label} svg (${svg.length / 1024} KB) to Download/Glyphsmith"
        } else {
            "save failed"
        }
    }

    fun copyText() = runExport("copy") {
        val text = art?.toText() ?: return@runExport "nothing to copy"
        Exporter.copyToClipboard(context, text)
        "${text.length} chars copied"
    }

    fun shareImage() = runExport("share") {
        val bitmap = renderFullSize() ?: return@runExport "nothing to share"
        withContext(Dispatchers.IO) { Exporter.shareImage(context, bitmap, _state.value.exportFormat) }
        bitmap.recycle()
        "shared"
    }

    fun shareText() = runExport("share") {
        val text = art?.toText() ?: return@runExport "nothing to share"
        withContext(Dispatchers.IO) { Exporter.shareText(context, text) }
        "shared"
    }

    private fun runExport(label: String, block: suspend () -> String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, status = "$label…")
            val message = runCatching { block() }.getOrElse { "$label failed: ${it.message}" }
            _state.value = _state.value.copy(working = false, status = message)
        }
    }

    // --- animation ---------------------------------------------------------------

    private var animFrames: List<Bitmap> = emptyList()
    private var playbackJob: Job? = null

    /**
     * Renders every frame once and then loops the cached bitmaps. Re-rendering per tick
     * would never hold the frame rate — a single frame is the whole pipeline.
     */
    fun playAnimation() {
        val current = source ?: return
        viewModelScope.launch {
            cancelPlayback()
            val params = _state.value.params
            val animation = params.animation
            _state.value = _state.value.copy(working = true, status = "rendering ${animation.frames} frames…")

            val frames = withContext(Dispatchers.Default) {
                renderFrames(current, params, animation, ANIM_PREVIEW_MAX_SIDE)
            }
            animFrames = frames
            _state.value = _state.value.copy(
                working = false,
                animPlaying = true,
                animFrames = frames.size,
                status = "${frames.size} frames · ${"%.1f".format(animation.durationSeconds)}s loop",
            )

            val frameDelay = 1000L / animation.fps.coerceAtLeast(1)
            playbackJob = viewModelScope.launch {
                var index = 0
                while (isActive && animFrames.isNotEmpty()) {
                    _state.value = _state.value.copy(preview = animFrames[index % animFrames.size])
                    index++
                    delay(frameDelay)
                }
            }
        }
    }

    fun stopAnimation() {
        viewModelScope.launch {
            cancelPlayback()
            _state.value = _state.value.copy(status = "stopped")
            rebuild(_state.value.params)
        }
    }

    private fun cancelPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        animFrames = emptyList()
        _state.value = _state.value.copy(animPlaying = false, animFrames = 0)
    }

    /**
     * Frames are forced to a common size: animating Depth changes the ramp length, which
     * changes the glyph cell, which would otherwise make each frame a slightly different
     * bitmap — and neither GIF nor MP4 accepts that.
     */
    private fun renderFrames(
        source: Source,
        base: AsciiParams,
        animation: AnimationParams,
        maxSide: Int,
    ): List<Bitmap> {
        val budgetSide = frameBudget(animation.frames, maxSide)
        var width = 0
        var height = 0
        return (0 until animation.frames).map { frame ->
            val position = frame.toFloat() / animation.frames
            // The clock is set here rather than in paramsAt, so temporal noise still moves
            // over a video whose parameter tracks are all switched off.
            val frameParams = Animator.paramsAt(base, animation, frame)
                .let { it.copy(temporal = it.temporal.copy(time = position)) }
            // A still hands back the same buffer every time; a video decodes this position.
            val pixels = source.pixelsAt(position)
            val rendered = Pipeline.run(pixels, source.width, source.height, frameParams, budgetSide).bitmap
            if (frame == 0) {
                width = rendered.width
                height = rendered.height
                rendered
            } else if (rendered.width != width || rendered.height != height) {
                val scaled = Bitmap.createScaledBitmap(rendered, width, height, true)
                if (scaled != rendered) rendered.recycle()
                scaled
            } else {
                rendered
            }
        }
    }

    /** Keeps the whole frame set inside a fixed memory budget. */
    private fun frameBudget(frames: Int, maxSide: Int): Int {
        val perFrame = MEMORY_BUDGET_BYTES / frames.coerceAtLeast(1) / 4
        val side = kotlin.math.sqrt(perFrame.toDouble()).toInt()
        return side.coerceIn(120, maxSide)
    }

    fun exportGif() = runExport("gif") {
        val current = source ?: return@runExport "no image"
        val params = _state.value.params
        val animation = params.animation
        val bytes = withContext(Dispatchers.Default) {
            val frames = renderFrames(current, params, animation, ANIM_EXPORT_MAX_SIDE)
            val width = frames.first().width
            val height = frames.first().height
            val buffers = frames.map { bitmap ->
                IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
            }
            frames.forEach { it.recycle() }
            java.io.ByteArrayOutputStream().use { out ->
                GifEncoder.encode(buffers, width, height, 100 / animation.fps.coerceAtLeast(1), out)
                out.toByteArray()
            }
        }
        val uri = withContext(Dispatchers.IO) {
            Exporter.saveBytes(context, bytes, Exporter.timestampedName("gif"), "image/gif")
        }
        if (uri != null) "gif saved to Download/Glyphsmith" else "save failed"
    }

    fun exportMp4() = runExport("mp4") {
        val current = source ?: return@runExport "no image"
        val params = _state.value.params
        val animation = params.animation
        val file = java.io.File(context.cacheDir, "glyphsmith-anim.mp4")
        val failure = withContext(Dispatchers.Default) {
            val frames = renderFrames(current, params, animation, ANIM_EXPORT_MAX_SIDE)
            val result = Mp4Encoder.encode(frames, animation.fps.coerceAtLeast(1), file)
            frames.forEach { it.recycle() }
            result
        }
        if (failure != null) return@runExport "mp4: $failure"
        val uri = withContext(Dispatchers.IO) {
            Exporter.saveBytes(context, file.readBytes(), Exporter.timestampedName("mp4"), "video/mp4")
        }
        file.delete()
        if (uri != null) "mp4 saved to Download/Glyphsmith" else "save failed"
    }

    fun savePreset(name: String) {
        val presets = presetStore.upsert(name, _state.value.params)
        _state.value = _state.value.copy(presets = presets, status = "preset saved")
    }

    fun deletePreset(name: String) {
        _state.value = _state.value.copy(presets = presetStore.delete(name), status = "preset deleted")
    }

    fun applyPreset(preset: Preset) {
        updateParams(preset.params)
        _state.value = _state.value.copy(status = "preset ${preset.name}")
    }

    private class Rebuilt(
        val grid: AsciiArt,
        val preview: Bitmap,
        val outputWidth: Int,
        val outputHeight: Int,
        val face: FontChoice,
    )

    private companion object {
        const val DEBOUNCE_MS = 90L
        const val MAX_HISTORY = 50
        const val PREVIEW_MAX_SIDE = 1600
        const val ANIM_PREVIEW_MAX_SIDE = 640
        const val ANIM_EXPORT_MAX_SIDE = 1080
        const val MEMORY_BUDGET_BYTES = 96L * 1024 * 1024
        const val REFERENCE_FONT_SIZE = 32
    }
}
