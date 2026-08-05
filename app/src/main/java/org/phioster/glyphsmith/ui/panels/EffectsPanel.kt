package org.phioster.glyphsmith.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.phioster.glyphsmith.render.RenderSettings
import org.phioster.glyphsmith.effects.EffectId
import org.phioster.glyphsmith.effects.EffectStack
import org.phioster.glyphsmith.ui.TerminalButton
import org.phioster.glyphsmith.ui.TerminalToggle
import org.phioster.glyphsmith.ui.theme.Term
import kotlin.random.Random

/** What a section's ▲/▼ buttons do, and whether each of them is available at all. */
internal data class MoveHandler(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMove: (Int) -> Unit,
)

/**
 * The effect chain: one collapsible block per effect, rendered in the order they run.
 *
 * Controls only appear once an effect is switched on — eight effects' worth of sliders at
 * once is unusable on a phone, and a slider that does nothing is worse than a hidden one.
 *
 * The order is movable because it matters more than most of the sliders do: damage applied
 * before the glow blooms through it, damage applied after cuts the bloom apart.
 *
 * Which controls a slot shows is [EffectControls], so this file stays about the chain — which
 * effects run, in what order, and how many of them are on. It iterates whatever
 * `effectiveOrder()` hands it, so an effect added to the enum appears here without this file
 * being touched at all.
 */
@Composable
fun EffectsPanel(
    params: RenderSettings,
    onChange: (RenderSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fx = params.effects
    val order = fx.effectiveOrder()
    fun update(block: EffectStack.() -> EffectStack) = onChange(params.copy(effects = fx.block()))

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("FX CHAIN", color = Term.Ink, style = MaterialTheme.typography.labelSmall)
            Text(
                "${fx.activeCount}/${EffectId.entries.size} active",
                color = if (fx.activeCount > 0) Term.Ink else Term.InkFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            order.joinToString(" → ") { it.label },
            color = Term.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )

        order.forEachIndexed { index, id ->
            EffectControls(
                id = id,
                fx = fx,
                move = MoveHandler(
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    onMove = { delta -> update { reorder(id, delta) } },
                ),
                onChange = { changed -> onChange(params.copy(effects = changed)) },
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TerminalButton(
                label = "reset order",
                onClick = { update { copy(order = EffectId.entries.toList()) } },
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "reset all effects",
                accent = Term.Amber,
                onClick = { onChange(params.copy(effects = EffectStack())) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun EffectSection(
    title: String,
    enabled: Boolean,
    move: MoveHandler,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .border(1.dp, if (enabled) Term.InkDim else Term.InkFaint, RectangleShape)
            .background(if (enabled) Term.Surface else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TerminalToggle(
                label = title,
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.weight(1f),
            )
            TerminalButton(
                label = "▲",
                enabled = move.canMoveUp,
                onClick = { move.onMove(-1) },
                modifier = Modifier.padding(start = 6.dp),
            )
            TerminalButton(
                label = "▼",
                enabled = move.canMoveDown,
                onClick = { move.onMove(1) },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (enabled) content()
    }
}

/** Random effects are seeded, so a look can be re-rolled deliberately and then kept. */
@Composable
internal fun SeedRow(seed: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SEED $seed", color = Term.InkDim, style = MaterialTheme.typography.bodySmall)
        TerminalButton(label = "reroll", onClick = { onChange(Random.nextInt(1, 100_000)) })
    }
}
