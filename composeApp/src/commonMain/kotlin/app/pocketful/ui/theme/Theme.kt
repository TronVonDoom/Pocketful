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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketful.domain.PokemonType

/**
 * The cards are the only saturated thing on screen. Everything else is a near-neutral
 * ground that gets out of their way.
 *
 * Surfaces climb in four steps rather than two. With sheets, an island bar and inset
 * wells all on screen at once, three greys were not enough to keep the stacking order
 * legible without leaning on borders for everything.
 */
object Ink {
    val Background = Color(0xFF07080B)
    val Surface = Color(0xFF12141A)
    val SurfaceRaised = Color(0xFF191C24)
    val SurfaceHigh = Color(0xFF22262F)
    val PocketWell = Color(0xFF0C0E13)

    val Outline = Color(0xFF2A3040)
    val OutlineSoft = Color(0xFF1C2029)
    val OutlineFaint = Color(0xFF14171E)

    val TextPrimary = Color(0xFFF3F5F9)
    val TextSecondary = Color(0xFF98A2B3)
    val TextTertiary = Color(0xFF5F6879)
    val TextDisabled = Color(0xFF3C4351)

    val Gold = Color(0xFFE3B45C)
    val Gain = Color(0xFF4ADE80)
    val Loss = Color(0xFFF87171)
    val Accent = Color(0xFF6E8BFF)
    val AccentSoft = Color(0xFF2A3560)
    val Wanted = Color(0xFF8B7BE8)

    /** The second colour in a header bloom. Never used for type -- only for light. */
    val Aura = Color(0xFF7C5CE0)

    val Scrim = Color(0xFF000000)

    /** The island bar and sheets sit on this: opaque enough to read, dark enough to float. */
    val Glass = Color(0xFF15181F)
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
    0f to color.copy(alpha = 0.16f),
    0.34f to color.copy(alpha = 0.06f),
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

object AppShape {
    val Chip = RoundedCornerShape(6.dp)
    val Pill = RoundedCornerShape(50)
    val Small = RoundedCornerShape(10.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(18.dp)
    val Large = RoundedCornerShape(24.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

private val Shapes = Shapes(
    extraSmall = AppShape.Chip,
    small = AppShape.Small,
    medium = AppShape.Medium,
    large = AppShape.Card,
    extraLarge = AppShape.Large,
)

/**
 * Display sizes are tracked tighter than the Material defaults. Large currency figures
 * are the most repeated element in the app and the default tracking makes them sprawl.
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val DarkScheme = darkColorScheme(
    primary = Ink.Accent,
    onPrimary = Color.White,
    primaryContainer = Ink.AccentSoft,
    onPrimaryContainer = Ink.TextPrimary,
    secondary = Ink.Gold,
    onSecondary = Color.Black,
    background = Ink.Background,
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
