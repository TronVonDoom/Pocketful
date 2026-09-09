package app.pocketful.domain

/**
 * The trading card games the app knows about.
 *
 * Two of these are wired to a live catalog and three are not. That is stated here as a
 * flag rather than hidden by listing only the ones that work, because the honest answer
 * to "can I track my Lorcana in this" is "not yet" and an app that silently omits the
 * question reads as one that has never considered it. The unconnected entries are listed,
 * labelled, and do nothing when tapped -- which is the truth.
 *
 * The Pokémon TCG and Pokémon TCG Pocket are listed apart even though one upstream
 * catalog answers for both, because they are two games rather than two eras of one. Cards
 * in the first are objects with a resale market; cards in the second cannot leave the
 * account that opened them. Filed together they put Genetic Apex between two Scarlet &
 * Violet sets and gave a binder full of digital cards a portfolio value of zero with
 * nothing on screen to explain it.
 *
 * When a third catalog is added, the work is a [Catalog] implementation and flipping one
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
    /** Whether a live catalog stands behind it. */
    val connected: Boolean,
    /**
     * Whether its cards are printed objects.
     *
     * Two things follow, and both are visible enough to be worth a flag rather than a
     * check against one enum constant. A digital card has no resale market, so the app
     * has no price to show for it and should say so instead of drawing a blank. And a
     * digital card has exactly one press run, so a master set of a digital set is the
     * set checklist under a grander name -- an offer worth withdrawing rather than
     * honouring twice.
     */
    val printed: Boolean,
    /** What a game tile says about it, in one line. */
    val note: String,
) {
    POKEMON(
        label = "Pokémon TCG",
        wordmark = "Pokémon",
        publisher = "The Pokémon Company",
        connected = true,
        printed = true,
        note = "Printed sets, with market prices",
    ),
    POKEMON_POCKET(
        label = "Pokémon TCG Pocket",
        wordmark = "TCG Pocket",
        publisher = "The Pokémon Company",
        connected = true,
        printed = false,
        note = "The mobile game. Digital cards, no prices",
    ),
    MAGIC(
        label = "Magic: The Gathering",
        wordmark = "Magic",
        publisher = "Wizards of the Coast",
        connected = false,
        printed = true,
        note = "No catalog connected yet",
    ),
    YUGIOH(
        label = "Yu-Gi-Oh!",
        wordmark = "Yu-Gi-Oh!",
        publisher = "Konami",
        connected = false,
        printed = true,
        note = "No catalog connected yet",
    ),
    ONE_PIECE(
        label = "One Piece Card Game",
        wordmark = "One Piece",
        publisher = "Bandai",
        connected = false,
        printed = true,
        note = "No catalog connected yet",
    ),
    LORCANA(
        label = "Disney Lorcana",
        wordmark = "Lorcana",
        publisher = "Ravensburger",
        connected = false,
        printed = true,
        note = "No catalog connected yet",
    );

    /** Everything a text query should be able to hit, lowercased once. */
    val searchIndex: String = "${label.lowercase()} ${publisher.lowercase()}"

    companion object {
        /** Connected games first: the ones that work should not be third in the list. */
        val browsable: List<TcgGame> get() = entries.sortedByDescending { it.connected }
    }
}
