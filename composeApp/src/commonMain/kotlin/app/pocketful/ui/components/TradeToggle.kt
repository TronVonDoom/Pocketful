package app.pocketful.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/**
 * "Up for trade", as one row.
 *
 * Given its own bordered row rather than dropped in with the condition and grade readouts
 * because it is the only thing on the sheet that is an *action* -- everything above it
 * describes the card, and this changes something. The whole row lights up when it is on,
 * so a card on the table is obvious from across the sheet without reading the label.
 */
@Composable
fun TradeToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Ink.Gain
    val background by animateColorAsState(
        if (checked) accent.copy(alpha = 0.10f) else Ink.SurfaceRaised,
        label = "tradeBackground",
    )
    val outline by animateColorAsState(
        if (checked) accent.copy(alpha = 0.45f) else Ink.OutlineSoft,
        label = "tradeOutline",
    )

    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Small)
            .background(background)
            .border(1.dp, outline, AppShape.Small)
            .tappable(pressScale = 0.99f) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(AppShape.Chip)
                .background(if (checked) accent.copy(alpha = 0.18f) else Ink.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.Trade,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (checked) accent else Ink.TextTertiary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Up for trade",
                color = if (checked) Ink.TextPrimary else Ink.TextSecondary,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // Says what the flag does *not* do. The first question people ask of a
                // toggle like this is whether it is about to move their card.
                text = if (checked) {
                    "Listed on your trade screen. Stays where it is filed."
                } else {
                    "Offer this copy without moving it out of its pocket."
                },
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(12.dp))
        ToggleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
