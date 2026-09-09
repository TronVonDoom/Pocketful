package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.CollectionExport
import app.pocketful.data.DocumentTransfer
import app.pocketful.data.EXPORT_MARKER
import app.pocketful.data.SAVE_SCHEMA
import app.pocketful.data.SaveJson
import app.pocketful.data.TransferResult
import app.pocketful.data.buildSnapshot
import app.pocketful.data.exportFileName
import app.pocketful.data.todayStamp
import app.pocketful.data.toExport
import app.pocketful.domain.CollectionSnapshot

/**
 * Taking a collection out of the app, and bringing one back in.
 *
 * Importing is destructive -- it replaces the collection -- so it is deliberately two
 * steps with a look at what is in the file between them. The file is read and parsed
 * first, and only *then* is anything asked or changed, which means a document that turns
 * out to be someone's grocery list is refused before the user has been offered a button
 * that would have deleted their binders.
 *
 * The rest of the app's writes go through [CollectionStore] and nothing here breaks that:
 * this parses and describes, and the store is still what actually changes.
 */
class CollectionTransfer(private val documents: DocumentTransfer) {

    /** Whether this platform can put a document anywhere the user can reach. */
    val supported: Boolean get() = documents.supported

    /** The last thing that happened, for the line under the buttons. */
    var status by mutableStateOf<Status>(Status.Idle)
        private set

    /** A parsed file waiting for the user to say yes. Null when nothing is pending. */
    var pending by mutableStateOf<Pending?>(null)
        private set

    /** True while a chooser is open, so the buttons can stop inviting a second one. */
    var busy by mutableStateOf(false)
        private set

    sealed interface Status {
        data object Idle : Status
        data class Done(val message: String) : Status
        data class Problem(val message: String) : Status
    }

    /**
     * A file that parsed, and what is in it.
     *
     * The counts are the point. "Replace everything?" is a question nobody can answer;
     * "replace everything with 4 binders and 312 cards?" is one they can.
     */
    data class Pending(
        val fileName: String,
        val export: CollectionExport,
        val binders: Int,
        val containers: Int,
        val cards: Int,
        val exportedOn: String?,
    )

    suspend fun export(snapshot: CollectionSnapshot, settings: AppSettings) {
        if (busy) return
        busy = true
        status = Status.Idle
        try {
            val day = todayStamp()
            val document = SaveJson.encodeToString(snapshot.toExport(settings.toSaved(), day))
            status = when (val result = documents.save(exportFileName(day), document)) {
                is TransferResult.Saved -> Status.Done("Saved ${result.name}")
                is TransferResult.Failed -> Status.Problem(result.message)
                // Dismissing a chooser is a decision, not a failure. Saying nothing is
                // the correct amount to say about it.
                TransferResult.Cancelled -> Status.Idle
                is TransferResult.Opened -> Status.Idle
            }
        } finally {
            busy = false
        }
    }

    /**
     * Asks for a file, reads it, and holds it for confirmation. Changes nothing.
     */
    suspend fun choose() {
        if (busy) return
        busy = true
        status = Status.Idle
        pending = null
        try {
            when (val result = documents.open()) {
                TransferResult.Cancelled -> Unit
                is TransferResult.Failed -> status = Status.Problem(result.message)
                is TransferResult.Saved -> Unit
                is TransferResult.Opened -> {
                    when (val parsed = parse(result.text)) {
                        is Parsed.Ok -> pending = describe(result.name, parsed.export)
                        is Parsed.No -> status = Status.Problem(parsed.reason)
                    }
                }
            }
        } finally {
            busy = false
        }
    }

    /**
     * Commits the pending import.
     *
     * The catalog is merged rather than replaced, unlike the collection. A catalog entry
     * is upstream fact keyed by an id that came from upstream, so two of them never
     * disagree and having more of them is never worse -- while a *collection* is a claim
     * about what someone owns, and merging two of those would invent a person who owns
     * both. So the binders and cards are replaced outright and the catalog is unioned,
     * which also means an import cannot cost the device the artwork it already had.
     */
    fun apply(store: CollectionStore) {
        val ready = pending ?: return
        val imported = buildSnapshot(ready.export.collection, ready.export.catalog)
        store.importCollection(imported, ready.export.collection.settings.toAppSettings())
        pending = null
        status = Status.Done(
            "Imported ${count(ready.cards, "card")} from ${ready.fileName}",
        )
    }

    /** Walks away from a pending import without applying it. */
    fun dismiss() {
        pending = null
    }

    private sealed interface Parsed {
        data class Ok(val export: CollectionExport) : Parsed
        data class No(val reason: String) : Parsed
    }

    /**
     * Turns a file into an export, or into a sentence explaining why it is not one.
     *
     * The three checks are deliberately in this order: whether it is JSON at all, whether
     * it is *this app's* JSON, and whether it is a version this build understands. Each
     * one produces a different sentence, because "that is not a Pocketful backup" and
     * "that backup is newer than this app" send the user somewhere completely different.
     */
    private fun parse(text: String): Parsed {
        val export = runCatching { SaveJson.decodeFromString<CollectionExport>(text) }.getOrNull()
            ?: return Parsed.No("That file is not a Pocketful backup.")
        if (export.app != EXPORT_MARKER) {
            return Parsed.No("That file is not a Pocketful backup.")
        }
        if (export.schema > SAVE_SCHEMA) {
            return Parsed.No("That backup was written by a newer version of Pocketful.")
        }
        if (export.collection.binders.isEmpty() &&
            export.collection.containers.isEmpty() &&
            export.collection.copies.isEmpty()
        ) {
            return Parsed.No("That backup is empty.")
        }
        return Parsed.Ok(export)
    }

    private fun describe(fileName: String, export: CollectionExport) = Pending(
        fileName = fileName,
        export = export,
        binders = export.collection.binders.size,
        containers = export.collection.containers.size,
        cards = export.collection.copies.size,
        exportedOn = export.exportedOn,
    )

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}

@Composable
fun rememberCollectionTransfer(documents: DocumentTransfer): CollectionTransfer =
    remember(documents) { CollectionTransfer(documents) }
