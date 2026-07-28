package org.phioster.glyphsmith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.phioster.glyphsmith.ui.GlyphsmithScreen
import org.phioster.glyphsmith.ui.theme.GlyphsmithTheme
import org.phioster.glyphsmith.ui.theme.Term

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlyphsmithTheme {
                val viewModel: GlyphsmithViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Term.Background)
                        .safeDrawingPadding(),
                ) {
                    GlyphsmithScreen(
                        state = state,
                        onParamsChange = viewModel::updateParams,
                        onPickImage = viewModel::loadImage,
                        onFormatChange = viewModel::setExportFormat,
                        onExportPng = viewModel::exportImage,
                        onExportTxt = viewModel::exportText,
                        onCopy = viewModel::copyText,
                        onShareImage = viewModel::shareImage,
                        onShareText = viewModel::shareText,
                        onApplyPreset = viewModel::applyPreset,
                        onSavePreset = viewModel::savePreset,
                        onDeletePreset = viewModel::deletePreset,
                        onExportPresets = viewModel::exportPresets,
                        onImportPresets = viewModel::importPresets,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onPlayAnimation = viewModel::playAnimation,
                        onStopAnimation = viewModel::stopAnimation,
                        onExportGif = viewModel::exportGif,
                        onExportMp4 = viewModel::exportMp4,
                    )
                }
            }
        }
    }
}
