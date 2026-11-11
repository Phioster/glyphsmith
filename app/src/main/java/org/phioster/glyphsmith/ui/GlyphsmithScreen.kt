package org.phioster.glyphsmith.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.UiState
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.anim.QuantizeMethod
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PlaybackQuality
import org.phioster.glyphsmith.data.PreviewQuality
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.glyph.SvgMode
import org.phioster.glyphsmith.ui.panels.AnimPanel
import org.phioster.glyphsmith.ui.panels.RenderPanel
import org.phioster.glyphsmith.ui.panels.ColorPanel
import org.phioster.glyphsmith.ui.panels.EffectsPanel
import org.phioster.glyphsmith.ui.panels.LayerPanel
import org.phioster.glyphsmith.ui.panels.OutputPanel
import org.phioster.glyphsmith.ui.panels.PresetPanel
import org.phioster.glyphsmith.ui.panels.MappingPanel
import org.phioster.glyphsmith.ui.theme.Term
import org.phioster.glyphsmith.data.PresetStore
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField

private enum class Tab(val label: String) {
    ASCII("SET"),
    MAPPING("MAP"),
    COLOR("COLOUR"),
    EFFECTS("FX"),
    LAYERS("LYR"),
    ANIM("ANIM"),
    OUTPUT("OUT"),
    PRESETS("PRE"),
}

@Composable
fun GlyphsmithScreen(
    state: UiState,
    onParamsChange: (RenderSettings) -> Unit,
    onPickImage: (android.net.Uri) -> Unit,
    onPickVideo: (android.net.Uri) -> Unit,
    onCapture: (android.net.Uri) -> Unit,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit,
    onFreezeLive: () -> Unit,
    onFlipCamera: () -> Unit,
    onPreviewPosition: (Float) -> Unit,
    onFormatChange: (ImageFormat) -> Unit,
    onExportPng: () -> Unit,
    onExportTxt: () -> Unit,
    onExportSvg: (SvgMode) -> Unit,
    onExportPixelSvg: () -> Unit,
    onExportHtml: () -> Unit,
    onExportAnsi: () -> Unit,
    onRunBatch: (List<android.net.Uri>) -> Unit,
    onExtractPalette: (Int, QuantizeMethod) -> Unit,
    onCopy: () -> Unit,
    onShareImage: () -> Unit,
    onShareText: () -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onExportPresets: () -> Unit,
    onImportPresets: (android.net.Uri) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPlayAnimation: () -> Unit,
    onStopAnimation: () -> Unit,
    onExportGif: () -> Unit,
    onExportMp4: () -> Unit,
    themeId: String,
    onThemeChange: (String) -> Unit,
    onAutoOrderRamp: () -> Unit,
    onToggleFavourite: (String) -> Unit,
    onRandomise: () -> Unit,
    onResetPresets: () -> Unit,
    onExportPalette: () -> Unit,
    onImportPalette: (android.net.Uri) -> Unit,
    onExportShapeSet: () -> Unit,
    onImportShapeSet: (android.net.Uri) -> Unit,
    onApplyPalette: (org.phioster.glyphsmith.data.PaletteFile) -> Unit,
    onForgetPalette: (String) -> Unit,
    onImportScreen: (android.net.Uri, Int) -> Unit,
    onPreviewQualityChange: (PreviewQuality) -> Unit,
    onPlaybackQualityChange: (PlaybackQuality) -> Unit,
    onToggleFavouritePalette: (String) -> Unit,
    onToggleFavouriteStyle: (String) -> Unit,
    onLoopedChange: (Boolean) -> Unit,
    onScrubbing: (Boolean) -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.ASCII) }
    var fullView by remember { mutableStateOf(false) }
    var compact by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickImage)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickVideo)
    }

    // The destination has to be decided before the camera is launched and read after it
    // returns, and TakePicture reports only success — so the uri is held here rather than
    // coming back through the result.
    val context = LocalContext.current
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    // What to do once the permission comes back. Both camera features need it, so the
    // launcher is shared and the intent is remembered rather than duplicated.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Refused is a state to report, not an error to swallow: without saying so the
        // button would simply do nothing and look broken.
        permissionDenied = !granted
        val next = afterPermission
        afterPermission = null
        if (granted) next?.invoke()
    }

    /**
     * Runs [action] once the camera permission is held.
     *
     * Taking a photo hands the work to the system camera app, which needs no permission of
     * ours — right up until this app *declares* CAMERA for the live view. From that point
     * Android refuses to start IMAGE_CAPTURE from a process that has not been granted it,
     * and the refusal is a SecurityException rather than a returned failure. So both paths
     * go through here.
     */
    fun withCamera(action: () -> Unit) {
        permissionDenied = false
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            afterPermission = action
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pending
        pending = null
        if (saved && target != null) onCapture(target)
    }

    // Provided once here so all hundred-odd sliders report drag state without any of them
    // taking a parameter for it.
    CompositionLocalProvider(LocalScrubReporter provides onScrubbing) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Term.Background)
            .padding(horizontal = 12.dp),
    ) {
        Header(
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onUndo = onUndo,
            onRedo = onRedo,
            onLoad = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onLoadVideo = {
                videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            },
            onCapture = {
                withCamera {
                    val (_, uri) = org.phioster.glyphsmith.data.CameraCapture.destination(context)
                    pending = uri
                    camera.launch(uri)
                }
            },
            live = state.liveCamera,
            onLive = {
                if (state.liveCamera) onStopLive() else withCamera(onStartLive)
            },
        )

        if (permissionDenied) {
            Text(
                "camera permission refused — both [cam] and [live] need it. Android requires " +
                    "it of any app that declares it, even for handing the job to the system " +
                    "camera. You can grant it in the app's settings.",
                color = Term.Amber,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        if (state.liveCamera) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TerminalButton("freeze", onFreezeLive, Modifier.weight(1f))
                TerminalButton("flip", onFlipCamera, Modifier.weight(1f))
                TerminalButton("stop", onStopLive, Modifier.weight(1f))
            }
        }

        // Small by default, so the controls get the height: on a phone the picture and a list
        // of sliders cannot both be large, and it is the list that needs scrolling.
        if (compact) {
            CompactPreview(state, onFullView = { fullView = true }, onGrow = { compact = false })
        } else {
            Preview(state, Modifier.weight(1f))
            StatusLine(state, onShrink = { compact = true }) { fullView = true }
        }

        if (fullView) FullView(state) { fullView = false }

        TabRow(tab) { tab = it }

        Column(
            Modifier
                .weight(1.15f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            when (tab) {
                Tab.ASCII -> RenderPanel(
                    params = state.params,
                    onChange = onParamsChange,
                    fontLabel = state.fontLabel,
                    missingGlyphs = state.missingGlyphs,
                    rampCoverage = state.rampCoverage,
                    onAutoOrder = onAutoOrderRamp,
                    onExportShapeSet = onExportShapeSet,
                    onImportShapeSet = onImportShapeSet,
                )
                Tab.MAPPING -> MappingPanel(
                    params = state.params,
                    onChange = onParamsChange,
                    favourites = state.favouriteStyles,
                    onToggleFavourite = onToggleFavouriteStyle,
                    onImportScreen = onImportScreen,
                )
                Tab.COLOR -> ColorPanel(
                    params = state.params,
                    onChange = onParamsChange,
                    onExtractPalette = onExtractPalette,
                    onExportPalette = onExportPalette,
                    onImportPalette = onImportPalette,
                    imported = state.importedPalettes,
                    onApplyPalette = onApplyPalette,
                    onForgetPalette = onForgetPalette,
                    favourites = state.favouritePalettes,
                    onToggleFavourite = onToggleFavouritePalette,
                )
                Tab.EFFECTS -> EffectsPanel(state.params, onParamsChange)
                Tab.LAYERS -> LayerPanel(state.params, onParamsChange)
                Tab.ANIM -> AnimPanel(
                    state = state,
                    onChange = onParamsChange,
                    onPlay = onPlayAnimation,
                    onStop = onStopAnimation,
                    onExportGif = onExportGif,
                    onExportMp4 = onExportMp4,
                    onPreviewPosition = onPreviewPosition,
                )

                Tab.OUTPUT -> OutputPanel(
                    state = state,
                    onChange = onParamsChange,
                    onFormatChange = onFormatChange,
                    onExportPng = onExportPng,
                    onExportTxt = onExportTxt,
                    onExportSvg = onExportSvg,
                    onExportPixelSvg = onExportPixelSvg,
                    onExportHtml = onExportHtml,
                    onExportAnsi = onExportAnsi,
                    onRunBatch = onRunBatch,
                    onCopy = onCopy,
                    onShareImage = onShareImage,
                    onShareText = onShareText,
                )

                Tab.PRESETS -> PresetPanel(
                    presets = state.presets,
                    onApply = onApplyPreset,
                    onSave = onSavePreset,
                    onDelete = onDeletePreset,
                    onExport = onExportPresets,
                    onImport = onImportPresets,
                    thumbs = state.presetThumbs,
                    onToggleFavourite = onToggleFavourite,
                    onRandomise = onRandomise,
                    onResetPresets = onResetPresets,
                    themeId = themeId,
                    onThemeChange = onThemeChange,
                    previewQuality = state.previewQuality,
                    onPreviewQualityChange = onPreviewQualityChange,
                    playbackQuality = state.playbackQuality,
                    onPlaybackQualityChange = onPlaybackQualityChange,
                    looped = state.looped,
                    onLoopedChange = onLoopedChange,
                )
            }
        }
    }
    }
}

@Composable
private fun Header(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onLoad: () -> Unit,
    onLoadVideo: () -> Unit,
    onCapture: () -> Unit,
    live: Boolean,
    onLive: () -> Unit,
) {
    // One row for everything: the title used to take a third of the header and said nothing
    // the user did not know.
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TerminalButton(label = "img", onClick = onLoad, modifier = Modifier.weight(1f))
        TerminalButton(label = "vid", onClick = onLoadVideo, modifier = Modifier.weight(1f))
        TerminalButton(label = "cam", onClick = onCapture, modifier = Modifier.weight(1f))
        TerminalButton(
            label = if (live) "live ■" else "live",
            accent = if (live) Term.Amber else Term.Ink,
            onClick = onLive,
            modifier = Modifier.weight(1f),
        )
        TerminalButton(label = "↶", onClick = onUndo, enabled = canUndo)
        TerminalButton(label = "↷", onClick = onRedo, enabled = canRedo)
    }
}

/**
 * Pinch to zoom, drag to pan. ASCII lives or dies on per-glyph detail, and a fit-to-screen
 * preview of a 200-column grid shows none of it.
 */
@Composable
private fun Preview(state: UiState, modifier: Modifier = Modifier, framed: Boolean = true) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 12f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    val frame = if (framed) {
        Modifier.heightIn(min = 180.dp).border(1.dp, Term.InkFaint, RectangleShape).background(Term.Surface)
    } else {
        Modifier
    }
    Box(
        modifier.fillMaxWidth().then(frame),
        contentAlignment = Alignment.Center,
    ) {
        val preview = state.preview
        if (preview == null) {
            Text(
                if (state.hasImage) "rendering…" else "no image — press [LOAD IMAGE]",
                color = Term.InkDim,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "render preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transform)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        }
    }
}

/**
 * The picture as a thumbnail in a fixed place, with the status beside it. A tap shows it on the
 * whole screen; [onGrow] brings back the large preview that shares the height with the controls.
 */
@Composable
private fun CompactPreview(state: UiState, onFullView: () -> Unit, onGrow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(COMPACT_WIDTH.dp)
                .height(COMPACT_HEIGHT.dp)
                .border(1.dp, Term.InkFaint, RectangleShape)
                .background(Term.Surface)
                .clickable(enabled = state.preview != null, onClick = onFullView),
            contentAlignment = Alignment.Center,
        ) {
            val preview = state.preview
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "render preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    if (state.hasImage) "rendering…" else "no image",
                    color = Term.InkDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                "> ${state.status}",
                color = if (state.working) Term.Amber else Term.InkDim,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (state.working) "busy" else "idle",
                color = if (state.working) Term.Amber else Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TerminalButton(label = "full view", onClick = onFullView, enabled = state.preview != null)
                TerminalButton(label = "large", onClick = onGrow)
            }
        }
    }
}

private const val COMPACT_WIDTH = 150
private const val COMPACT_HEIGHT = 125

@Composable
private fun StatusLine(state: UiState, onShrink: () -> Unit, onFullView: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "> ${state.status}",
            color = if (state.working) Term.Amber else Term.InkDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (state.preview != null) {
            Text(
                "[full view]",
                color = Term.Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(onClick = onFullView).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(
            "[small]",
            color = Term.Ink,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable(onClick = onShrink).padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
        )
        Text(
            if (state.working) "busy" else "idle",
            color = if (state.working) Term.Amber else Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * The picture on the whole screen, with the same pinch and pan as the preview.
 *
 * The working layout shares the screen with the controls, which leaves a portrait image small;
 * this is the look-at-it view. It reads the same state, so an animation keeps playing in it.
 */
@Composable
private fun FullView(state: UiState, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Preview(state, Modifier.fillMaxSize(), framed = false)
            Text(
                "[close]",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TabRow(selected: Tab, onSelect: (Tab) -> Unit) {
    // Every tab is offered in every mode. MAP used to be hidden outside glyph art, on the
    // reading that the whole panel was about how luminance becomes a *character* — but most of
    // it is about how luminance becomes a *level*, which is the pixel mode's own question. The
    // panel hides its glyph half instead; see MappingSections.
    val tabs = Tab.entries

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            TerminalChip(
                label = tab.label,
                selected = tab == selected,
                onClick = { onSelect(tab) },
            )
        }
    }
}
