package app.pocketful.domain

/**
 * Storage that is not a binder.
 *
 * A binder is a grid: every card has a pocket, and the pocket is part of how you think
 * about the card. A box of bulk is not -- it is an unordered pile with a label on it, and
 * forcing it into a grid would mean inventing pocket numbers nobody will ever look at.
 * So a [Container] holds an ordered list of copies and nothing else, and the two live
 * side by side under Collections rather than one pretending to be the other.
 */
data class Container(
    val id: ContainerId,
    val name: String,
    val subtitle: String? = null,
    val kind: ContainerKind = ContainerKind.BOX,
    val color: Long = 0xFF64748B,
    val copyIds: List<CopyId> = emptyList(),
) {
    val count: Int get() = copyIds.size

    fun without(copyId: CopyId): Container =
        if (copyId in copyIds) copy(copyIds = copyIds - copyId) else this

    /** Adding is idempotent: filing a card twice into the same box is not two cards. */
    fun with(copyId: CopyId): Container =
        if (copyId in copyIds) this else copy(copyIds = copyIds + copyId)
}

/**
 * What the container physically is. This drives the icon and the wording, not the rules --
 * every kind behaves the same, so adding one is a one-line change here.
 */
enum class ContainerKind(val label: String, val hint: String) {
    BOX("Storage box", "Bulk and sorted commons"),
    DECK("Deck", "A list you actually play"),
    TOPLOADER("Toploaders", "Singles in rigid holders"),
    SLAB("Slab case", "Graded cards"),
    SEALED("Sealed", "Unopened product"),
    SHOEBOX("Shoebox", "Everything else, honestly"),
}
