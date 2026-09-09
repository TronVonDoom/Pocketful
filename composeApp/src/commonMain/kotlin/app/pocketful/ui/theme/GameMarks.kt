package app.pocketful.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import app.pocketful.domain.TcgGame

/**
 * A mark for each game, so a tile is recognised before it is read.
 *
 * These are *not* the publishers' logos, and the difference is deliberate. Shipping six
 * trademarked wordmarks into the binary is a licensing question rather than a layout one,
 * and the app has no right to any of them. What a grid actually needs is not the official
 * logo but a shape per game that is distinct from the other five at a glance -- so each
 * of these is an original emblem drawn from what the game is *about*: the ball you throw,
 * the handset you open packs on, the five-colour wheel, the pyramid, the hat, the drop of
 * ink.
 *
 * Drawn in the same language as [AppIcons] -- a 24x24 viewport, a 1.9 stroke, round caps
 * -- because they sit next to those icons on the same screens, and a set of marks in a
 * heavier or filled style would read as artwork someone pasted in rather than as part of
 * the app.
 */
object GameMarks {

    /** The ball: a circle, the band across it, and the catch at the centre. */
    val Pokeball: ImageVector by lazy {
        markIcon("mark-pokeball") {
            circle(12f, 12f, 8.4f)
            // Broken either side of the catch, so the middle of the mark is not three
            // shapes stacked on one line at 20dp.
            moveTo(3.6f, 12f)
            horizontalLineTo(8.6f)
            moveTo(15.4f, 12f)
            horizontalLineTo(20.4f)
            circle(12f, 12f, 2.8f)
        }
    }

    /**
     * The handheld: a phone, with a card held on its screen.
     *
     * Deliberately not a second ball. Pokémon TCG Pocket sits next to the printed game in
     * the same grid, and the whole point of listing them apart is that one is cardboard
     * and the other is not -- two variations on a Pokéball would say the opposite at the
     * exact glance the mark exists to serve. So the emblem is the device, and the card is
     * inside it rather than in your hand.
     */
    val Handheld: ImageVector by lazy {
        markIcon("mark-handheld", width = 1.8f) {
            roundedRect(left = 6.6f, top = 2.5f, right = 17.4f, bottom = 21.5f, radius = 2.3f)
            // The earpiece, which is the line that stops the outer shape reading as a
            // plain card standing on end.
            moveTo(10.6f, 5.4f)
            horizontalLineTo(13.4f)
            // The card on the screen, in the 5:7 the hobby actually prints at.
            roundedRect(left = 9.1f, top = 7.6f, right = 14.9f, bottom = 15.7f, radius = 1.1f)
        }
    }

    /**
     * The colour wheel: five points on a ring, chorded to each other.
     *
     * A pentagram rather than a pentagon, because the chords are the whole idea -- the
     * five colours of a mana pie are defined by which of the others each one opposes, and
     * an outline alone would say "five of something" without saying they are related.
     */
    val Pentacle: ImageVector by lazy {
        markIcon("mark-pentacle", width = 1.7f) {
            circle(12f, 12f, 9f)
            // Vertices at -90, -18, 54, 126 and 198 degrees on a radius of 6.4, joined
            // every second point.
            val points = listOf(
                12.00f to 5.60f,
                18.09f to 10.02f,
                15.76f to 17.18f,
                8.24f to 17.18f,
                5.91f to 10.02f,
            )
            moveTo(points[0].first, points[0].second)
            for (step in 1..5) {
                val (x, y) = points[(step * 2) % 5]
                lineTo(x, y)
            }
            close()
        }
    }

    /** The pyramid: a stepped triangle, for the game that has always been Egyptian. */
    val Pyramid: ImageVector by lazy {
        markIcon("mark-pyramid") {
            moveTo(12f, 3.6f)
            lineTo(21.2f, 19.6f)
            horizontalLineTo(2.8f)
            close()
            // The course line, and the two faces meeting above it. Without these the
            // silhouette is a triangle, which is a shape rather than a building.
            moveTo(6.5f, 13.2f)
            horizontalLineTo(17.5f)
            moveTo(12f, 3.6f)
            verticalLineTo(13.2f)
        }
    }

    /** The straw hat: crown, band, and a brim wider than either. */
    val StrawHat: ImageVector by lazy {
        markIcon("mark-strawhat") {
            // Crown, sat on the brim rather than floating over it.
            moveTo(7.6f, 14.1f)
            curveTo(7.6f, 8.4f, 9.5f, 5.8f, 12f, 5.8f)
            curveTo(14.5f, 5.8f, 16.4f, 8.4f, 16.4f, 14.1f)
            // The band.
            moveTo(7.7f, 11.7f)
            horizontalLineTo(16.3f)
            // The brim, bowed the way one is seen from just below the hat.
            moveTo(2.9f, 14.1f)
            curveTo(5.9f, 17.4f, 18.1f, 17.4f, 21.1f, 14.1f)
        }
    }

    /** The drop of ink. */
    val InkDrop: ImageVector by lazy {
        markIcon("mark-inkdrop") {
            moveTo(12f, 3.4f)
            curveTo(15.7f, 8.0f, 18.5f, 11.5f, 18.5f, 14.6f)
            curveTo(18.5f, 18.2f, 15.6f, 20.6f, 12f, 20.6f)
            curveTo(8.4f, 20.6f, 5.5f, 18.2f, 5.5f, 14.6f)
            curveTo(5.5f, 11.5f, 8.3f, 8.0f, 12f, 3.4f)
            close()
        }
    }
}

/**
 * The mark a game is drawn with.
 *
 * A `when` rather than a property on [TcgGame] for the same reason its accent colour is:
 * this is a fact about how this app draws the game, not about the game. Exhaustive on
 * purpose -- a seventh game should be a compile error here, not a blank tile at runtime.
 */
val TcgGame.mark: ImageVector
    get() = when (this) {
        TcgGame.POKEMON -> GameMarks.Pokeball
        TcgGame.POKEMON_POCKET -> GameMarks.Handheld
        TcgGame.MAGIC -> GameMarks.Pentacle
        TcgGame.YUGIOH -> GameMarks.Pyramid
        TcgGame.ONE_PIECE -> GameMarks.StrawHat
        TcgGame.LORCANA -> GameMarks.InkDrop
    }

/** Appends a rounded rectangle as its own subpath, corners drawn as quarter arcs. */
private fun PathBuilder.roundedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) {
    moveTo(left + radius, top)
    horizontalLineTo(right - radius)
    arcTo(radius, radius, 0f, false, true, right, top + radius)
    verticalLineTo(bottom - radius)
    arcTo(radius, radius, 0f, false, true, right - radius, bottom)
    horizontalLineTo(left + radius)
    arcTo(radius, radius, 0f, false, true, left, bottom - radius)
    verticalLineTo(top + radius)
    arcTo(radius, radius, 0f, false, true, left + radius, top)
    close()
}

/** Appends a full circle as its own subpath, built from two half arcs. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

private fun markIcon(
    name: String,
    width: Float = 1.9f,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}.build()
