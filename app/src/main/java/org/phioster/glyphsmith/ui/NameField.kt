package org.phioster.glyphsmith.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.ui.theme.Term

/** A line to type a name into, and the button that takes it: the row "save current look" opens. */
@Composable
fun NameRow(hint: String, action: String, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .border(1.dp, Term.Amber, RectangleShape)
                .background(Term.SurfaceHigh)
                .padding(horizontal = 8.dp, vertical = 9.dp),
        ) {
            if (name.isEmpty()) Text(hint, color = Term.InkFaint, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Ink),
                cursorBrush = SolidColor(Term.Ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TerminalButton(label = action, enabled = name.isNotBlank(), onClick = { onDone(name.trim()) })
    }
}
