package app.pocketful.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.pocketful.domain.PokemonType

/**
 * The colour of the application.
 *
 * Organised as a ladder rather than a list. Every surface knows which step it is on, and a
 * component picks its ground by asking how high it is stacked instead of by naming a grey
 * it liked -- which is what kept happening, and why a sheet, a panel inside that sheet and
 * a chip inside that panel could all end up the same colour.
 *
 * The rungs:
 *
 *   Canvas    the window itself; nothing is behind it
 *   Surface   a panel, a tile, a list row -- the default place content sits
 *   Raised    a control on a panel: a field, a button, a chip
 *   High      a control on a control: a stepper's buttons, a chip on a field
 *   Top       the highest thing that is still a surface; rare, and a sign to stop
 *
 * Card art is the only saturated thing on screen by design. Everything here is within a few
 * points of neutral, cool rather than warm, so an illustration put on top of it is the
 * brightest object in view without having to fight for it.
 */
object Ink {

    // ------------------------------------------------------------- the ladder

    /** The window. Not pure black -- a slight blue cast stops OLED smearing on scroll. */
    val Canvas = Color(0xFF08090D)

    /** Where content lives. */
    val Surface = Color(0xFF101218)

    /** A control on a panel. */
    val SurfaceRaised = Color(0xFF171A22)

    /** A control on a control. */
    val SurfaceHigh = Color(0xFF20242E)

    /** The top rung. If something needs to go above this, it wants a shadow instead. */
    val SurfaceTop = Color(0xFF2A2F3C)

    /**
     * The floating layer: the navigation dock, the selection bar, a sheet.
     *
     * Lighter than [Surface] rather than darker, because these sit *over* scrolling content
     * and a floating panel that is darker than the page reads as a hole cut in it.
     */
    val Glass = Color(0xFF161A23)

    /** Deliberately below the canvas: an inset well, an empty pocket, a recessed track. */
    val Well = Color(0xFF04050A)

    /** Retained name for the binder page's empty pockets. */
    val PocketWell = Well

    /** Kept so screens written against the old name still read correctly. */
    val Background = Canvas

    // -------------------------------------------------------------- the edges

    /** A real border, on something that needs to be told apart from its neighbour. */
    val Outline = Color(0xFF333A4A)

    /** The usual edge: present, but not a line you read. */
    val OutlineSoft = Color(0xFF222733)

    /** Barely there. The edge of a panel on the canvas. */
    val OutlineFaint = Color(0xFF181C25)

    // --------------------------------------------------------------- the type

    /** Names, figures, anything being read. */
    val TextPrimary = Color(0xFFF5F7FB)

    /** Supporting text that is still meant to be read. */
    val TextSecondary = Color(0xFFA6B0C2)

    /** Captions, units, labels under a figure. */
    val TextTertiary = Color(0xFF727C90)

    /** Off. Not a colour for text anyone needs. */
    val TextDisabled = Color(0xFF474F60)

    // ----------------------------------------------------------- the meanings

    /**
     * The application's own colour. Actions, selection, the current tab.
     *
     * Reserved: if everything is accented then nothing is, so this marks what the user can
     * do, never what the app merely wants to point at.
     */
    val Accent = Color(0xFF6D7BFF)

    /** The same, lit -- for a pressed state or a glyph on a dark accent ground. */
    val AccentBright = Color(0xFF93A0FF)

    /** An accent surface: a selected chip, the fill behind a current tab. */
    val AccentSoft = Color(0xFF232A52)

    /** Money. Every figure that is an amount of it, and nothing else. */
    val Gold = Color(0xFFEFB95F)

    /** Value going up, and anything on the table in a trade. */
    val Gain = Color(0xFF3FD98A)

    /** Value going down, and anything about to be destroyed. */
    val Loss = Color(0xFFFF6B6B)

    /** A card you are hunting. Violet everywhere, on every screen, without exception. */
    val Wanted = Color(0xFFA78BFA)

    /** Foil catching light. Cool where gold is warm, so a holo mark never reads as a price. */
    val Foil = Color(0xFF67E8F9)

    /** The second colour in a header bloom. Never used for type -- only for light. */
    val Aura = Color(0xFF8B5CF6)

    /** Behind a modal. */
    val Scrim = Color(0xFF000000)
}

/** Spine colours offered when creating a binder. Named so the picker can label them. */
data class SpineSwatch(val label: String, val value: Long)

val SpineSwatches = listOf(
    SpineSwatch("Ember", 0xFFE0483B),
    SpineSwatch("Amber", 0xFFE08A2E),
    SpineSwatch("Bronze", 0xFFC08A4A),
    SpineSwatch("Moss", 0xFF4F9E5C),
    SpineSwatch("Teal", 0xFF2E9C97),
    SpineSwatch("Ocean", 0xFF3B82F6),
    SpineSwatch("Indigo", 0xFF6D5BD0),
    SpineSwatch("Orchid", 0xFFB05FC4),
    SpineSwatch("Rose", 0xFFD9527C),
    SpineSwatch("Slate", 0xFF64748B),
)

/** Classic type identities, desaturated just enough to sit on a near-black ground. */
val PokemonType?.tint: Color
    get() = when (this) {
        PokemonType.GRASS -> Color(0xFF5FA85A)
        PokemonType.FIRE -> Color(0xFFE0633C)
        PokemonType.WATER -> Color(0xFF3D8FD1)
        PokemonType.LIGHTNING -> Color(0xFFD9B23A)
        PokemonType.PSYCHIC -> Color(0xFFA361C4)
        PokemonType.FIGHTING -> Color(0xFFB6613C)
        PokemonType.DARKNESS -> Color(0xFF44506B)
        PokemonType.METAL -> Color(0xFF7C8794)
        PokemonType.FAIRY -> Color(0xFFD96D9C)
        PokemonType.DRAGON -> Color(0xFF9A7B3F)
        PokemonType.COLORLESS -> Color(0xFF8B8F99)
        null -> Color(0xFF6B7280)
    }

val PokemonType?.label: String
    get() = when (this) {
        null -> "Colorless"
        else -> name.lowercase().replaceFirstChar { it.uppercase() }
    }

/**
 * Stand-in for real card art: a plausible frame tinted by the card's type. The dark stop
 * is weighted to the bottom so the caption scrim has something to sit against.
 */
fun PokemonType?.artBrush(): Brush = Brush.linearGradient(
    listOf(
        tint.copy(alpha = 0.95f),
        tint.copy(alpha = 0.62f),
        tint.copy(alpha = 0.30f),
        Color.Black.copy(alpha = 0.72f),
    ),
)

/**
 * The wash behind a screen header. Barely visible, but it stops the top reading as flat.
 *
 * Three stops rather than two. A straight fade to transparent puts its steepest change
 * right under the headline, which reads as a band with an edge; holding a low value
 * through the middle of the run moves that edge off the type and into empty space.
 */
fun headerGlow(color: Color): Brush = Brush.verticalGradient(
    0f to color.copy(alpha = 0.18f),
    0.34f to color.copy(alpha = 0.07f),
    1f to Color.Transparent,
)

/**
 * A soft bloom of colour, for the corner of a header.
 *
 * Drawn as a radial rather than another vertical fade because the two are doing different
 * jobs: the wash tints the whole top of the screen, and this gives that tint a source. A
 * gradient with no origin reads as a coloured rectangle no matter how faint it is.
 */
fun headerBloom(color: Color, strength: Float = 0.20f): Brush = Brush.radialGradient(
    0f to color.copy(alpha = strength),
    0.55f to color.copy(alpha = strength * 0.35f),
    1f to Color.Transparent,
)

/**
 * A surface that is lit from above.
 *
 * Panels used to be filled flat, which is honest and also means a 200dp-tall card is the
 * same colour top and bottom and reads as a painted rectangle. One step of lightness across
 * the height is enough to say the thing has a surface without anybody noticing a gradient.
 */
fun litSurface(base: Color = Ink.Surface): Brush = Brush.verticalGradient(
    listOf(base.copy(alpha = 1f), base.copy(alpha = 1f).darkenTowards(Ink.Canvas, 0.35f)),
)

private fun Color.darkenTowards(target: Color, fraction: Float): Color = Color(
    red = red + (target.red - red) * fraction,
    green = green + (target.green - green) * fraction,
    blue = blue + (target.blue - blue) * fraction,
    alpha = alpha,
)

/**
 * The shapes, by size band. See [Radius] -- this is that scale, expressed the way Compose
 * wants it, so `MaterialTheme.shapes` and the app's own names cannot drift apart.
 */
object AppShape {
    val Chip = RoundedCornerShape(Radius.xs)
    val Pill = RoundedCornerShape(Radius.pill)
    val Small = RoundedCornerShape(Radius.sm)
    val Medium = RoundedCornerShape(Radius.md)
    val Card = RoundedCornerShape(Radius.lg)
    val Large = RoundedCornerShape(Radius.xl)
    val Sheet = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)
}

private val Shapes = Shapes(
    extraSmall = AppShape.Chip,
    small = AppShape.Small,
    medium = AppShape.Medium,
    large = AppShape.Card,
    extraLarge = AppShape.Large,
)

/**
 * The type scale.
 *
 * Built around one fact: the most repeated element in this app is a currency figure, and
 * the second is a card's name truncated to fit a tile. So the display sizes are tracked
 * tight -- default tracking makes a four-figure sum sprawl across a phone -- and the small
 * label sizes are tracked loose, because 10sp uppercase set solid is unreadable.
 *
 * Six weights would be five too many at these sizes. Everything here is Medium, SemiBold or
 * Bold, and the step between them is doing the work that a seventh size would otherwise.
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp),
    displayMedium = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    // The tracked micro-label: section headings, units under a figure, the eyebrow above a
    // title. Uppercase at this size needs the extra tracking or the letters collide.
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

private val DarkScheme = darkColorScheme(
    primary = Ink.Accent,
    onPrimary = Color.White,
    primaryContainer = Ink.AccentSoft,
    onPrimaryContainer = Ink.TextPrimary,
    secondary = Ink.Gold,
    onSecondary = Color.Black,
    background = Ink.Canvas,
    onBackground = Ink.TextPrimary,
    surface = Ink.Surface,
    onSurface = Ink.TextPrimary,
    surfaceVariant = Ink.SurfaceRaised,
    onSurfaceVariant = Ink.TextSecondary,
    outline = Ink.Outline,
    outlineVariant = Ink.OutlineSoft,
    error = Ink.Loss,
    onError = Color.Black,
    scrim = Ink.Scrim,
)

/**
 * Deliberately dark regardless of system theme. A light binder page washes out card art,
 * and the whole point of the page view is that the art carries it.
 */
@Composable
fun PocketfulTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        shapes = Shapes,
        content = content,
    )
}
