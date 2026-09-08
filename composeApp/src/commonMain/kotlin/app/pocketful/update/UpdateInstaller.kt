package app.pocketful.update

import androidx.compose.runtime.Composable
import app.pocketful.data.Release

/**
 * Taking a release from GitHub and putting it on the device.
 *
 * Split from [app.pocketful.data.AppUpdates] because the two halves have nothing in
 * common: checking is one JSON request that works identically everywhere, and installing
 * is entirely a question of what the platform will let an app do to itself. Android will,
 * given permission and a content URI; a desktop build would hand the file to the OS a
 * different way, and a store build would not be allowed to at all -- which is why
 * [supported] exists rather than every caller assuming it can.
 */
interface UpdateInstaller {

    /** Whether this platform can install an update from inside the app at all. */
    val supported: Boolean

    /**
     * Downloads [release] and hands it to the system installer.
     *
     * Returns once the installer has been *launched*, not once the install has finished:
     * from that point the app is being replaced by the thing it just downloaded, and there
     * is no meaningful "after" for it to report. Everything before that -- a missing APK
     * on the release, a download that failed, a permission the user has not granted -- is
     * a [Failure] with something the user can act on.
     */
    suspend fun install(release: Release, onProgress: (Float) -> Unit): Result<Unit>

    /** What went wrong, in a form a settings row can print. */
    class Failure(message: String, cause: Throwable? = null) : Exception(message, cause)
}

/** The installer for the platform the app is running on. */
@Composable
expect fun rememberUpdateInstaller(): UpdateInstaller
