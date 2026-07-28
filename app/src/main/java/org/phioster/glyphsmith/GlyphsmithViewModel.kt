package org.phioster.glyphsmith

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phioster.glyphsmith.ascii.AsciiArt
import org.phioster.glyphsmith.ascii.AsciiEngine
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.ascii.AsciiRenderer
import org.phioster.glyphsmith.data.ImageLoader
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PresetStore
import org.phioster.glyphsmith.effects.EpsilonGlow
import org.phioster.glyphsmith.export.Exporter
import org.phioster.glyphsmith.export.ImageFormat

data class UiState(
    val params: AsciiParams = AsciiParams(),
    val preview: Bitmap? = null,
    val hasImage: Boolean = false,
    val cols: Int = 0,
    val rows: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val working: Boolean = false,
    val exportFormat: ImageFormat = ImageFormat.PNG,
    val presets: List<Preset> = emptyList(),
    val status: String = "no image loaded",
)

class GlyphsmithViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication<Application>()

    private val presetStore = PresetStore(app)

    private val _state = MutableStateFlow(UiState(presets = presetStore.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val paramsFlow = MutableStateFlow(AsciiParams())

    private var sourcePixels: IntArray? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var art: AsciiArt? = null

    init {
        viewModelScope.launch {
            paramsFlow.collectLatest { params ->
                // Coalesce slider spam: collectLatest cancels the previous pass, so a drag
                // only ever renders the value the finger came to rest on.
                delay(DEBOUNCE_MS)
                rebuild(params)
            }
        }
    }

    fun updateParams(params: AsciiParams) {
        _state.value = _state.value.copy(params = params)
        paramsFlow.value = params
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, status = "decoding…")
            val loaded = withContext(Dispatchers.IO) { ImageLoader.load(context, uri) }
            if (loaded == null) {
                _state.value = _state.value.copy(working = false, status = "could not decode that image")
                return@launch
            }
            sourcePixels = withContext(Dispatchers.Default) { ImageLoader.pixelsOf(loaded) }
            sourceWidth = loaded.width
            sourceHeight = loaded.height
            loaded.recycle()
            _state.value = _state.value.copy(
                hasImage = true,
                status = "loaded ${sourceWidth}×$sourceHeight",
            )
            rebuild(_state.value.params)
        }
    }

    private suspend fun rebuild(params: AsciiParams) {
        val pixels = sourcePixels ?: return
        _state.value = _state.value.copy(working = true)
        val result = withContext(Dispatchers.Default) {
            val ramp = params.effectiveRamp().ifEmpty { " " }
            // Aspect is measured at a fixed reference size so the grid stays identical
            // between the preview and the full-size export — only the glyphs get bigger.
            val aspect = AsciiRenderer.metrics(REFERENCE_FONT_SIZE, ramp, params.fontStyle).aspect
            val grid = AsciiEngine.convert(pixels, sourceWidth, sourceHeight, params, aspect)
            val previewFont = AsciiRenderer.fitFontSize(
                grid.cols, grid.rows, ramp, params.fontSizePx, PREVIEW_MAX_SIDE, params.fontStyle,
            )
            val bitmap = EpsilonGlow.apply(AsciiRenderer.render(grid, params, previewFont), params.glow)
            val exportCell = AsciiRenderer.metrics(
                AsciiRenderer.fitFontSize(
                    grid.cols, grid.rows, ramp, params.fontSizePx,
                    AsciiRenderer.MAX_OUTPUT_SIDE, params.fontStyle,
                ),
                ramp,
                params.fontStyle,
            )
            Triple(grid, bitmap, exportCell)
        }
        art = result.first
        // The old preview is deliberately not recycled: Compose may still be drawing it
        // this frame, and a recycled bitmap under the canvas is an instant crash.
        _state.value = _state.value.copy(
            preview = result.second,
            cols = result.first.cols,
            rows = result.first.rows,
            outputWidth = result.first.cols * result.third.width,
            outputHeight = result.first.rows * result.third.height,
            working = false,
            status = "${result.first.cols}×${result.first.rows} cells",
        )
    }

    private suspend fun renderFullSize(): Bitmap? {
        val grid = art ?: return null
        val params = _state.value.params
        return withContext(Dispatchers.Default) {
            EpsilonGlow.apply(AsciiRenderer.render(grid, params, params.fontSizePx), params.glow)
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

    private companion object {
        const val DEBOUNCE_MS = 90L
        const val PREVIEW_MAX_SIDE = 1600
        const val REFERENCE_FONT_SIZE = 32
    }
}
