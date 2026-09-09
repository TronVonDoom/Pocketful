package app.pocketful.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.pocketful.data.Release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * The Android half: fetch the APK, then ask the package installer to take it from here.
 *
 * Installing is not something an app can do to itself. It hands a content URI to the
 * system installer and the user confirms it, and three things have to line up for even
 * that much to work -- all three are visible in this file:
 *
 *  1. The file lands in the app's own cache and is shared through a [FileProvider]. A
 *     `file://` URI handed to the installer has been an exception since Android 7.
 *  2. `REQUEST_INSTALL_PACKAGES` is declared *and* the user has granted the app the
 *     "install unknown apps" toggle. The permission alone is not enough on API 26+, and
 *     the failure without it is silent, so [canInstall] checks first and the caller is
 *     sent to the setting rather than to a dead end.
 *  3. The downloaded APK is signed with the same key as the installed one. Nothing here
 *     can enforce that -- it is settled in composeApp/build.gradle.kts -- and getting it
 *     wrong surfaces at this end as the installer refusing with "app not installed".
 *
 * The download deliberately does not go through Ktor, which every other request in the app
 * does. This one is a large binary on a JVM-only path with a progress bar attached, and
 * `HttpURLConnection` does that in fifteen lines that cannot be broken by a change to a
 * streaming API two layers down.
 */
private class AndroidUpdateInstaller(private val context: Context) : UpdateInstaller {

    override val supported: Boolean = true

    override suspend fun install(
        release: Release,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = runCatching {
        val asset = release.asset
            ?: throw UpdateInstaller.Failure(
                "This release has no APK attached. Open it on GitHub to see what it ships.",
            )

        if (!canInstall()) {
            requestInstallPermission()
            throw UpdateInstaller.Failure(
                "Allow Pocketful to install apps, then tap Install update again.",
            )
        }

        launchInstaller(download(asset.url, release.tag, onProgress))
    }

    /**
     * Streams the APK into the cache directory, reporting how far along it is.
     *
     * Streamed rather than read whole: a release APK is tens of megabytes, and holding one
     * in a ByteArray on a phone that is already carrying a collection's worth of decoded
     * artwork is the kind of allocation that gets an app killed halfway through its own
     * update.
     *
     * Written to a `.part` file and renamed at the end, so an interrupted download can
     * never be mistaken for a finished one and handed to the installer.
     */
    private suspend fun download(
        url: String,
        tag: String,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        // Everything but the app's own build of this name is attacker-controlled: the tag
        // arrives over the network, and a `../` inside it would write outside the cache.
        val safeTag = tag.filter { it.isLetterOrDigit() || it == '.' }.ifEmpty { "latest" }
        val target = File(directory, "pocketful-$safeTag.apk")
        val partial = File(directory, "pocketful-$safeTag.apk.part")

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw UpdateInstaller.Failure(
                    "GitHub answered ${connection.responseCode} for that download.",
                )
            }
            val total = connection.contentLengthLong
            var written = 0L

            connection.inputStream.use { source ->
                partial.outputStream().use { sink ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        // Checked every chunk so leaving the screen actually stops the
                        // download rather than letting it run to completion unwatched.
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress(written.toFloat() / total)
                    }
                }
            }

            if (written == 0L) throw UpdateInstaller.Failure("The download was empty. Try again.")
            if (total > 0 && written != total) {
                throw UpdateInstaller.Failure("The download stopped early. Try again.")
            }
        } finally {
            connection.disconnect()
        }

        target.delete()
        if (!partial.renameTo(target)) {
            throw UpdateInstaller.Failure("Could not save the download.")
        }
        onProgress(1f)
        target
    }

    private fun launchInstaller(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            // NEW_TASK because this starts from an application context, and the read grant
            // because the installer is a different process reading our cache directory.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * Whether the user has granted this app the right to install packages.
     *
     * Since API 26 the manifest permission is only half of it: the other half is a
     * per-app toggle in system settings that only the user can flip. minSdk is 26, so
     * there is no older path left to guard -- the version checks that used to stand here
     * could never be false.
     */
    private fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    private fun requestInstallPermission() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData("package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private companion object {
        const val CHUNK = 64 * 1024
    }
}

@Composable
actual fun rememberUpdateInstaller(): UpdateInstaller {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidUpdateInstaller(context) }
}
