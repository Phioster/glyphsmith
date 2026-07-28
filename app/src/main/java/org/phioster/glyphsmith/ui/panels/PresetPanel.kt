package org.phioster.glyphsmith.ui.panels

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ascii.CharacterSets
import org.phioster.glyphsmith.data.Preset
import org.phioster.glyphsmith.ui.SectionHeader
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.theme.Term

@Composable
fun PresetPanel(
    presets: List<Preset>,
    onApply: (Preset) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: () -> Unit,
    onImport: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    // Exports are plain JSON, but pickers and file managers label them inconsistently, so
    // text/* and */* are accepted too rather than hiding the file the user just exported.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }

    Column(modifier.fillMaxWidth()) {
        SectionHeader("presets")

        presets.forEach { preset ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .border(1.dp, Term.InkFaint, RectangleShape)
                    .background(Term.SurfaceHigh),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onApply(preset) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    Text(preset.name, color = Term.Ink, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${CharacterSets.byId(preset.params.charSetId).name} · " +
                            "cell ${preset.params.cellSize} · depth ${preset.params.depth}",
                        color = Term.InkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TerminalButton(
                    label = "del",
                    accent = Term.Amber,
                    onClick = { onDelete(preset.name) },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }

        SectionHeader("save current")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Ink),
                cursorBrush = SolidColor(Term.Ink),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Term.InkDim, RectangleShape)
                    .background(Term.SurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 9.dp),
            )
            TerminalButton(
                label = "save",
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(name)
                    name = ""
                },
            )
        }

        SectionHeader("transfer")

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalButton(
                label = "export all",
                onClick = onExport,
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "import",
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "exports land in Download/Glyphsmith; importing merges by name",
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
