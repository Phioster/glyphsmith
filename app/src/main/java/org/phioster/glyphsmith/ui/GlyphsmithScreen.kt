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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.UiState
import org.phioster.glyphsmith.ascii.AsciiParams
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.data.PreviewQuality
import org.phioster.glyphsmith.export.ImageFormat
import org.phioster.glyphsmith.export.SvgMode
import org.phioster.glyphsmith.ui.panels.AnimPanel
import org.phioster.glyphsmith.ui.panels.AsciiPanel
import org.phioster.glyphsmith.ui.panels.ColorPanel
import org.phioster.glyphsmith.ui.panels.EffectsPanel
import org.phioster.glyphsmith.ui.panels.OutputPanel
import org.phioster.glyphsmith.ui.panels.PresetPanel
import org.phioster.glyphsmith.ui.panels.MappingPanel
import org.phioster.glyphsmith.ui.theme.Term

private enum class Tab(val label: String) {
    ASCII("SET"),
    MAPPING("MAP"),
    COLOR("COLOUR"),
    EFFECTS("FX"),
    ANIM("ANIM"),
    OUTPUT("OUT"),
    PRESETS("PRE"),
}

@Composable
fun GlyphsmithScreen(
    state: UiState,
    onParamsChange: (AsciiParams) -> Unit,
    onPickImage: (android.net.Uri) -> Unit,
    onPickVideo: (android.net.Uri) -> Unit,
    onPreviewPosition: (Float) -> Unit,
    onFormatChange: (ImageFormat) -> Unit,
    onExportPng: () -> Unit,
    onExportTxt: () -> Unit,
    onExportSvg: (SvgMode) -> Unit,
    onExportHtml: () -> Unit,
    onExportAnsi: () -> Unit,
    onRunBatch: (List<android.net.Uri>) -> Unit,
    onExtractPalette: (Int) -> Unit,
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
    onPreviewQualityChange: (PreviewQuality) -> Unit,
    onLoopedChange: (Boolean) -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.ASCII) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickImage)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickVideo)
    }

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
        )

        Preview(state, Modifier.weight(1f))

        StatusLine(state)

        TabRow(tab) { tab = it }

        Column(
            Modifier
                .weight(1.15f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            when (tab) {
                Tab.ASCII -> AsciiPanel(
                    params = state.params,
                    onChange = onParamsChange,
                    fontLabel = state.fontLabel,
                    missingGlyphs = state.missingGlyphs,
                    rampCoverage = state.rampCoverage,
                    onAutoOrder = onAutoOrderRamp,
                )
                Tab.MAPPING -> MappingPanel(state.params, onParamsChange)
                Tab.COLOR -> ColorPanel(
                    params = state.params,
                    onChange = onParamsChange,
                    onExtractPalette = onExtractPalette,
                    onExportPalette = onExportPalette,
                    onImportPalette = onImportPalette,
                )
                Tab.EFFECTS -> EffectsPanel(state.params, onParamsChange)
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
                    looped = state.looped,
                    onLoopedChange = onLoopedChange,
                )
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
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("GLYPHSMITH", color = Term.Ink, style = MaterialTheme.typography.titleLarge)
            Text("ascii forge", color = Term.InkFaint, style = MaterialTheme.typography.labelSmall)
        }
        TerminalButton(label = "↶", onClick = onUndo, enabled = canUndo)
        TerminalButton(label = "↷", onClick = onRedo, enabled = canRedo)
        TerminalButton(label = "img", onClick = onLoad)
        TerminalButton(label = "vid", onClick = onLoadVideo)
    }
}

/**
 * Pinch to zoom, drag to pan. ASCII lives or dies on per-glyph detail, and a fit-to-screen
 * preview of a 200-column grid shows none of it.
 */
@Composable
private fun Preview(state: UiState, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 12f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .border(1.dp, Term.InkFaint, RectangleShape)
            .background(Term.Surface),
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
                contentDescription = "ASCII preview",
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

@Composable
private fun StatusLine(state: UiState) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "> ${state.status}",
            color = if (state.working) Term.Amber else Term.InkDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (state.working) "busy" else "idle",
            color = if (state.working) Term.Amber else Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TabRow(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { tab ->
            TerminalChip(
                label = tab.label,
                selected = tab == selected,
                onClick = { onSelect(tab) },
            )
        }
    }
}
