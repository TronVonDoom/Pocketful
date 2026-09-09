package app.pocketful.data

import androidx.compose.runtime.Composable

/**
 * Somewhere private to put a file, and a clock to stamp it with.
 *
 * Deliberately the smallest surface that persistence needs: read a document, write a
 * document, throw one away. No paths, no streams, no directory handles -- everything the
 * app saves is a whole small document rewritten at once, and an interface that admits
 * that is one a desktop or iOS target can satisfy in a dozen lines.
 *
 * [write] is required to be **atomic**: after it returns, [name] either holds the new
 * content or the content it held before, and never half of either. That is not a detail
 * an implementation may skip. The whole reason this exists is that Android kills apps
 * mid-flight, and a save that can be interrupted halfway through is a save that turns a
 * process kill from an inconvenience into data loss.
 */
interface SaveStorage {

    /** The document's contents, or null if it has never been written or cannot be read. */
    suspend fun read(name: String): String?

    /** Replaces [name] atomically. Throws if the write could not be completed. */
    suspend fun write(name: String, text: String)

    /** Removes [name]. Missing is not an error. */
    suspend fun delete(name: String)

    /**
     * Renames a document out of the way instead of deleting it.
     *
     * Used for exactly one thing: a save file that will not parse. Overwriting it with a
     * fresh empty collection would be the app destroying the only evidence of what the
     * user had, so the unreadable file is kept under another name and the app starts
     * empty beside it.
     */
    suspend fun quarantine(name: String)
}

/** Wall-clock seconds. Used to date the catalog cache, never to order anything. */
expect fun nowEpochSeconds(): Long

/** The storage for the platform the app is running on. */
@Composable
expect fun rememberSaveStorage(): SaveStorage
