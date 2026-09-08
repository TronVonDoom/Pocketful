package app.pocketful.domain

/**
 * The trading card games the app knows about.
 *
 * Exactly one of these is wired to a live catalog. That is stated here as a flag rather
 * than hidden by listing only Pokémon, because the honest answer to "can I track my
 * Lorcana in this" is "not yet" and an app that silently omits the question reads as one
 * that has never considered it. The unconnected entries are listed, labelled, and do
 * nothing when tapped -- which is the truth.
 *
 * When a second catalog is added, the work is a [Catalog] implementation and flipping one
 * flag here; nothing above this needs to learn what a game is.
 */
enum class TcgGame(
    val label: String,
    /**
     * The name as it is worn on a tile, where there is room for a wordmark and not for a
     * full title. "Magic" and "One Piece" are what these games are called out loud; the
     * registered name stays in [label] for the places that are naming rather than
     * labelling.
     */
    val wordmark: String,
    val publisher: String,
    /** Whether a live catalog stands behind it. Only Pokémon does today. */
    val connected: Boolean,
    /** What the search screen says about it, in one line. */
    val note: String,
) {
    POKEMON(
        label = "Pokémon TCG",
        wordmark = "Pokémon",
        publisher = "The Pokémon Company",
        connected = true,
        note = "Every set and card, with TCGplayer market prices",
    ),
    MAGIC(
        label = "Magic: The Gathering",
        wordmark = "Magic",
        publisher = "Wizards of the Coast",
        connected = false,
        note = "No catalog connected yet",
    ),
    YUGIOH(
        label = "Yu-Gi-Oh!",
        wordmark = "Yu-Gi-Oh!",
        publisher = "Konami",
        connected = false,
        note = "No catalog connected yet",
    ),
    ONE_PIECE(
        label = "One Piece Card Game",
        wordmark = "One Piece",
        publisher = "Bandai",
        connected = false,
        note = "No catalog connected yet",
    ),
    LORCANA(
        label = "Disney Lorcana",
        wordmark = "Lorcana",
        publisher = "Ravensburger",
        connected = false,
        note = "No catalog connected yet",
    );

    /** Everything a text query should be able to hit, lowercased once. */
    val searchIndex: String = "${label.lowercase()} ${publisher.lowercase()}"

    companion object {
        /** Connected games first: the one that works should not be third in the list. */
        val browsable: List<TcgGame> get() = entries.sortedByDescending { it.connected }
    }
}
