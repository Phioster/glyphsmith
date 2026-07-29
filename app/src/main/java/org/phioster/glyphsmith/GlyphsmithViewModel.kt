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
import org.phioster.glyphsmith.anim.QuantizeMethod
import org.phioster.glyphsmith.anim.GifEncoder
import org.phioster.glyphsmith.anim.Mp4Encoder
import org.phioster.glyphsmith.ascii.AsciiRenderer
import org.phioster.glyphsmith.ascii.CharacterSets
import org.phioster.glyphsmith.ascii.ColorMode
import org.phioster.glyphsmith.ascii.DitherMode
import org.phioster.glyphsmith.ascii.Palettes
import org.phioster.glyphsmith.ascii.Pipeline
import org.phioster.glyphsmith.ascii.GlyphCoverage
import org.phioster.glyphsmith.data.CameraCapture
import org.phioster.glyphsmith.data.ImageLoader
import org.phioster.glyphsmith.data.LiveCamera
import org.phioster.glyphsmith.data.LiveFrame
import org.phioster.glyphsmith.data.PaletteFile
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PresetStore
import org.phioster.glyphsmith.data.PlaybackQuality
import org.phioster.glyphsmith.data.PreviewQuality
import org.phioster.glyphsmith.data.Settings
import org.phioster.glyphsmith.data.Source
import org.phioster.glyphsmith.data.StillSource
import org.phioster.glyphsmith.data.VideoSource
import org.phioster.glyphsmith.effects.BlurSharpenParams
import org.phioster.glyphsmith.effects.ChromaticParams
import org.phioster.glyphsmith.effects.CmykHalftoneParams
import org.phioster.glyphsmith.effects.DiffractionStarsParams
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.effects.GlowParams
import org.phioster.glyphsmith.effects.InterlaceParams
import org.phioster.glyphsmith.effects.JpegGlitchParams
import org.phioster.glyphsmith.effects.PixelSortParams
import org.phioster.glyphsmith.effects.PostProcessingParams
import org.phioster.glyphsmith.effects.SliceShiftParams
import org.phioster.glyphsmith.effects.SubtextureParams
import org.phioster.glyphsmith.effects.TextureKind
import org.phioster.glyphsmith.effects.TintParams
import org.phioster.glyphsmith.export.Exporter
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.export.SvgExporter
import org.phioster.glyphsmith.export.SvgMode
import org.phioster.glyphsmith.export.TextExporters
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
    /** One rendered thumbnail per preset name, built from the current source. */
    val presetThumbs: Map<String, Bitmap> = emptyMap(),
    val themeId: String = "matrix",
    val previewQuality: PreviewQuality = PreviewQuality.FULL,
    /** Playback repeats rather than stopping at the last frame. */
    val looped: Boolean = true,
    val playbackQuality: PlaybackQuality = PlaybackQuality.RENDERED,
    val favouritePalettes: Set<String> = emptySet(),
    val favouriteStyles: Set<String> = emptySet(),
    /** The live camera is running and the preview is showing what it sees. */
    val liveCamera: Boolean = false,
    val frontCamera: Boolean = false,
    /** How far a batch run has got, as done/total. Zero total means nothing is running. */
    val batchDone: Int = 0,
    val batchTotal: Int = 0,
    val status: String = "no image loaded",
)

class GlyphsmithViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication<Application>()

    private val presetStore = PresetStore(app)
    private val settings = Settings(app)

    private val _state = MutableStateFlow(
        UiState(
            presets = presetStore.load(),
            themeId = settings.themeId,
            previewQuality = settings.previewQuality,
            looped = settings.looped,
            playbackQuality = settings.playbackQuality,
            favouritePalettes = settings.favouritePalettes,
            favouriteStyles = settings.favouriteStyles,
        ),
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

    /**
     * True between a slider being grabbed and released.
     *
     * Not in [UiState]: nothing in the interface renders differently while a slider is held, and
     * putting it there would recompose every panel twice per drag for no visible change.
     */
    private var scrubbing = false

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

    /**
     * Called when any slider is grabbed or released.
     *
     * While held, [rebuild] renders at [SCRUB_MAX_SIDE] instead of the preview budget, which is
     * what makes a heavy chain track the finger. The release triggers one more pass at full
     * preview quality — without it the user would be left looking at the coarse render and
     * would reasonably conclude the app had got worse.
     */
    fun setScrubbing(active: Boolean) {
        if (scrubbing == active) return
        scrubbing = active
        if (!active) viewModelScope.launch { rebuild(paramsFlow.value) }
    }

    /** Halving the preview resolution is the one lever that makes a heavy chain feel live. */
    fun setPreviewQuality(quality: PreviewQuality) {
        settings.previewQuality = quality
        _state.value = _state.value.copy(previewQuality = quality)
        viewModelScope.launch { rebuild(_state.value.params) }
    }

    fun setPlaybackQuality(quality: PlaybackQuality) {
        settings.playbackQuality = quality
        _state.value = _state.value.copy(playbackQuality = quality)
    }

    fun toggleFavouritePalette(id: String) {
        val next = settings.favouritePalettes.toMutableSet()
        if (!next.remove(id)) next.add(id)
        settings.favouritePalettes = next
        _state.value = _state.value.copy(favouritePalettes = next)
    }

    fun toggleFavouriteStyle(name: String) {
        val next = settings.favouriteStyles.toMutableSet()
        if (!next.remove(name)) next.add(name)
        settings.favouriteStyles = next
        _state.value = _state.value.copy(favouriteStyles = next)
    }

    fun setLooped(looped: Boolean) {
        settings.looped = looped
        _state.value = _state.value.copy(looped = looped)
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
    fun extractPalette(count: Int, method: QuantizeMethod = QuantizeMethod.MEDIAN_CUT) =
        runExport("palette") {
        val pixels = currentPixels() ?: return@runExport "no image"
        val colors = withContext(Dispatchers.Default) {
            Palettes.fromColors(ColorQuantizer.extract(pixels, count, method).toList())
        }
        if (colors.isEmpty()) return@runExport "no colours found"
        updateParams(
            _state.value.params.copy(
                colorMode = ColorMode.PALETTE,
                paletteOverride = colors,
                paletteLocks = List(colors.size) { false },
            ),
        )
        "extracted ${colors.size} colours by ${method.label}"
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

    /** Writes the palette in use to its own file, so colours can be shared without a look. */
    fun exportPalette() = runExport("palette") {
        val palette = _state.value.params.activePalette()
        val text = PaletteFile.encode(palette)
        val name = Exporter.timestampedName("json")
            .replace("glyphsmith-", "glyphsmith-palette-")
        val uri = withContext(Dispatchers.IO) { Exporter.saveJson(context, text, name) }
        if (uri != null) {
            "palette saved to Download/Glyphsmith"
        } else {
            "save failed"
        }
    }

    fun importPalette(uri: Uri) = runExport("palette") {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        } ?: return@runExport "could not read that file"

        val file = PaletteFile.decode(text) ?: return@runExport "that file isn't a palette"
        val colors = PaletteFile.colorsOf(file)
        if (colors.isEmpty()) return@runExport "no usable colours in that file"

        updateParams(
            _state.value.params.copy(
                colorMode = ColorMode.PALETTE,
                paletteOverride = colors,
                paletteLocks = List(colors.size) { false },
            ),
        )
        "loaded ${file.name} · ${colors.size} colours"
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
        renderThumbs()
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
            renderThumbs()
        }
    }

    /**
     * Takes the photo the system camera just wrote and loads it like any other image.
     *
     * Nothing here knows it came from a camera, which is the point: a capture is a source
     * like any other, and everything downstream — presets, effects, export — stays unaware.
     */
    fun loadCapture(uri: Uri) {
        CameraCapture.prune(context)
        loadImage(uri)
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
            renderThumbs()
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
        live?.release()
        live = null
        source?.close()
        source = null
        super.onCleared()
    }

    private suspend fun rebuild(params: AsciiParams) {
        val current = source ?: return
        val pixels = current.pixelsAt(_state.value.previewPosition)
        _state.value = _state.value.copy(working = true)
        val scrub = scrubbing
        val budget = if (scrub) SCRUB_MAX_SIDE else _state.value.previewQuality.maxSide
        val result = withContext(Dispatchers.Default) {
            Pipeline.run(pixels, current.width, current.height, params, budget, isScrubbing = scrub)
        }
        art = result.art
        // Measured once per rebuild and cached inside GlyphCoverage, so a slider drag pays
        // for the rasterisation only the first time a glyph is seen in this face. The pixel
        // mode has no face to profile, so there is nothing to measure and nothing to show.
        val face = result.face
        val coverage = if (face == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                GlyphCoverage.profile(params.baseGlyphs(), face.typeface).map { it.second }
            }
        }
        // The old preview is deliberately not recycled: Compose may still be drawing it
        // this frame, and a recycled bitmap under the canvas is an instant crash.
        _state.value = _state.value.copy(
            preview = result.bitmap,
            cols = result.cols,
            rows = result.rows,
            outputWidth = result.outputWidth,
            outputHeight = result.outputHeight,
            fontLabel = face?.label ?: "",
            missingGlyphs = face?.missing ?: "",
            rampCoverage = coverage,
            working = false,
            status = "${result.cols}×${result.rows} ${if (face == null) "px" else "cells"}",
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

    fun exportHtml() = runExport("html") {
        val grid = art ?: return@runExport "nothing to export"
        val text = TextExporters.html(grid, _state.value.params)
        val uri = withContext(Dispatchers.IO) { Exporter.saveHtml(context, text) }
        if (uri != null) "html saved to Download/Glyphsmith" else "save failed"
    }

    fun exportAnsi() = runExport("ansi") {
        val grid = art ?: return@runExport "nothing to export"
        val text = TextExporters.ansi(grid, _state.value.params)
        val uri = withContext(Dispatchers.IO) { Exporter.saveAnsi(context, text) }
        if (uri != null) "ansi saved to Download/Glyphsmith" else "save failed"
    }

    /**
     * Runs every picked image through the current settings and saves each result.
     *
     * Deliberately sequential. Each pass allocates a full-size bitmap and the effect chain
     * works on its own copies, so running several at once is the quickest way to meet the
     * heap limit — and the phone has nothing to gain from it anyway.
     *
     * The source is put back afterwards. A batch is something you launch *from* a look you
     * have already dialled in, and losing that look as a side effect would be hostile.
     */
    fun runBatch(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val params = _state.value.params
            val restore = source
            val restorePosition = _state.value.previewPosition
            var saved = 0
            var failed = 0

            _state.value = _state.value.copy(
                working = true,
                batchDone = 0,
                batchTotal = uris.size,
                status = "batch 0/${uris.size}",
            )

            uris.forEachIndexed { index, uri ->
                val loaded = withContext(Dispatchers.IO) { ImageLoader.load(context, uri) }
                if (loaded == null) {
                    failed++
                } else {
                    val pixels = withContext(Dispatchers.Default) { ImageLoader.pixelsOf(loaded) }
                    val width = loaded.width
                    val height = loaded.height
                    loaded.recycle()

                    val bitmap = withContext(Dispatchers.Default) {
                        Pipeline.run(pixels, width, height, params, AsciiRenderer.MAX_OUTPUT_SIDE).bitmap
                    }
                    val format = _state.value.exportFormat
                    val name = Exporter.timestampedName(format.extension)
                        .replace("glyphsmith-", "glyphsmith-batch-${index + 1}-")
                    val uriOut = withContext(Dispatchers.IO) {
                        Exporter.saveImage(context, bitmap, format, name)
                    }
                    bitmap.recycle()
                    if (uriOut != null) saved++ else failed++
                }
                _state.value = _state.value.copy(
                    batchDone = index + 1,
                    status = "batch ${index + 1}/${uris.size}",
                )
            }

            source = restore
            _state.value = _state.value.copy(
                working = false,
                batchDone = 0,
                batchTotal = 0,
                previewPosition = restorePosition,
                status = if (failed == 0) {
                    "batch done — $saved saved to Pictures/Glyphsmith"
                } else {
                    "batch done — $saved saved, $failed failed"
                },
            )
            rebuild(_state.value.params)
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

            val quality = _state.value.playbackQuality
            val frames = withContext(Dispatchers.Default) {
                renderFrames(current, params, animation, quality.maxSide, quality.step)
            }
            animFrames = frames
            _state.value = _state.value.copy(
                working = false,
                animPlaying = true,
                animFrames = frames.size,
                status = if (quality.step > 1) {
                    // Said out loud, because a preview that quietly differs from the export
                    // is worse than a slow one.
                    "${frames.size} of ${animation.frames} frames · approximate preview"
                } else {
                    "${frames.size} frames · ${"%.1f".format(animation.durationSeconds)}s loop"
                },
            )

            // Each rendered frame stands in for `step` of them, so it has to be held that
            // much longer or a quick preview would run at several times real speed.
            val frameDelay = 1000L * quality.step / animation.fps.coerceAtLeast(1)
            playbackJob = viewModelScope.launch {
                var index = 0
                val loop = _state.value.looped
                while (isActive && animFrames.isNotEmpty()) {
                    _state.value = _state.value.copy(preview = animFrames[index % animFrames.size])
                    index++
                    // Not looping means stopping on the last frame rather than snapping back,
                    // which is what you want while judging where an animation ends up.
                    if (!loop && index >= animFrames.size) break
                    delay(frameDelay)
                }
                if (!loop) _state.value = _state.value.copy(animPlaying = false)
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
        step: Int = 1,
    ): List<Bitmap> {
        // Only the frames actually rendered count against the budget, so a stepped run gets
        // more room per frame rather than the same room spread over frames it skips.
        val rendered = (animation.frames + step - 1) / step
        val budgetSide = frameBudget(rendered, maxSide)
        var width = 0
        var height = 0
        return (0 until animation.frames step step).map { frame ->
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

    fun savePreset(name: String, description: String = "") {
        val presets = presetStore.upsert(name, _state.value.params, description)
        _state.value = _state.value.copy(presets = presets, status = "preset saved")
        renderThumbs()
    }

    fun renamePreset(from: String, to: String, description: String) {
        _state.value = _state.value.copy(
            presets = presetStore.rename(from, to, description),
            status = "preset renamed",
        )
        renderThumbs()
    }

    fun deletePreset(name: String) {
        _state.value = _state.value.copy(presets = presetStore.delete(name), status = "preset deleted")
        renderThumbs()
    }

    fun toggleFavourite(name: String) {
        _state.value = _state.value.copy(presets = presetStore.toggleFavourite(name))
    }

    /** Puts the shipped library back, discarding anything saved on top of it. */
    fun resetPresets() {
        _state.value = _state.value.copy(
            presets = presetStore.reset(),
            status = "presets reset to the built-in library",
        )
        renderThumbs()
    }

    /**
     * Rolls a look at random.
     *
     * Deliberately narrow: a genuinely uniform roll over every parameter produces an empty
     * or unreadable image far more often than an interesting one, which makes the button
     * useless. Cell size, depth and the effect count are all kept inside the range that
     * reliably yields something worth looking at, and the effects are picked one at a time
     * rather than all rolled independently.
     */
    fun randomise() {
        val random = kotlin.random.Random.Default
        val set = CharacterSets.all.random(random)
        val palette = Palettes.all.random(random)
        val dither = DitherMode.entries.random(random)

        var effects = EffectStack()
        repeat(random.nextInt(0, 3)) {
            effects = when (EffectId.entries.random(random)) {
                EffectId.POST -> effects.copy(
                    postProcessing = PostProcessingParams(
                        enabled = true,
                        grain = random.nextInt(0, 40),
                        vignette = random.nextInt(0, 60),
                        scanlines = random.nextInt(0, 60),
                    ),
                )

                EffectId.BLUR -> effects.copy(
                    blurSharpen = BlurSharpenParams(enabled = true, amount = random.nextInt(-70, 70)),
                )

                EffectId.TINT -> effects.copy(tint = TintParams(enabled = true, color = palette.colors.last()))
                EffectId.CHROMATIC -> effects.copy(
                    chromatic = ChromaticParams(enabled = true, maxDisplace = random.nextInt(2, 18)),
                )

                EffectId.GLITCH -> effects.copy(
                    jpegGlitch = JpegGlitchParams(enabled = true, corruption = random.nextInt(20, 140)),
                )

                EffectId.SORT -> effects.copy(
                    pixelSort = PixelSortParams(
                        enabled = true,
                        thresholdLow = random.nextInt(10, 40),
                        thresholdHigh = random.nextInt(55, 90),
                    ),
                )

                EffectId.SLICE -> effects.copy(
                    sliceShift = SliceShiftParams(enabled = true, maxOffset = random.nextInt(4, 20)),
                )

                EffectId.INTERLACE -> effects.copy(
                    interlace = InterlaceParams(
                        enabled = true,
                        shift = random.nextInt(2, 18),
                        density = random.nextInt(30, 90),
                        tearColor = random.nextInt(0, 60),
                    ),
                )

                EffectId.STARS -> effects.copy(stars = DiffractionStarsParams(enabled = true))
                EffectId.SUBTEXTURE -> effects.copy(
                    subtexture = SubtextureParams(
                        enabled = true,
                        kind = TextureKind.entries.random(random),
                        intensity = random.nextInt(20, 60),
                    ),
                )

                EffectId.CMYK -> effects.copy(
                    cmyk = CmykHalftoneParams(enabled = true, frequency = random.nextInt(4, 14)),
                )

                EffectId.GLOW -> effects.copy(
                    glow = GlowParams(enabled = true, intensity = random.nextInt(200, 600)),
                )
            }
        }

        updateParams(
            _state.value.params.copy(
                charSetId = set.id,
                cellSize = random.nextInt(4, 13),
                depth = random.nextInt(3, 24),
                invert = random.nextBoolean(),
                ditherMode = dither,
                ditherStrength = random.nextInt(50, 101),
                modScale = random.nextInt(4, 16),
                modAngle = random.nextInt(0, 360),
                colorMode = ColorMode.entries.random(random),
                paletteId = palette.id,
                paletteOverride = emptyList(),
                paletteLocks = emptyList(),
                rampOverride = "",
                effects = effects,
            ),
        )
        _state.value = _state.value.copy(status = "rolled ${set.name} · ${dither.label}")
    }

    private var live: LiveCamera? = null
    /** The last frame the camera delivered, kept so it can be frozen into a still. */
    private var lastLive: LiveFrame? = null

    /**
     * Starts the live preview. The caller has already secured the camera permission.
     *
     * Frames are rendered on the delivering thread — [LiveCamera] treats the callback
     * returning as the signal to accept the next one, so handing the work elsewhere would
     * uncap the rate and put the preview permanently behind.
     */
    fun startLive(owner: androidx.lifecycle.LifecycleOwner) {
        if (live != null) return
        val camera = LiveCamera(context)
        live = camera
        _state.value = _state.value.copy(liveCamera = true, status = "live")
        camera.start(owner, _state.value.frontCamera) { frame ->
            lastLive = frame
            val result = runCatching {
                Pipeline.run(frame.pixels, frame.width, frame.height, _state.value.params, LiveCamera.MAX_SIDE)
            }.getOrNull() ?: return@start
            art = result.art
            _state.value = _state.value.copy(
                preview = result.bitmap,
                hasImage = true,
                cols = result.cols,
                rows = result.rows,
                status = "live · ${result.cols}×${result.rows}",
            )
        }
    }

    fun stopLive() {
        live?.release()
        live = null
        _state.value = _state.value.copy(liveCamera = false, status = "live stopped")
    }

    fun flipCamera() {
        val front = !_state.value.frontCamera
        _state.value = _state.value.copy(frontCamera = front)
        val owner = liveOwner ?: return
        stopLive()
        startLive(owner)
    }

    private var liveOwner: androidx.lifecycle.LifecycleOwner? = null

    fun rememberLiveOwner(owner: androidx.lifecycle.LifecycleOwner) {
        liveOwner = owner
    }

    /**
     * Freezes what the camera is showing into an ordinary still.
     *
     * This is what the live view is *for*: everything downstream — export, presets, the
     * effect chain — works on a source, and the frozen frame becomes one indistinguishable
     * from a loaded photograph.
     */
    fun freezeLive() {
        val frame = lastLive ?: return
        stopLive()
        adopt(StillSource(frame.pixels, frame.width, frame.height))
        _state.value = _state.value.copy(
            hasImage = true,
            isVideo = false,
            previewPosition = 0f,
            status = "frozen ${frame.width}×${frame.height}",
        )
        viewModelScope.launch {
            rebuild(_state.value.params)
            renderThumbs()
        }
    }

    private var thumbJob: Job? = null

    /**
     * Renders one small preview per preset from the current source.
     *
     * Effects work in absolute pixels, so a glow radius of 200 covers a 160px thumbnail
     * entirely while barely showing at export size. The thumbnail is there to show a
     * preset's *character*, not to predict its output — the live preview has the same
     * limitation at [PreviewQuality.FULL], this one is just further along the same scale.
     *
     * An animated preset is shown at frame 0. Two dozen running loops in a list would be
     * neither readable nor affordable.
     */
    private fun renderThumbs() {
        val current = source ?: return
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch {
            val presets = _state.value.presets
            val pixels = current.pixelsAt(_state.value.previewPosition)
            val thumbs = withContext(Dispatchers.Default) {
                presets.associate { preset ->
                    preset.name to Pipeline.run(
                        pixels,
                        current.width,
                        current.height,
                        preset.params,
                        THUMB_MAX_SIDE,
                    ).bitmap
                }
            }
            // The previous set is dropped rather than recycled: Compose may still be drawing
            // one of them this frame, and a recycled bitmap under the canvas crashes.
            _state.value = _state.value.copy(presetThumbs = thumbs)
        }
    }

    fun applyPreset(preset: Preset) {
        updateParams(preset.params)
        _state.value = _state.value.copy(status = "preset ${preset.name}")
    }

    private companion object {
        const val DEBOUNCE_MS = 90L
        const val MAX_HISTORY = 50
        /**
         * Preview budget while a slider is held. The same figure the live camera renders at,
         * for the same reason: it is the size at which a full chain still keeps up with input.
         */
        const val SCRUB_MAX_SIDE = 480
        const val THUMB_MAX_SIDE = 160
        const val ANIM_EXPORT_MAX_SIDE = 1080
        const val MEMORY_BUDGET_BYTES = 96L * 1024 * 1024
    }
}
