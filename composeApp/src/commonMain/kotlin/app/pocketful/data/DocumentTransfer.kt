package app.pocketful.data

import androidx.compose.runtime.Composable

/**
 * Handing a document to the user, and taking one back.
 *
 * Deliberately not part of [SaveStorage]. That is the app's own private shelf, where the
 * app decides the filenames and nothing ever prompts; this is the user's filesystem,
 * where every call opens a chooser they can cancel and the app is a guest. The two have
 * opposite failure modes -- a save that silently does nothing is a bug, a chooser the
 * user dismisses is a Tuesday -- so they are separate interfaces rather than one with a
 * confusing contract.
 *
 * Both calls suspend until the user has finished choosing, which is why [TransferResult]
 * has a cancelled case rather than throwing: backing out of a file picker is not an
 * error and should not be reported to anyone as one.
 */
interface DocumentTransfer {

    /** Whether this platform can put a file where the user can reach it. */
    val supported: Boolean

    /** Offers [text] to be saved, suggesting [suggestedName]. The user picks where. */
    suspend fun save(suggestedName: String, text: String): TransferResult

    /** Asks the user for a document and reads it. */
    suspend fun open(): TransferResult
}

sealed interface TransferResult {
    /** The chooser was dismissed. Not a failure, and not worth a message. */
    data object Cancelled : TransferResult

    data class Saved(val name: String) : TransferResult

    data class Opened(val name: String, val text: String) : TransferResult

    /** Something went wrong that the user can be told about in one line. */
    data class Failed(val message: String) : TransferResult
}

/**
 * Today, as `2026-09-09`.
 *
 * Platform-provided because common code has no calendar: the app carries no date library,
 * and the only thing it has ever needed a wall clock for is stamping a file. Sortable by
 * string, which is the whole reason for this shape over a friendlier one.
 */
expect fun todayStamp(): String

/** The document chooser for the platform the app is running on. */
@Composable
expect fun rememberDocumentTransfer(): DocumentTransfer
