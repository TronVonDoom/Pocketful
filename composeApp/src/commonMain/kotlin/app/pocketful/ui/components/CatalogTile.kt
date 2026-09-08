package app.pocketful.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketful.data.CardArt
import app.pocketful.data.RemoteSet
import app.pocketful.domain.TcgGame
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.mark
import coil3.compose.AsyncImage

/**
 * The catalog as tiles rather than rows.
 *
 * Games and sets are the two things in this app that are *identified by a logo* -- nobody
 * recognises "SV03.5" but everybody recognises the 151 wordmark -- and a list row gives a
 * wordmark a 46dp box on its left edge, which is enough to know a logo is there and not
 * enough to read it. Two to a row at full tile width is the smallest arrangement where
 * the artwork does the identifying, which is the whole reason for showing it.
 */

/**
 * A game, as the front door to its catalog.
 *
 * The plate is drawn rather than fetched. The set and series wordmarks come off TCGdex,
 * but there is no comparable source for the games themselves, and shipping five publisher
 * logos into the binary is a licensing question rather than a layout one -- so the tile
 * carries an emblem of the app's own from [GameMarks] instead, above the game's name.
 *
 * The mark is what makes the grid scannable. Five tinted plates of text are five plates
 * of text: at arm's length the eye gets colour and word-shape and has to read to be sure,
 * and Magic and Lorcana sat in the same warm-to-cool range as each other. A shape reads
 * before a word does, which is the entire reason a game has a logo in the first place.
 */
@Composable
fun GameTile(
    game: TcgGame,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = game.accent
    TileSurface(onClick = onClick, modifier = modifier, enabled = game.connected) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(AppShape.Medium)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.06f)),
                    ),
                )
                .border(1.dp, accent.copy(alpha = 0.22f), AppShape.Medium)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = game.mark,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = accent,
                )
                Spacer(Modifier.height(7.dp))
                // A step down from the headline this used to be: the mark above it is
                // now doing the identifying, and a wordmark set as loud as the emblem
                // makes the pair look like two attempts at the same job.
                Text(
                    text = game.wordmark,
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = game.label,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = caption,
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            val tagColor = if (game.connected) Ink.Gain else Ink.TextTertiary
            Tag(
                text = if (game.connected) "LIVE" else "SOON",
                color = tagColor,
                background = tagColor.copy(alpha = 0.16f),
            )
        }
    }
}

/**
 * A set, identified by its own wordmark.
 *
 * The name is allowed two lines where every other tile in the app takes one. Set names
 * run long and truncate badly -- "Sword & Shield Black Star P…" and "Sword & Shield
 * Brilliant St…" are the same string to a reader skimming a grid -- and the logo above it
 * is not always legible enough to break the tie on its own.
 */
@Composable
fun SetTile(
    set: RemoteSet,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TileSurface(onClick = onClick, modifier = modifier) {
        LogoPanel(logoStem = set.logo, fallbackIcon = AppIcons.Binders, modifier = Modifier.weight(1f))
        Spacer(Modifier.height(9.dp))
        Text(
            text = set.name,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = set.tileCaption(),
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The heading over one era's worth of sets.
 *
 * Carries the era's own wordmark for the same reason the tiles do: the grid below it is a
 * wall of logos, and a header that is only text stops reading as the label for the thing
 * underneath it once you have scrolled past three of them.
 */
@Composable
fun SeriesBanner(
    name: String,
    caption: String,
    modifier: Modifier = Modifier,
    logoStem: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(30.dp)
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
            } else {
                Icon(AppIcons.Collections, null, Modifier.size(14.dp), tint = Ink.TextTertiary)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A set's wordmark at header size.
 *
 * Public where [LogoPanel] is not, because a set now has a screen of its own and the
 * wordmark is the first thing on it. The plate is drawn even when the catalog has no
 * logo, so a set without one is a blank plate rather than a header that has lost a
 * column and reflowed.
 */
@Composable
fun SetLogo(logoStem: String?, modifier: Modifier = Modifier, width: Dp = 86.dp) {
    Box(
        modifier
            .width(width)
            .height(width * 0.62f)
            .clip(AppShape.Medium)
            .background(Ink.SurfaceHigh)
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium)
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        val url = CardArt.logo(logoStem)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(AppIcons.Binders, null, Modifier.size(18.dp), tint = Ink.TextTertiary)
        }
    }
}

/**
 * The plate a wordmark is drawn on.
 *
 * Fitted, never cropped, and given the full width of the tile. A cropped wordmark is
 * unreadable, and an unreadable wordmark is worse than no wordmark at all -- it takes the
 * space the name would have used and gives nothing back.
 */
@Composable
private fun LogoPanel(
    logoStem: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(Ink.SurfaceHigh)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val url = CardArt.logo(logoStem)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(fallbackIcon, null, Modifier.size(20.dp), tint = Ink.TextTertiary)
        }
    }
}

/** "102 cards · 1999", with whichever halves the catalog actually knows. */
private fun RemoteSet.tileCaption(): String = listOfNotNull(
    officialCount?.let { "$it ${if (it == 1) "card" else "cards"}" },
    releaseYear,
).joinToString(" · ").ifEmpty { id }

/**
 * The colour a game is recognised by, near enough.
 *
 * Kept here rather than on [TcgGame] because it is a fact about how this app draws the
 * game, not about the game -- and written as a `when` rather than a map so that adding a
 * sixth game is a compile error here instead of a grey tile at runtime.
 */
private val TcgGame.accent: Color
    get() = when (this) {
        TcgGame.POKEMON -> Color(0xFFF2C14E)
        TcgGame.MAGIC -> Color(0xFFC08A5A)
        TcgGame.YUGIOH -> Color(0xFF9B7BE8)
        TcgGame.ONE_PIECE -> Color(0xFFE06A6E)
        TcgGame.LORCANA -> Color(0xFF4FB3C9)
    }
