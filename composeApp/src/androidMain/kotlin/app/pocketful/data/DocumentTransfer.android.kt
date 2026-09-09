package app.pocketful.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The Android half: the Storage Access Framework.
 *
 * SAF rather than a share sheet, and rather than the [androidx.core.content.FileProvider]
 * this app already has. That provider is scoped to `updates/` on purpose -- it exists so
 * one APK can reach the system installer, and pointing it anywhere wider would expose the
 * cache it deliberately does not cover. SAF needs no provider at all: the chooser hands
 * back a content URI the user picked, which is both narrower and the flow people already
 * know, with Drive and Files and everything else in it for free.
 *
 * The awkward part is shape rather than behaviour. A launcher is callback-based and must
 * be registered during composition, but the callers here are coroutines that want to
 * await an answer -- so each launcher completes a [CompletableDeferred] that the suspend
 * function is sitting on. One deferred per direction, held outside the launcher because
 * the callback has no other way to reach the coroutine that started it.
 */
private class AndroidDocumentTransfer(
    private val context: Context,
    private val saveLauncher: ActivityResultLauncher<String>,
    private val openLauncher: ActivityResultLauncher<Array<String>>,
    private val pending: PendingChoices,
) : DocumentTransfer {

    override val supported: Boolean = true

    override suspend fun save(suggestedName: String, text: String): TransferResult {
        val uri = pending.awaitSave { saveLauncher.launch(suggestedName) }
            ?: return TransferResult.Cancelled

        return runCatching {
            withContext(Dispatchers.IO) {
                // `use` on the stream, not on the descriptor: a chooser can hand back a
                // URI whose provider refuses to open, and that arrives here as null
                // rather than as an exception.
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("could not be written")
                stream.use { it.write(text.encodeToByteArray()) }
            }
            TransferResult.Saved(displayName(uri) ?: suggestedName)
        }.getOrElse { TransferResult.Failed("That file could not be written.") }
    }

    override suspend fun open(): TransferResult {
        val uri = pending.awaitOpen { openLauncher.launch(READABLE) }
            ?: return TransferResult.Cancelled

        return runCatching {
            val text = withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("could not be read")
                stream.use { it.readBytes().decodeToString() }
            }
            TransferResult.Opened(displayName(uri) ?: "that file", text)
        }.getOrElse { TransferResult.Failed("That file could not be read.") }
    }

    /** The name the user sees, so a confirmation can name the file they actually picked. */
    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private companion object {
        /**
         * What the open chooser will show.
         *
         * Wider than the one type this app writes, because the type a document comes back
         * as is decided by whatever wrote it. A backup that has been through a chat app,
         * a cloud drive or a zip round trip frequently arrives labelled `text/plain` or
         * `application/octet-stream`, and a picker that hides the user's own backup from
         * them is worse than one that also lists a text file they will not choose.
         */
        val READABLE = arrayOf(
            "application/json",
            "application/octet-stream",
            "text/plain",
            "text/json",
        )
    }
}

/**
 * The two answers a chooser can be waiting to give.
 *
 * Kept in one object so the composable can hand the same reference to both the launcher
 * callbacks and the class that awaits them. `complete` on an already-completed deferred
 * is a no-op, which is what makes a duplicate callback harmless rather than a crash.
 */
private class PendingChoices {
    var save: CompletableDeferred<Uri?>? = null
    var open: CompletableDeferred<Uri?>? = null

    fun deliverSave(uri: Uri?) {
        save?.complete(uri)
        save = null
    }

    fun deliverOpen(uri: Uri?) {
        open?.complete(uri)
        open = null
    }

    suspend fun awaitSave(launch: () -> Unit): Uri? {
        val deferred = CompletableDeferred<Uri?>()
        save = deferred
        return try {
            launch()
            deferred.await()
        } finally {
            // Cleared on cancellation too, so a screen left while the chooser is open does
            // not strand a deferred that the next attempt would find already in place.
            if (save === deferred) save = null
        }
    }

    suspend fun awaitOpen(launch: () -> Unit): Uri? {
        val deferred = CompletableDeferred<Uri?>()
        open = deferred
        return try {
            launch()
            deferred.await()
        } finally {
            if (open === deferred) open = null
        }
    }
}

actual fun todayStamp(): String = LocalDate.now().toString()

@Composable
actual fun rememberDocumentTransfer(): DocumentTransfer {
    val context = LocalContext.current.applicationContext
    val pending = remember { PendingChoices() }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> pending.deliverSave(uri) }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pending.deliverOpen(uri) }

    return remember(context, saveLauncher, openLauncher, pending) {
        AndroidDocumentTransfer(context, saveLauncher, openLauncher, pending)
    }
}
