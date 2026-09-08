package app.pocketful.ui.launch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketful.AppVersion
import app.pocketful.resources.Res
import app.pocketful.resources.app_icon
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.headerBloom
import org.jetbrains.compose.resources.painterResource

/**
 * The first screen, and the only one whose job is to be left.
 *
 * It exists because the app has real work to do before Home can tell the truth -- the set
 * index, the price match, the artwork behind a hundred tiles -- and that work used to
 * happen in front of the user, on a screen that looked finished and then kept changing
 * under them. Given somewhere to happen instead, the same wait reads as a launch.
 *
 * The whole composition is the launcher icon at rest. That continuity is the point: the
 * window background already shows this mark before a single frame of Compose has run
 * (see `drawable/splash_ground.xml`), so the handover from system splash to app is the
 * same image in the same place on the same black, and the only thing that appears is the
 * type under it. Nothing slides, nothing pops, nothing flashes white.
 *
 * Every moving part is deliberately slow. A launch screen is looked at for a second and a
 * half; anything on it that spins quickly makes that second and a half feel like it is
 * taking effort.
 */
@Composable
fun LoadingScreen(
    progress: Float,
    status: String,
    summary: String?,
    modifier: Modifier = Modifier,
) {
    val breathing = rememberInfiniteTransition(label = "launch")

    // A very shallow, very slow scale. Enough that the screen is not frozen while the
    // network is quiet, small enough that nobody can point at it and say what it is doing.
    val lift by breathing.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lift",
    )

    val glow by breathing.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    // The bar is animated toward its target rather than set to it. The steps behind it
    // finish in jumps -- a catalog index either has arrived or has not -- and a bar that
    // teleports reads as a bar that is broken.
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420, easing = LinearEasing),
        label = "progress",
    )

    Box(
        modifier.fillMaxSize().background(Ink.Background),
        contentAlignment = Alignment.Center,
    ) {
        // The same two lights as a screen header, at the middle of the screen instead of
        // the top of it, so the icon has something to sit in rather than on.
        Box(
            Modifier
                .size(460.dp)
                .offset(y = (-40).dp)
                .background(headerBloom(Ink.Accent, strength = glow * 0.34f), CircleShape),
        )
        Box(
            Modifier
                .size(360.dp)
                .offset(x = 60.dp, y = 90.dp)
                .background(headerBloom(Ink.Aura, strength = glow * 0.22f), CircleShape),
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(148.dp).scale(lift),
            )

            Spacer(Modifier.height(22.dp))
            Wordmark()

            Spacer(Modifier.height(9.dp))
            Text(
                text = "Every card, in its pocket",
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(AppShape.Pill)
                    .background(Ink.SurfaceRaised),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(shown)
                        .height(4.dp)
                        .clip(AppShape.Pill)
                        // Gold at the leading edge, so the bar has a head rather than
                        // being a rectangle that gets wider.
                        .background(Brush.horizontalGradient(listOf(Ink.Accent, Ink.Gold))),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Crossfaded rather than replaced. The steps are short and the labels are
            // long, and text that hard-cuts four times in a second and a half is the one
            // thing on this screen that would draw the eye for the wrong reason.
            AnimatedContent(
                targetState = summary ?: status,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "status",
            ) { line ->
                Text(
                    text = line,
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(
            text = "v${AppVersion.NAME}",
            color = Ink.TextDisabled,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
        )
    }
}

/**
 * The name, set as a wordmark rather than as a heading.
 *
 * Split where the pun is. "Pocket" is the object the app is about and "ful" is the joke,
 * and giving the second half the same gradient the progress bar runs is what ties the two
 * pieces of type on this screen into one object.
 *
 * A gradient rather than a flat accent, because the icon above it is the most saturated
 * thing in the app and a solid blue syllable next to it reads as a hyperlink.
 */
@Composable
private fun Wordmark() {
    val style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 1.sp)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
        Text(text = "Pocket", color = Ink.TextPrimary, style = style)
        Text(
            text = "ful",
            style = style.copy(brush = Brush.horizontalGradient(listOf(Ink.Accent, Ink.Gold))),
        )
    }
}
