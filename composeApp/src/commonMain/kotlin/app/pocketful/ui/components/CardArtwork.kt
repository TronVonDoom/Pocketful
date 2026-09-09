package app.pocketful.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import app.pocketful.data.CardArt
import app.pocketful.domain.PokemonType
import app.pocketful.state.LocalAppSettings
import app.pocketful.ui.theme.artBrush
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Which rendition of the art a surface wants. */
enum class ArtSize { Thumb, Full }

/**
 * Real card art, with the tinted placeholder underneath it.
 *
 * The gradient is not a spinner that gets swapped out -- it is drawn first and left
 * there, so a card whose art has not arrived yet, or which the catalog has no scan of,
 * reads as a slightly plain card rather than a hole in the page. That matters most on the
 * binder view, where sixteen pockets loading at once would otherwise flash sixteen empty
 * rectangles on every page turn.
 */
@Composable
fun CardArtwork(
    artStem: String?,
    type: PokemonType?,
    modifier: Modifier = Modifier,
    size: ArtSize = ArtSize.Thumb,
    /** Whether this printing is a foil, and so has light to catch. */
    holo: Boolean = false,
) {
    Box(modifier) {
        Box(Modifier.fillMaxSize().background(type.artBrush()))

        val url = when (size) {
            ArtSize.Thumb -> CardArt.thumb(artStem)
            ArtSize.Full -> CardArt.full(artStem)
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // Seeded from the art so that a page of sixteen foils does not flash in unison
        // -- sixteen synchronised sweeps read as one screen-wide strobe rather than as
        // sixteen cards catching the light.
        //
        // The switch is read here rather than at each call site. Five places draw a card,
        // the settings screen makes one promise about foil, and a tile that went on
        // shimmering after the toggle was turned off was a toggle that did not work.
        if (holo && LocalAppSettings.current.holoShimmer) HoloSheen(seed = artStem.hashCode())
    }
}

/**
 * Ambient holo sweep. This is the placeholder for the real effect, which should be driven
 * by the device gyroscope so tilting the phone moves the sheen the way a real card does.
 *
 * The whole trick is that the loop has to restart at a moment when there is nothing on
 * screen to restart. An earlier version ran the band between two offsets picked to look
 * about right, and ended its cycle with the band at full brightness across the card: the
 * sheen did not finish sweeping, it was cut off and re-dealt from the left. So the travel
 * here is derived rather than guessed -- far enough that the card is provably unlit at
 * both ends -- and the jump happens where there is nothing to see.
 *
 * The rest between sweeps is the other half of it. Foil catches the light when a card
 * moves, not continuously, and a sheen with no gap is a shimmer you stop seeing after ten
 * seconds and start finding irritating after twenty.
 *
 * Shared between the pocket and the list thumbnail so the same foil does not shimmer two
 * different ways depending on which screen it is being looked at from.
 *
 * @param seed anything stable about the card. Only its remainder is used, to spread the
 *   start of the sweep across the pockets on a page, and to fix where its flecks sit.
 */
@Composable
fun HoloSheen(seed: Int = 0) {
    val leadIn = seed.mod(STAGGER_STEPS) * (REST_MILLIS / STAGGER_STEPS)
    val flecks = remember(seed) { flecksFor(seed) }

    val transition = rememberInfiniteTransition(label = "holo")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = REST_MILLIS + SWEEP_MILLIS
                0f at 0
                // Linear, so the band crosses at the pace light actually rolls across a
                // tilted card. An eased sweep spends its slow parts off the edges and
                // whips through the visible middle, which turns a glide into a blink.
                0f at leadIn using LinearEasing
                1f at leadIn + SWEEP_MILLIS
                1f at REST_MILLIS + SWEEP_MILLIS
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "holoPhase",
    )

    Canvas(Modifier.fillMaxSize()) {
        // A pocket measures zero on the pass before it is laid out, and the travel below
        // divides by the band's width. There is nothing to light on a card with no area.
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val span = size.width * 1.4f
        // The band is swept diagonally, so how far it has to travel to clear the card is
        // not just its own width. A gradient running from (x, 0) to (x + span, height)
        // measures every pixel along that slant, which means the bottom-right corner is
        // still being lit long after the band has passed the right edge horizontally --
        // and it is exactly that overhang the old sweep cut through. This is the extra
        // distance the slant costs, and parking it at both ends is what makes the loop
        // seamless: at phase 0 and phase 1 no pixel of the card is above zero alpha.
        val overhang = size.height * size.height / span
        val travel = size.width + span + 2f * overhang
        val start = Offset(phase * travel - (span + overhang), 0f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
                start = start,
                end = Offset(start.x + span, size.height),
            ),
        )

        // How far along the band a point sits, in the same coordinate the gradient spaces
        // its colour stops along: 0 at the leading edge, 1 at the trailing one, 0.5 in the
        // bright core. This is the projection Compose does internally to paint the band,
        // repeated here because a fleck has to know how lit it is rather than just be
        // painted over -- and doing it any other way is how the glitter ends up sparkling
        // somewhere the light is not.
        val axisSquared = span * span + size.height * size.height
        val minSide = minOf(size.width, size.height)

        for (fleck in flecks) {
            val cx = fleck.x * size.width
            val cy = fleck.y * size.height
            val along = ((cx - start.x) * span + cy * size.height) / axisSquared
            // Cubed: a fleck should ignite as the core of the band arrives, not glow
            // faintly the whole time the band is anywhere on the card. Foil is specular.
            val reach = (1f - abs(along * 2f - 1f)).coerceAtLeast(0f)
            val glow = reach * reach * reach
            if (glow <= 0.01f) continue

            // ...and each on its own beat, so the flecks scintillate as the light crosses
            // rather than the card frosting over evenly and clearing again.
            val twinkle = 0.45f + 0.55f * sin((phase * fleck.rate + fleck.offset) * TAU)
            val alpha = (glow * twinkle * fleck.strength).coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue

            val radius = fleck.radius * minSide
            val centre = Offset(cx, cy)
            // Halo, core, arms, in that order of importance. A bright round core inside a
            // soft halo is what a point of light looks like; the cross is the four-point
            // flare around it and stays thin and short, because arms drawn as long as the
            // halo is wide stop being a glint and become a plus sign.
            drawCircle(Color.White.copy(alpha = alpha * 0.20f), radius * 1.7f, centre)
            drawCircle(Color.White.copy(alpha = alpha), radius * 0.42f, centre)
            drawLine(
                color = Color.White.copy(alpha = alpha * 0.8f),
                start = Offset(cx - radius, cy),
                end = Offset(cx + radius, cy),
                strokeWidth = radius * 0.22f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = alpha * 0.8f),
                start = Offset(cx, cy - radius),
                end = Offset(cx, cy + radius),
                strokeWidth = radius * 0.22f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** One speck of foil: where it sits, how big it is, and on what beat it flickers. */
private class Fleck(
    val x: Float,
    val y: Float,
    val radius: Float,
    val strength: Float,
    val rate: Float,
    val offset: Float,
)

/**
 * A card's glitter, fixed for that card.
 *
 * The sweep says a card is foil; the glitter says what kind. A flat sheen crossing a flat
 * picture is a light effect applied *to* an image, and the thing that turns it into a
 * surface is the specks that catch that light at different moments as it goes past. So
 * these are lit by the band's own position rather than animated on their own clock: a
 * fleck the light has not reached is dark, which is the whole reason it reads as sitting
 * on the card rather than floating above it.
 *
 * Seeded rather than random, so a pocket keeps its own constellation across page turns
 * and recompositions. Flecks that jumped every time the binder redrew would read as noise
 * on the screen instead of texture on the card. Held off the outer few percent so nothing
 * sparkles on the border.
 */
private fun flecksFor(seed: Int): List<Fleck> {
    val random = Random(seed)
    return List(FLECK_COUNT) {
        Fleck(
            x = 0.07f + random.nextFloat() * 0.86f,
            y = 0.07f + random.nextFloat() * 0.86f,
            // A fraction of the card's short side, so a fleck is the same size relative to
            // the card in a 60dp pocket and on a full-screen scan. Small: a speck that
            // measures a tenth of the card is not a speck, it is a symbol drawn on it.
            radius = 0.010f + random.nextFloat() * 0.018f,
            strength = 0.5f + random.nextFloat() * 0.5f,
            // Two to five flickers across a sweep. Slower and a fleck fades up and down
            // once, which is a smudge; faster and the card fizzes.
            rate = 2f + random.nextFloat() * 3f,
            offset = random.nextFloat(),
        )
    }
}

/**
 * How many specks a card carries.
 *
 * Enough that several are lit at once as the band goes past -- one or two at a time reads
 * as a mark on the card rather than as glitter -- and few enough that a nine-pocket page
 * is drawing a couple of hundred per frame, most of which are skipped before any drawing
 * happens because at any moment the band is only over part of the card.
 */
private const val FLECK_COUNT = 22

/** One full turn, for the per-fleck flicker. */
private const val TAU = 6.2831855f

/**
 * How long the band takes to cross.
 *
 * Set so the part of that crossing which is actually over the card lasts a little over
 * two seconds, which is the pace the sweep had before it was cut short -- the timing was
 * never the problem with it.
 */
private const val SWEEP_MILLIS = 2800

/**
 * How long it waits, parked off the edge, before crossing again.
 *
 * Short. Foil catches the light when a card moves rather than continuously, so a beat
 * between sweeps reads more like a card than an unbroken crawl does -- but a long pause
 * on a page of sixteen pockets just looks like the animation has stopped.
 */
private const val REST_MILLIS = 1400

/** How many distinct start times the pockets on a page are spread across. */
private const val STAGGER_STEPS = 6
