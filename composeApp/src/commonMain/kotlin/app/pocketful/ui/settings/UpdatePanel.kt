package app.pocketful.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketful.AppVersion
import app.pocketful.data.AppUpdates
import app.pocketful.data.Release
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.ProgressTrack
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink
import app.pocketful.update.UpdateInstaller
import app.pocketful.update.rememberUpdateInstaller
import kotlinx.coroutines.launch

/**
 * Updating the app from the repository it is published from.
 *
 * The app is not on a store, so nothing checks on its behalf. This is that missing piece:
 * it asks GitHub for the newest release, compares the tag against the version this build
 * was cut at, and -- if there is something newer with an APK on it -- downloads it and
 * hands it to the system installer.
 *
 * Everything the user can be told, they are told. A check that finds nothing says so
 * rather than going quiet; a release that exists but ships no APK offers the release page
 * instead of a dead button; a download that fails prints why and leaves the button where
 * it was. The one thing that cannot be explained from in here is a signature mismatch --
 * that is decided by whichever key the installed build was signed with, and it arrives as
 * the system installer refusing.
 */
@Composable
fun UpdatePanel(modifier: Modifier = Modifier) {
    val updates = remember { AppUpdates() }
    val installer = rememberUpdateInstaller()
    val links = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    Panel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailRow("Installed", "v${AppVersion.NAME}")
            DetailRow("Channel", "${AppUpdates.OWNER}/${AppUpdates.REPO}")
            (state as? UpdateState.Found)?.release?.let { release ->
                DetailRow("Latest", release.tag)
                release.publishedAt?.let { DetailRow("Published", it) }
                release.asset?.let { DetailRow("Download", it.sizeLabel) }
            }
        }

        Spacer(Modifier.height(14.dp))

        when (val current = state) {
            UpdateState.Idle -> {
                Explainer(
                    "Checks the project's GitHub releases for a build newer than this one " +
                        "and installs it. Nothing is downloaded until you ask for it.",
                )
                Spacer(Modifier.height(12.dp))
                AppOutlineButton(
                    label = "Check for updates",
                    onClick = {
                        state = UpdateState.Checking
                        scope.launch { state = check(updates) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UpdateState.Checking -> Status("Asking GitHub for the latest release…")

            UpdateState.UpToDate -> {
                Status("Pocketful is up to date.", Ink.Gain)
                Spacer(Modifier.height(12.dp))
                AppOutlineButton(
                    label = "Check again",
                    onClick = {
                        state = UpdateState.Checking
                        scope.launch { state = check(updates) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is UpdateState.Found -> {
                val release = current.release
                Text(
                    text = release.name,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                release.notes?.let { notes ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // Release notes are markdown written for a web page. Six lines of
                        // it is a summary; the whole thing is a document, and a settings
                        // panel that scrolls a changelog has stopped being a settings
                        // panel -- which is what the GitHub button underneath is for.
                        text = notes,
                        color = Ink.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(12.dp))
                if (release.installable && installer.supported) {
                    AppButton(
                        label = "Install ${release.tag}",
                        onClick = {
                            state = UpdateState.Downloading(release, 0f)
                            scope.launch {
                                val result = installer.install(release) { fraction ->
                                    state = UpdateState.Downloading(release, fraction)
                                }
                                state = result.fold(
                                    // The installer is now in front of the user and this
                                    // app is on its way out, so there is nothing further
                                    // to say and no state worth returning to.
                                    onSuccess = { UpdateState.Handoff },
                                    onFailure = { UpdateState.Failed(release, it.readable()) },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = AppIcons.Download,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                AppOutlineButton(
                    label = if (release.installable) "View on GitHub" else "Open release on GitHub",
                    onClick = { links.openUri(release.url) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is UpdateState.Downloading -> {
                Status("Downloading ${current.release.tag}…")
                Spacer(Modifier.height(10.dp))
                ProgressTrack(fraction = current.fraction)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(current.fraction * 100).toInt()}%",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            UpdateState.Handoff -> Status(
                "Android is installing the update. Reopen Pocketful when it finishes.",
                Ink.Gain,
            )

            is UpdateState.Failed -> {
                Status(current.message, Ink.Loss)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppOutlineButton(
                        label = "Try again",
                        // A failed check has no release to go back to, so it retries the
                        // check; a failed download does, and retrying that should not
                        // cost a second round trip to ask what we already know.
                        onClick = {
                            val release = current.release
                            state = if (release == null) UpdateState.Idle else UpdateState.Found(release)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    AppOutlineButton(
                        label = "Open GitHub",
                        onClick = { links.openUri(AppUpdates.releasesUrl) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Explainer(text: String) {
    Text(text, color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Status(text: String, color: Color = Ink.TextSecondary) {
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
}

/**
 * The check, as the one place that decides what "newer" means.
 *
 * A pre-release is offered as readily as a stable one. This app ships from a repository
 * rather than a store, whoever is running it is running it on purpose, and the version
 * number is the only promise being made about what a build contains.
 */
private suspend fun check(updates: AppUpdates): UpdateState {
    val release = updates.latest()
        ?: return UpdateState.Failed(null, "Could not reach GitHub. Check your connection.")
    return if (release.isNewerThanInstalled) UpdateState.Found(release) else UpdateState.UpToDate
}

/** Where the update flow has got to. */
private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Found(val release: Release) : UpdateState
    data class Downloading(val release: Release, val fraction: Float) : UpdateState

    /** The system installer has it now, and this app is about to be replaced. */
    data object Handoff : UpdateState
    data class Failed(val release: Release?, val message: String) : UpdateState
}

/**
 * A failure as one sentence.
 *
 * [UpdateInstaller.Failure] already carries something written for a person, so it is used
 * as-is; anything else is an exception class name, which tells the user nothing they can
 * act on.
 */
private fun Throwable.readable(): String = when (this) {
    is UpdateInstaller.Failure -> message ?: "The update could not be installed."
    else -> "The update could not be downloaded. Check your connection and try again."
}
