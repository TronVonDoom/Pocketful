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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import app.pocketful.data.CardArt
import app.pocketful.domain.PokemonType
import app.pocketful.ui.theme.artBrush
import coil3.compose.AsyncImage

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
    shimmer: Boolean = false,
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
        if (shimmer) HoloSheen(seed = artStem.hashCode())
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
 *   start of the sweep across the pockets on a page.
 */
@Composable
fun HoloSheen(seed: Int = 0) {
    val leadIn = seed.mod(STAGGER_STEPS) * (REST_MILLIS / STAGGER_STEPS)

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
    }
}

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
