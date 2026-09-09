package app.pocketful.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Android half: files in the app's private directory.
 *
 * `filesDir`, not `cacheDir` and not external storage. The collection is the one thing in
 * this app that cannot be re-fetched, and the other two locations are both places the
 * system is entitled to empty without asking.
 *
 * Writes go to a `.tmp` beside the target and are renamed over it -- the same shape the
 * updater uses for a downloaded APK, and for the same reason. `writeText` on the target
 * directly would truncate the existing file *before* writing the new bytes, so a process
 * killed at that moment leaves a zero-length collection where the collection used to be.
 * Renaming is atomic within a filesystem, so the worst an interrupted save can do is
 * leave the previous version in place.
 */
private class AndroidSaveStorage(context: Context) : SaveStorage {

    private val directory = File(context.filesDir, "pocketful").apply { mkdirs() }

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        val file = File(directory, name)
        if (!file.exists()) null else runCatching { file.readText() }.getOrNull()
    }

    /**
     * Runs under [NonCancellable]: a save that has decided to write is finished, because
     * the alternative is a rename that never happens and an edit that is silently lost.
     * The window is one small file, and the caller cancels *before* this by dropping the
     * debounce, not during it.
     */
    override suspend fun write(name: String, text: String) = withContext(Dispatchers.IO + NonCancellable) {
        val target = File(directory, name)
        val temporary = File(directory, "$name.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) {
            // renameTo will not replace an existing file on every Android version, so the
            // fallback is delete-then-rename. It opens a window where neither name holds
            // the collection; the temp file is left behind if it fails, which is what
            // makes the loss recoverable by hand rather than total.
            target.delete()
            if (!temporary.renameTo(target)) error("Could not save $name")
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) {
            File(directory, name).delete()
            File(directory, "$name.tmp").delete()
        }
    }

    override suspend fun quarantine(name: String) {
        withContext(Dispatchers.IO) {
            val file = File(directory, name)
            if (!file.exists()) return@withContext
            // One slot, not a timestamped pile. The interesting file is the one from
            // before the app first failed to read it; a second failure is usually the
            // same file failing again, and letting it overwrite the original would lose
            // the very thing being kept.
            val kept = File(directory, "$name.corrupt")
            if (!kept.exists()) file.renameTo(kept) else file.delete()
        }
    }
}

actual fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

@Composable
actual fun rememberSaveStorage(): SaveStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidSaveStorage(context) }
}
