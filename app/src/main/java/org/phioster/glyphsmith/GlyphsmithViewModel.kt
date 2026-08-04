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
import org.phioster.glyphsmith.glyph.GlyphGrid
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.export.AndroidExports
import org.phioster.glyphsmith.state.ExportCoordinator
import org.phioster.glyphsmith.state.HistoryController
import org.phioster.glyphsmith.state.PlaybackPlan
import org.phioster.glyphsmith.state.PresetController
import org.phioster.glyphsmith.state.SourceController
import org.phioster.glyphsmith.anim.AnimationParams
import org.phioster.glyphsmith.anim.Animator
import org.phioster.glyphsmith.anim.ColorQuantizer
import org.phioster.glyphsmith.anim.QuantizeMethod
import org.phioster.glyphsmith.anim.GifEncoder
import org.phioster.glyphsmith.anim.Mp4Encoder
import org.phioster.glyphsmith.glyph.GlyphRenderOutput
import org.phioster.glyphsmith.glyph.GlyphRenderer
import org.phioster.glyphsmith.render.RenderBudget
import org.phioster.glyphsmith.glyph.CharacterSets
import org.phioster.glyphsmith.state.RandomLook
import org.phioster.glyphsmith.render.RenderMode
import org.phioster.glyphsmith.render.ColorMode
import org.phioster.glyphsmith.pipeline.RenderPipeline
import org.phioster.glyphsmith.glyph.GlyphCoverage
import org.phioster.glyphsmith.data.CameraCapture
import org.phioster.glyphsmith.data.ImageLoader
import org.phioster.glyphsmith.data.LiveCamera
import org.phioster.glyphsmith.data.LiveFrame
import org.phioster.glyphsmith.data.PaletteFile
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PlaybackQuality
import org.phioster.glyphsmith.data.PreviewQuality
import org.phioster.glyphsmith.data.Settings
import org.phioster.glyphsmith.data.Source
import org.phioster.glyphsmith.data.StillSource
import org.phioster.glyphsmith.data.VideoSource
import org.phioster.glyphsmith.export.AnimationFormat
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.glyph.SvgExporter
import org.phioster.glyphsmith.glyph.SvgMode
import org.phioster.glyphsmith.glyph.TextExporters
import org.phioster.glyphsmith.ui.theme.Term
import org.phioster.glyphsmith.ui.theme.TermThemes
import org.phioster.glyphsmith.core.color.Palettes
import org.phioster.glyphsmith.glyph.baseGlyphs
import org.phioster.glyphsmith.glyph.effectiveRamp

data class UiState(
    /**
     * A session with nothing loaded starts in Pixel Dither, not in the field default — see
     * [RenderSettings.newSession]. Loading a preset replaces this wholesale, so the mode a saved
     * look carries still wins.
     */
    val params: RenderSettings = RenderSettings.newSession(),
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

    private val presets = PresetController(app)
    private val settings = Settings(app)
    private val exports = ExportCoordinator(AndroidExports(app))

    private val _state = MutableStateFlow(
        UiState(
            presets = presets.presets,
            themeId = settings.themeId,
            previewQuality = settings.previewQuality,
            looped = settings.looped,
            playbackQuality = settings.playbackQuality,
            favouritePalettes = settings.favouritePalettes,
            favouriteStyles = settings.favouriteStyles,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val paramsFlow = MutableStateFlow(RenderSettings.newSession())

    // Captured at the debounce point rather than in updateParams(), so a slider drag is one
    // undo step rather than dozens — see HistoryController, which owns the rule.
    private val history = HistoryController(RenderSettings.newSession())

    private val sources = SourceController()
    private var art: GlyphGrid? = null

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
                history.commit(params)
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

    fun updateParams(params: RenderSettings) {
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
        val pixels = sources.frame()?.pixels ?: return@runExport "no image"
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
        val face = GlyphRenderer.faceFor(params, params.effectiveRamp().ifEmpty { " " })
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
        val text = PaletteFile.encode(_state.value.params.activePalette())
        withContext(Dispatchers.IO) { exports.palette(text) }
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

    fun undo() = history.undo()?.let(::restore)

    fun redo() = history.redo()?.let(::restore)

    private fun restore(params: RenderSettings) {
        _state.value = _state.value.copy(params = params)
        paramsFlow.value = params
        publishHistory()
    }

    private fun publishHistory() {
        _state.value = _state.value.copy(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
        )
    }

    fun exportPresets() = runExport("presets") {
        val json = presets.exportJson()
        withContext(Dispatchers.IO) { exports.presets(json) }
    }

    fun importPresets(uri: Uri) = runExport("import") {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        } ?: return@runExport "could not read that file"
        // What it could not read is reported rather than swallowed — see Import.summary().
        val status = presets.import(text)
        publishPresets(status)
        status
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
            sources.adopt(StillSource(pixels, width, height))
            _state.value = _state.value.copy(
                hasImage = true,
                isVideo = false,
                previewPosition = sources.position,
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
            sources.adopt(opened)
            _state.value = _state.value.copy(
                hasImage = true,
                isVideo = true,
                previewPosition = sources.position,
                status = "video ${opened.width}×${opened.height}",
            )
            rebuild(_state.value.params)
            renderThumbs()
        }
    }

    /** Scrubs the preview through a video. Ignored for a still, which has one frame. */
    /**
     * Scrubs a video's preview frame.
     *
     * The previous render is cancelled rather than left to finish. This is the one slider that
     * does not go through [paramsFlow] — the params do not change, only which frame is decoded —
     * so it never had `collectLatest`'s coalescing, and a drag used to launch one render per
     * emission with all of them racing to write the preview. The last one to finish won, which is
     * not necessarily the last one asked for.
     */
    fun setPreviewPosition(position: Float) {
        if (!sources.seek(position)) return
        _state.value = _state.value.copy(previewPosition = sources.position)
        positionJob?.cancel()
        positionJob = viewModelScope.launch { rebuild(_state.value.params) }
    }

    private var positionJob: Job? = null

    override fun onCleared() {
        live?.release()
        live = null
        sources.release()
        super.onCleared()
    }

    private suspend fun rebuild(params: RenderSettings) {
        val frame = sources.frame() ?: return
        _state.value = _state.value.copy(working = true)
        val scrub = scrubbing
        val budget = if (scrub) SCRUB_MAX_SIDE else _state.value.previewQuality.maxSide
        val result = withContext(Dispatchers.Default) {
            RenderPipeline.run(
                frame.pixels, frame.width, frame.height, params, budget, AppRenderModules,
                isScrubbing = scrub,
            )
        }
        val glyphs = result.output as? GlyphRenderOutput
        art = glyphs?.art
        // Measured once per rebuild and cached inside GlyphCoverage, so a slider drag pays
        // for the rasterisation only the first time a glyph is seen in this face. The pixel
        // mode has no face to profile, so there is nothing to measure and nothing to show.
        val face = glyphs?.face
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
        val frame = sources.frame() ?: return null
        val params = _state.value.params
        return withContext(Dispatchers.Default) {
            RenderPipeline.run(
                frame.pixels, frame.width, frame.height, params, RenderBudget.MAX_OUTPUT_SIDE,
                AppRenderModules,
            ).bitmap
        }
    }

    fun setExportFormat(format: ImageFormat) {
        _state.value = _state.value.copy(exportFormat = format)
    }

    fun exportImage() = runExport("image") {
        val format = _state.value.exportFormat
        val bitmap = renderFullSize()
        withContext(Dispatchers.IO) { exports.image(bitmap, format) }
    }

    fun exportText() = runExport("txt") {
        val text = art?.toText()
        withContext(Dispatchers.IO) { exports.text(text) }
    }

    /**
     * The grid as vectors. [GlyphRenderer.layout] always measures against the full output
     * size, so this is the export resolution even though `art` came from the preview pass —
     * the grid itself doesn't change with preview scale, only the glyph size does.
     */
    fun exportSvg(mode: SvgMode) = runExport("svg") {
        val grid = art
        val params = _state.value.params
        val svg = grid?.let {
            withContext(Dispatchers.Default) { SvgExporter.build(it, params, params.fontSizePx, mode) }
        }
        withContext(Dispatchers.IO) { exports.svg(svg, mode) }
    }

    fun exportHtml() = runExport("html") {
        val text = art?.let { TextExporters.html(it, _state.value.params) }
        withContext(Dispatchers.IO) { exports.html(text) }
    }

    fun exportAnsi() = runExport("ansi") {
        val text = art?.let { TextExporters.ansi(it, _state.value.params) }
        withContext(Dispatchers.IO) { exports.ansi(text) }
    }

    /**
     * Runs every picked image through the current settings and saves each result.
     *
     * Deliberately sequential. Each pass allocates a full-size bitmap and the effect chain
     * works on its own copies, so running several at once is the quickest way to meet the
     * heap limit — and the phone has nothing to gain from it anyway.
     *
     * The loaded source is not touched: a batch is something you launch *from* a look you have
     * already dialled in, and each picked image is rendered straight through the pipeline
     * without ever becoming the session's source. It used to save and restore the source
     * around the loop, from when it did adopt each one; both halves of that had long since
     * become assignments of a value to itself.
     */
    fun runBatch(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val params = _state.value.params
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
                        RenderPipeline.run(
                            pixels, width, height, params, RenderBudget.MAX_OUTPUT_SIDE,
                            AppRenderModules,
                        ).bitmap
                    }
                    val format = _state.value.exportFormat
                    val written = withContext(Dispatchers.IO) {
                        exports.batchImage(bitmap, format, index + 1)
                    }
                    if (written) saved++ else failed++
                }
                _state.value = _state.value.copy(
                    batchDone = index + 1,
                    status = "batch ${index + 1}/${uris.size}",
                )
            }

            _state.value = _state.value.copy(
                working = false,
                batchDone = 0,
                batchTotal = 0,
                status = exports.batchStatus(saved, failed),
            )
            rebuild(_state.value.params)
        }
    }

    fun copyText() = runExport("copy") { exports.copy(art?.toText()) }

    fun shareImage() = runExport("share") {
        val bitmap = renderFullSize()
        withContext(Dispatchers.IO) { exports.shareImage(bitmap, _state.value.exportFormat) }
    }

    fun shareText() = runExport("share") {
        val text = art?.toText()
        withContext(Dispatchers.IO) { exports.shareText(text) }
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
        val current = sources.source ?: return
        viewModelScope.launch {
            cancelPlayback()
            val params = _state.value.params
            val animation = params.animation
            _state.value = _state.value.copy(working = true, status = "rendering ${animation.frames} frames…")

            // Everything countable about the run — which frames, how large, how long each is
            // held — is decided before any of them is drawn. See PlaybackPlan.
            val plan = PlaybackPlan.of(animation, _state.value.playbackQuality)
            val frames = withContext(Dispatchers.Default) {
                renderFrames(current, params, animation, plan)
            }
            animFrames = frames
            _state.value = _state.value.copy(
                working = false,
                animPlaying = true,
                animFrames = frames.size,
                status = plan.status(animation.durationSeconds),
            )

            val frameDelay = plan.frameDelayMs
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
        base: RenderSettings,
        animation: AnimationParams,
        plan: PlaybackPlan,
    ): List<Bitmap> {
        val budgetSide = plan.budgetSide
        var width = 0
        var height = 0
        return plan.positions.map { frame ->
            val position = frame.toFloat() / animation.frames
            // The clock is set here rather than in paramsAt, so temporal noise still moves
            // over a video whose parameter tracks are all switched off.
            val frameParams = Animator.paramsAt(base, animation, frame)
                .let { it.copy(temporal = it.temporal.copy(time = position)) }
            // A still hands back the same buffer every time; a video decodes this position.
            val pixels = source.pixelsAt(position)
            val rendered = RenderPipeline.run(
                pixels, source.width, source.height, frameParams, budgetSide, AppRenderModules,
            ).bitmap
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

    fun exportGif() = runExport("gif") {
        val current = sources.source ?: return@runExport "no image"
        val params = _state.value.params
        val animation = params.animation
        val bytes = withContext(Dispatchers.Default) {
            val frames = renderFrames(current, params, animation, PlaybackPlan.everyFrame(animation, ANIM_EXPORT_MAX_SIDE))
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
        withContext(Dispatchers.IO) { exports.animation(bytes, AnimationFormat.GIF) }
    }

    fun exportMp4() = runExport("mp4") {
        val current = sources.source ?: return@runExport "no image"
        val params = _state.value.params
        val animation = params.animation
        val file = java.io.File(context.cacheDir, "glyphsmith-anim.mp4")
        val failure = withContext(Dispatchers.Default) {
            val frames = renderFrames(current, params, animation, PlaybackPlan.everyFrame(animation, ANIM_EXPORT_MAX_SIDE))
            val result = Mp4Encoder.encode(frames, animation.fps.coerceAtLeast(1), file)
            frames.forEach { it.recycle() }
            result
        }
        if (failure != null) return@runExport "mp4: $failure"
        val status = withContext(Dispatchers.IO) {
            exports.animation(file.readBytes(), AnimationFormat.MP4)
        }
        file.delete()
        status
    }

    fun savePreset(name: String, description: String = "") =
        publishPresets(presets.save(name, _state.value.params, description))

    fun renamePreset(from: String, to: String, description: String) =
        publishPresets(presets.rename(from, to, description))

    fun deletePreset(name: String) = publishPresets(presets.delete(name))

    fun toggleFavourite(name: String) {
        presets.toggleFavourite(name)
        // No status line: the star that just changed is the feedback, and no thumbnail moved.
        _state.value = _state.value.copy(presets = presets.presets)
    }

    /**
     * One place where an operation on the library becomes something the interface shows.
     *
     * Five call sites used to spell this out for themselves — copy the list in, set a line,
     * rebuild the thumbnails — which is three steps repeated five times and one of them
     * eventually going missing.
     */
    private fun publishPresets(status: String) {
        _state.value = _state.value.copy(presets = presets.presets, status = status)
        renderThumbs()
    }

    /** Puts the shipped library back, discarding anything saved on top of it. */
    fun resetPresets() = publishPresets(presets.reset())

    /**
     * Rolls a look at random.
     *
     * The roll itself is [RandomLook] — a plain function, so the ranges that make it produce
     * something worth looking at are testable without a device. This binds it to the session:
     * one undo step, and a status line naming what came up.
     *
     * No mode is passed, so it rolls [RenderMode.DEFAULT] — Pixel Dither. Surprise Me is the
     * general-purpose button and Glyph Art is chosen deliberately, not stumbled into.
     */
    fun randomise() {
        val rolled = RandomLook.roll(_state.value.params)
        updateParams(rolled)
        val set = CharacterSets.byId(rolled.charSetId)
        _state.value = _state.value.copy(status = "rolled ${set.name} · ${rolled.ditherMode.label}")
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
                RenderPipeline.run(
                    frame.pixels, frame.width, frame.height, _state.value.params,
                    LiveCamera.MAX_SIDE, AppRenderModules,
                )
            }.getOrNull() ?: return@start
            art = (result.output as? GlyphRenderOutput)?.art
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
        sources.adopt(StillSource(frame.pixels, frame.width, frame.height))
        _state.value = _state.value.copy(
            hasImage = true,
            isVideo = false,
            previewPosition = sources.position,
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
        val frame = sources.frame() ?: return
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch {
            val presets = _state.value.presets
            val thumbs = withContext(Dispatchers.Default) {
                presets.associate { preset ->
                    preset.name to RenderPipeline.run(
                        frame.pixels,
                        frame.width,
                        frame.height,
                        preset.params,
                        THUMB_MAX_SIDE,
                        AppRenderModules,
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
        /**
         * Preview budget while a slider is held. The same figure the live camera renders at,
         * for the same reason: it is the size at which a full chain still keeps up with input.
         */
        const val SCRUB_MAX_SIDE = 480
        const val THUMB_MAX_SIDE = 160
        const val ANIM_EXPORT_MAX_SIDE = 1080
    }
}
