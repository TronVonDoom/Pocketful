package app.pocketful.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A hand-rolled, stroke-based icon set.
 *
 * Drawn here rather than pulled from material-icons-extended for two reasons: that
 * artifact ships around 1500 icons to use a dozen, and its filled style fights the thin,
 * outlined language the rest of the app is drawn in. Every glyph below is a 24x24
 * viewport with a 1.9 stroke, so they optically match at any size.
 */
object AppIcons {

    val Home: ImageVector by lazy {
        strokeIcon("home") {
            moveTo(3.5f, 10.2f)
            lineTo(12f, 3.5f)
            lineTo(20.5f, 10.2f)
            verticalLineTo(19.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
            horizontalLineTo(5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
            close()
            // The door, drawn as its own subpath so it reads at 17dp.
            moveTo(9.5f, 21f)
            verticalLineTo(14f)
            horizontalLineTo(14.5f)
            verticalLineTo(21f)
        }
    }

    /**
     * Collections: a binder edge and a box, because the section now holds both. Drawn as
     * two overlapping objects rather than a single container, so it does not read as
     * "storage box" and quietly demote the binders inside it.
     */
    val Collections: ImageVector by lazy {
        strokeIcon("collections") {
            // Binder standing behind, spine to the left.
            moveTo(4f, 3f)
            horizontalLineTo(13.5f)
            verticalLineTo(12f)
            moveTo(6.2f, 3f)
            verticalLineTo(12f)

            // Box in front, lid seam across.
            moveTo(3f, 13.5f)
            horizontalLineTo(21f)
            verticalLineTo(20.5f)
            arcToRelative(0.5f, 0.5f, 0f, false, true, -0.5f, 0.5f)
            horizontalLineTo(3.5f)
            arcToRelative(0.5f, 0.5f, 0f, false, true, -0.5f, -0.5f)
            close()
            moveTo(9.5f, 13.5f)
            verticalLineTo(16.5f)
            horizontalLineTo(14.5f)
            verticalLineTo(13.5f)
        }
    }

    /** One box on its own: a container in a list, or the button that makes a new one. */
    val Box: ImageVector by lazy {
        strokeIcon("box") {
            moveTo(3.5f, 7.5f)
            lineTo(12f, 3.5f)
            lineTo(20.5f, 7.5f)
            verticalLineTo(17f)
            lineTo(12f, 21f)
            lineTo(3.5f, 17f)
            close()
            moveTo(3.5f, 7.5f)
            lineTo(12f, 11.5f)
            lineTo(20.5f, 7.5f)
            moveTo(12f, 11.5f)
            verticalLineTo(21f)
        }
    }

    /** A card sealed in a rigid case: a graded slab, or a toploader. */
    val Slab: ImageVector by lazy {
        strokeIcon("slab") {
            moveTo(6.5f, 2.5f)
            horizontalLineTo(17.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
            verticalLineTo(20f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
            horizontalLineTo(6.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5f, 20f)
            verticalLineTo(4f)
            arcTo(1.5f, 1.5f, 0f, false, true, 6.5f, 2.5f)
            close()
            // The grading label across the top, then the card under it.
            moveTo(8f, 6.5f)
            horizontalLineTo(16f)
            moveTo(8f, 9.5f)
            horizontalLineTo(16f)
            verticalLineTo(18f)
            horizontalLineTo(8f)
            close()
        }
    }

    val Binders: ImageVector by lazy {
        strokeIcon("binders") {
            moveTo(4f, 19.5f)
            verticalLineTo(4.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 6.5f, 2f)
            horizontalLineTo(20f)
            verticalLineTo(22f)
            horizontalLineTo(6.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, 0f, -5f)
            horizontalLineTo(20f)
        }
    }

    val Cards: ImageVector by lazy {
        strokeIcon("cards") {
            // Front card, drawn whole.
            moveTo(5f, 8f)
            horizontalLineTo(13f)
            arcTo(2f, 2f, 0f, false, true, 15f, 10f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 13f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            verticalLineTo(10f)
            arcTo(2f, 2f, 0f, false, true, 5f, 8f)
            close()
            // The card behind it: only the part that would actually peek out.
            moveTo(7f, 8f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 9f, 3f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 21f, 5f)
            verticalLineTo(15f)
            arcTo(2f, 2f, 0f, false, true, 19f, 17f)
            horizontalLineTo(15f)
        }
    }

    val Stats: ImageVector by lazy {
        strokeIcon("stats") {
            moveTo(3.5f, 20.5f)
            horizontalLineTo(20.5f)
            moveTo(7.5f, 20.5f)
            verticalLineTo(14f)
            moveTo(12f, 20.5f)
            verticalLineTo(9.5f)
            moveTo(16.5f, 20.5f)
            verticalLineTo(16.5f)
        }
    }

    val Settings: ImageVector by lazy {
        strokeIcon("settings") {
            moveTo(3.5f, 7f)
            horizontalLineTo(7.5f)
            moveTo(12.5f, 7f)
            horizontalLineTo(20.5f)
            circle(10f, 7f, 2.5f)

            moveTo(3.5f, 12f)
            horizontalLineTo(11.5f)
            moveTo(16.5f, 12f)
            horizontalLineTo(20.5f)
            circle(14f, 12f, 2.5f)

            moveTo(3.5f, 17f)
            horizontalLineTo(5.5f)
            moveTo(10.5f, 17f)
            horizontalLineTo(20.5f)
            circle(8f, 17f, 2.5f)
        }
    }

    val Plus: ImageVector by lazy {
        strokeIcon("plus") {
            moveTo(12f, 5f)
            verticalLineTo(19f)
            moveTo(5f, 12f)
            horizontalLineTo(19f)
        }
    }

    /** An update on its way down. Arrow into a tray, so it reads as *install*, not *save*. */
    val Download: ImageVector by lazy {
        strokeIcon("download") {
            moveTo(12f, 3.5f)
            verticalLineTo(14.5f)
            moveTo(7.6f, 10.4f)
            lineTo(12f, 14.8f)
            lineTo(16.4f, 10.4f)
            moveTo(4.5f, 16.4f)
            verticalLineTo(18.4f)
            curveTo(4.5f, 19.5f, 5.4f, 20.5f, 6.5f, 20.5f)
            horizontalLineTo(17.5f)
            curveTo(18.6f, 20.5f, 19.5f, 19.5f, 19.5f, 18.4f)
            verticalLineTo(16.4f)
        }
    }

    val Minus: ImageVector by lazy {
        strokeIcon("minus") {
            moveTo(5f, 12f)
            horizontalLineTo(19f)
        }
    }

    val Search: ImageVector by lazy {
        strokeIcon("search") {
            circle(11f, 11f, 7f)
            moveTo(16.6f, 16.6f)
            lineTo(20.5f, 20.5f)
        }
    }

    val Close: ImageVector by lazy {
        strokeIcon("close") {
            moveTo(18f, 6f)
            lineTo(6f, 18f)
            moveTo(6f, 6f)
            lineTo(18f, 18f)
        }
    }

    /** Four arrows from a centre: pick this up and put it somewhere else. */
    val Move: ImageVector by lazy {
        strokeIcon("move") {
            moveTo(12f, 3.5f)
            lineTo(12f, 20.5f)
            moveTo(3.5f, 12f)
            lineTo(20.5f, 12f)
            moveTo(9.2f, 6.3f)
            lineTo(12f, 3.5f)
            lineTo(14.8f, 6.3f)
            moveTo(9.2f, 17.7f)
            lineTo(12f, 20.5f)
            lineTo(14.8f, 17.7f)
            moveTo(6.3f, 9.2f)
            lineTo(3.5f, 12f)
            lineTo(6.3f, 14.8f)
            moveTo(17.7f, 9.2f)
            lineTo(20.5f, 12f)
            lineTo(17.7f, 14.8f)
        }
    }

    val Check: ImageVector by lazy {
        strokeIcon("check") {
            moveTo(20f, 6.5f)
            lineTo(9.2f, 17.3f)
            lineTo(4f, 12.1f)
        }
    }

    val ChevronLeft: ImageVector by lazy {
        strokeIcon("chevronLeft") {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        strokeIcon("chevronRight") {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }

    val Trash: ImageVector by lazy {
        strokeIcon("trash") {
            moveTo(3.5f, 6f)
            horizontalLineTo(20.5f)
            moveTo(8f, 6f)
            verticalLineTo(4f)
            arcTo(1f, 1f, 0f, false, true, 9f, 3f)
            horizontalLineTo(15f)
            arcTo(1f, 1f, 0f, false, true, 16f, 4f)
            verticalLineTo(6f)
            moveTo(18.5f, 6f)
            lineTo(17.6f, 20f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 1.9f)
            horizontalLineTo(8.4f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -1.9f)
            lineTo(5.5f, 6f)
        }
    }

    val Edit: ImageVector by lazy {
        strokeIcon("edit") {
            moveTo(12.5f, 20.5f)
            horizontalLineTo(21f)
            moveTo(16.6f, 3.6f)
            arcToRelative(2.12f, 2.12f, 0f, false, true, 3f, 3f)
            lineTo(7.4f, 18.8f)
            lineTo(3f, 20f)
            lineTo(4.2f, 15.6f)
            close()
        }
    }

    /** A card you are hunting: a reticle, not a heart. This is a want-list, not a wishlist. */
    val Target: ImageVector by lazy {
        strokeIcon("target") {
            circle(12f, 12f, 8.5f)
            circle(12f, 12f, 3.5f)
        }
    }

    /**
     * Foil catching the light: a four-point star with the concave arms glitter has.
     *
     * Not the usual five-point star -- that one means "favourite" on every phone ever
     * made, and the button it sits on is not about liking a set.
     */
    val Sparkle: ImageVector by lazy {
        strokeIcon("sparkle") {
            moveTo(12f, 2.5f)
            quadTo(13f, 11f, 21.5f, 12f)
            quadTo(13f, 13f, 12f, 21.5f)
            quadTo(11f, 13f, 2.5f, 12f)
            quadTo(11f, 11f, 12f, 2.5f)
            close()
        }
    }

    /**
     * Trade: two cards passing in opposite directions.
     *
     * Not the usual pair of circular arrows -- that glyph means "sync" or "refresh"
     * everywhere else on a phone, and this screen is about giving a card away, which is
     * not an operation you want confused with reloading prices.
     */
    val Trade: ImageVector by lazy {
        strokeIcon("trade") {
            // Upper track, heading right.
            moveTo(3.5f, 8.5f)
            horizontalLineTo(17f)
            moveTo(13.5f, 5f)
            lineTo(17f, 8.5f)
            lineTo(13.5f, 12f)
            // Lower track, heading back.
            moveTo(20.5f, 15.5f)
            horizontalLineTo(7f)
            moveTo(10.5f, 12f)
            lineTo(7f, 15.5f)
            lineTo(10.5f, 19f)
        }
    }
}

/** Appends a full circle as its own subpath, built from two half arcs. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

private fun strokeIcon(
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
