package app.pocketful.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketful.domain.Binder
import app.pocketful.domain.BinderLayout
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerKind
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/**
 * How a piece of storage is pictured in a list.
 *
 * Both covers are the same size and sit in the same slot of the same row, so a binder and
 * a box read as two kinds of one thing rather than two unrelated features. What tells
 * them apart is the drawing: a binder shows its page grid, a container shows what kind of
 * container it is.
 */
private val CoverWidth = 52.dp
private val CoverShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 8.dp, bottomEnd = 8.dp)

/**
 * A miniature of the binder itself: spine on the left, and the actual page grid printed
 * on the cover. Showing the real row and column count means a 4-pocket and a 12-pocket
 * binder are told apart at a glance, before reading a word -- and a 4x3 page is visibly
 * not a 3x4 one.
 */
@Composable
fun BinderCover(binder: Binder, modifier: Modifier = Modifier, width: Dp = CoverWidth) {
    val spine = Color(binder.spineColor)

    Box(
        modifier
            .width(width)
            .aspectRatio(0.74f)
            .clip(CoverShape)
            .background(
                Brush.linearGradient(
                    listOf(spine.copy(alpha = 0.95f), spine.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.45f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), CoverShape),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.28f)))

        PageGrid(
            layout = binder.layout,
            pocketColor = Color.White.copy(alpha = 0.24f),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 11.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        )
    }
}

/**
 * The page shape on its own, at whatever size the caller gives it.
 *
 * Shared by the cover art and the layout picker so that the grid you choose in the editor
 * is drawn by the same code that later prints it on the shelf.
 */
@Composable
fun PageGrid(
    layout: BinderLayout,
    pocketColor: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 2.dp,
    pocketCorner: Dp = 1.dp,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(layout.rows) {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                repeat(layout.cols) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(pocketCorner))
                            .background(pocketColor),
                    )
                }
            }
        }
    }
}

/** A container's cover: the same footprint as a binder, showing its kind instead of a grid. */
@Composable
fun ContainerCover(container: Container, modifier: Modifier = Modifier, width: Dp = CoverWidth) {
    val tint = Color(container.color)

    Box(
        modifier
            .width(width)
            .aspectRatio(0.74f)
            .clip(CoverShape)
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.5f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), CoverShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = container.kind.icon,
            contentDescription = container.kind.label,
            modifier = Modifier.size(width * 0.46f),
            tint = Color.White.copy(alpha = 0.85f),
        )
    }
}

/** Kinds are a domain concept; which glyph draws them is not, so the mapping lives here. */
val ContainerKind.icon
    get() = when (this) {
        ContainerKind.DECK -> AppIcons.Cards
        // A slab and a toploader are the same picture: one card, one rigid case.
        ContainerKind.SLAB, ContainerKind.TOPLOADER -> AppIcons.Slab
        ContainerKind.BOX, ContainerKind.SEALED, ContainerKind.SHOEBOX -> AppIcons.Box
    }

/** The dashed "add another" row that closes each section of the collections list. */
@Composable
fun AddStorageRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Card)
            .border(1.dp, Ink.OutlineSoft, AppShape.Card)
            .tappable(pressScale = 0.985f, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(AppShape.Pill)
                .background(Ink.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Plus, null, Modifier.size(12.dp), tint = Ink.TextSecondary)
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
