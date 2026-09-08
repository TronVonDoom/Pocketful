package app.pocketful.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketful.data.CardArt
import app.pocketful.data.SearchHit
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import coil3.compose.AsyncImage

/**
 * A card the collection does not have yet.
 *
 * Deliberately not a [CardListRow]: that row takes a `CardBrief`, which only exists for
 * cards this collection already knows about, and inventing a fake one to render a search
 * result would put a card in the catalog before the user had chosen it.
 *
 * Shared between the pocket picker and the search tab so a result found while filling a
 * binder and the same result found while browsing a set are visibly the same row.
 */
@Composable
fun CatalogCardRow(
    hit: SearchHit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    trailingTint: Color = Ink.Accent,
    badge: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(Ink.Surface)
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium)
            .alpha(if (enabled) 1f else 0.5f)
            .tappable(enabled = enabled, pressScale = 0.985f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val width = 38.dp
        Box(
            Modifier
                .width(width)
                .aspectRatio(CARD_ASPECT_RATIO)
                .clip(cardShape(width))
                .border(1.dp, Color.White.copy(alpha = 0.12f), cardShape(width)),
        ) {
            CardArtwork(artStem = hit.artStem, type = null, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = hit.name,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${hit.setName} · ${hit.collectorNumber}",
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            badge?.let {
                Spacer(Modifier.height(6.dp))
                Tag(it, color = Ink.Gain, background = Ink.Gain.copy(alpha = 0.14f))
            }
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(trailingIcon, "Add this card", Modifier.size(16.dp), tint = trailingTint)
        }
    }
}

/**
 * A row for something that is not a card: a game, an era, a set.
 *
 * One shape for all three so that drilling from a game into its series and on into its
 * sets feels like following a single list inward rather than crossing between three
 * screens that happen to be stacked.
 *
 * The artwork slot takes a logo rather than a picture: sets and series have wordmarks, not
 * illustrations, so it is drawn on a lightened plate at a wide aspect and fitted rather
 * than cropped -- a cropped wordmark is unreadable, which defeats the point of showing it.
 */
@Composable
fun CatalogListRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    logoStem: String? = null,
    fallbackIcon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color = Ink.Accent,
    tag: String? = null,
    tagColor: Color = Ink.TextSecondary,
    trailingIcon: ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(Ink.Surface)
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium)
            .alpha(if (enabled) 1f else 0.45f)
            .tappable(enabled = enabled, pressScale = 0.985f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoPlate(logoStem = logoStem, fallbackIcon = fallbackIcon, accent = accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tag != null) {
            Spacer(Modifier.width(10.dp))
            Tag(tag, color = tagColor, background = tagColor.copy(alpha = 0.14f))
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(trailingIcon, null, Modifier.size(15.dp), tint = Ink.TextTertiary)
        }
    }
}

/** The wordmark plate on a [CatalogListRow], with something to show when there is no logo. */
@Composable
private fun LogoPlate(
    logoStem: String?,
    fallbackIcon: ImageVector?,
    accent: Color,
    width: Dp = 46.dp,
) {
    Box(
        Modifier
            .width(width)
            .height(width * 0.72f)
            .clip(AppShape.Chip)
            .background(Ink.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val url = CardArt.logo(logoStem)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else if (fallbackIcon != null) {
            Icon(fallbackIcon, null, Modifier.size(16.dp), tint = accent.copy(alpha = 0.8f))
        }
    }
}
