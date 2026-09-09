package app.pocketful.data

import app.pocketful.domain.Card
import app.pocketful.domain.CardId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Edition
import app.pocketful.domain.Finish
import app.pocketful.domain.Money
import app.pocketful.domain.PokemonType
import app.pocketful.domain.PriceSnapshot
import app.pocketful.domain.Printing
import app.pocketful.domain.PrintingId
import app.pocketful.domain.Supertype
import app.pocketful.domain.Variant
import app.pocketful.domain.VariantId

/**
 * One pocket of a binder built from a set: a card, and the press run it is being held
 * open for.
 *
 * A plain set binder is one pocket per card, so every pocket carries the same finish and
 * this pairing is a formality. A master set is not: the same Charizard needs a holo pocket
 * and a reverse pocket, and those are two different variants at two different prices, so
 * the pocket has to say which of them it is waiting for.
 */
data class SetPocket(val hit: SearchHit, val finish: Finish)

/**
 * Turning a fetched card into catalog rows.
 *
 * The identity split the app is built on -- card / printing / variant -- has no
 * equivalent upstream: TCGdex returns one document per printing with a `variants` flag
 * set saying which press runs exist. So an import fans one document out into one [Card],
 * one [Printing], and a [Variant] per finish that was actually printed, which is what
 * lets a reverse holo and a plain copy of the same card carry different prices.
 *
 * Ids are derived from the upstream id rather than generated, so importing the same card
 * twice updates it in place instead of quietly creating a second Charizard.
 */
object CardImport {

    /** Which TCGplayer price key belongs to which finish, best match first. */
    private fun priceKeys(finish: Finish, firstEdition: Boolean): List<String> = when (finish) {
        Finish.NON_HOLO -> if (firstEdition) {
            listOf("1stEditionNormal", "1stEdition", "normal")
        } else {
            listOf("normal", "1stEditionNormal")
        }
        Finish.HOLO -> if (firstEdition) {
            listOf("1stEditionHolofoil", "holofoil")
        } else {
            listOf("holofoil", "1stEditionHolofoil")
        }
        Finish.REVERSE_HOLO -> listOf("reverseHolofoil", "holofoil")
        Finish.FULL_ART, Finish.TEXTURED, Finish.GOLD, Finish.OTHER ->
            listOf("holofoil", "normal", "reverseHolofoil")
    }

    /** Which finishes this printing actually exists in, per the upstream variant flags. */
    fun finishesOf(card: RemoteCard): List<Finish> = finishesOf(card.variants)

    /** The same question asked of the flags alone, which is how a set answers it in bulk. */
    fun finishesOf(flags: RemoteVariants?): List<Finish> {
        val found = buildList {
            if (flags == null || flags.normal) add(Finish.NON_HOLO)
            if (flags?.holo == true) add(Finish.HOLO)
            if (flags?.reverse == true) add(Finish.REVERSE_HOLO)
        }
        // A holo-only card (most vintage rares) reports normal = false. Falling back to
        // non-holo there would file a Base Set Charizard as a common.
        return found.ifEmpty { listOf(Finish.HOLO) }
    }

    fun cardId(card: RemoteCard): CardId = cardId(card.id)

    fun printingId(card: RemoteCard): PrintingId = printingId(card.id)

    fun variantId(card: RemoteCard, finish: Finish, edition: Edition): VariantId =
        variantId(card.id, finish, edition)

    // The id-only forms. A search row and a fetched card are the same card at two levels
    // of detail, so they have to key to the same rows -- otherwise filling a want list
    // from a set listing and then fetching one of those cards produces two Charizards.

    fun cardId(remoteId: String): CardId = CardId("tcgdex-$remoteId")

    fun printingId(remoteId: String): PrintingId = PrintingId("tcgdex-$remoteId")

    fun variantId(remoteId: String, finish: Finish, edition: Edition): VariantId {
        val suffix = buildString {
            append(finish.name.lowercase())
            if (edition != Edition.UNLIMITED) append("-").append(edition.name.lowercase())
        }
        return VariantId("tcgdex-$remoteId-$suffix")
    }

    /**
     * One pocket per card, each in the press run that card was actually printed in.
     *
     * The plainest finish that exists, which is not the same as the plainest finish. Most
     * cards are normals and get a normal pocket, but a modern ex or a vintage holo rare
     * was never printed as a normal at all, and a want-pocket for a variation nobody has
     * ever pulled is a pocket that can never be filled from a real binder. [finishesOf]
     * already ranks them plainest-first, so the head of that list is the answer.
     *
     * A card the catalog could not be asked about falls back to a normal -- which is what
     * every pocket in this binder used to be.
     */
    fun checklist(
        hits: List<SearchHit>,
        variants: Map<String, RemoteVariants>,
    ): List<SetPocket> = hits.map { hit ->
        SetPocket(hit, finishesOf(variants[hit.id]).first())
    }

    /**
     * Every pocket a master set needs, in the order they go into the binder.
     *
     * A master set is the set with every variation of every card in it, so the checklist
     * fans out: one pocket per press run rather than one per card. Ordered card by card
     * and then plainest finish first, which is how a master set is built in the hand --
     * a card's normal, holo and reverse sit together, rather than the binder holding every
     * normal in the set and then starting over at card one.
     *
     * The difference from [checklist] is only how much of each card's list is taken: that
     * one keeps the head, this one keeps all of it. A card the catalog could not be asked
     * about therefore falls back to the same single pocket either way. A missing holo
     * pocket is one the user can add in a gesture; a card missing outright is a hole in
     * the checklist they have to notice first.
     */
    fun masterSet(
        hits: List<SearchHit>,
        variants: Map<String, RemoteVariants>,
    ): List<SetPocket> = hits.flatMap { hit ->
        finishesOf(variants[hit.id]).map { finish -> SetPocket(hit, finish) }
    }

    /**
     * Catalog rows for cards known only from a listing, in one pass.
     *
     * Building a want list for a 200-card set cannot mean 200 card fetches -- that is a
     * minute of waiting for a button that should feel instant. A set listing already
     * carries everything a *pocket* needs: a name, a printed number, and the art stem,
     * which is what makes a wanted pocket a picture of the card you are hunting rather
     * than a dashed rectangle with a name in it. What it does not carry is the price and
     * the card's type, and both arrive on their own the next time the catalog sync runs.
     *
     * Existing rows are never overwritten. A card already imported in full knows more
     * than a listing row does, and a stub landing on top of it would throw that away --
     * so this only ever fills gaps, and returns the variant for every hit either way.
     */
    fun stubAll(
        snapshot: CollectionSnapshot,
        pockets: List<SetPocket>,
        edition: Edition = Edition.UNLIMITED,
    ): Pair<CollectionSnapshot, List<VariantId>> {
        val cards = snapshot.cards.toMutableMap()
        val printings = snapshot.printings.toMutableMap()
        val variants = snapshot.variants.toMutableMap()

        val ids = pockets.map { (hit, finish) ->
            val cardId = cardId(hit.id)
            val printingId = printingId(hit.id)
            val variantId = variantId(hit.id, finish, edition)

            if (cardId !in cards) {
                cards[cardId] = Card(id = cardId, name = hit.name, supertype = Supertype.POKEMON)
            }
            if (printingId !in printings) {
                printings[printingId] = Printing(
                    id = printingId,
                    cardId = cardId,
                    setCode = hit.setId,
                    setName = hit.setName,
                    number = hit.number,
                    setTotal = hit.setTotal,
                    rarity = null,
                    illustrator = null,
                    releaseYear = null,
                    imageUrl = hit.artStem,
                )
            }
            if (variantId !in variants) {
                variants[variantId] = Variant(
                    id = variantId,
                    printingId = printingId,
                    finish = finish,
                    edition = edition,
                )
            }
            variantId
        }

        return snapshot.copy(cards = cards, printings = printings, variants = variants) to ids
    }

    /**
     * Writes [card] into [snapshot], replacing whatever was there under the same ids.
     *
     * Prices are only written when TCGplayer actually quoted the finish. A card with no
     * quote keeps whatever the snapshot already held, so a refresh can never silently
     * zero out a value that a user typed in themselves.
     */
    fun into(
        snapshot: CollectionSnapshot,
        card: RemoteCard,
        edition: Edition = Edition.UNLIMITED,
        fetchedAtEpochSeconds: Long = 0L,
    ): CollectionSnapshot {
        val cardId = cardId(card)
        val printingId = printingId(card)
        val firstEdition = edition == Edition.FIRST_EDITION || card.variants?.firstEdition == true

        val domainCard = Card(
            id = cardId,
            name = card.name,
            supertype = supertypeOf(card.category),
            hp = card.hp,
            types = card.types.mapNotNull(::typeOf),
            flavorText = card.description,
        )

        val printing = Printing(
            id = printingId,
            cardId = cardId,
            setCode = card.set?.id ?: card.id.substringBeforeLast('-', ""),
            setName = card.set?.name ?: "Unknown set",
            number = card.localId ?: card.id.substringAfterLast('-'),
            setTotal = card.set?.cardCount?.official?.toString(),
            rarity = card.rarity,
            illustrator = card.illustrator,
            releaseYear = null,
            imageUrl = card.image,
        )

        var variants = snapshot.variants
        var prices = snapshot.prices
        for (finish in finishesOf(card)) {
            val id = variantId(card, finish, edition)
            variants = variants + (
                id to Variant(id = id, printingId = printingId, finish = finish, edition = edition)
                )
            card.marketPriceCents(priceKeys(finish, firstEdition))?.let { cents ->
                prices = prices + (
                    id to PriceSnapshot(
                        variantId = id,
                        market = Money(cents),
                        source = "tcgplayer",
                        fetchedAtEpochSeconds = fetchedAtEpochSeconds,
                    )
                    )
            }
        }

        return snapshot.copy(
            cards = snapshot.cards + (cardId to domainCard),
            printings = snapshot.printings + (printingId to printing),
            variants = variants,
            prices = prices,
        )
    }

    private fun supertypeOf(category: String?): Supertype = when (category?.lowercase()) {
        "trainer" -> Supertype.TRAINER
        "energy" -> Supertype.ENERGY
        else -> Supertype.POKEMON
    }

    private fun typeOf(raw: String): PokemonType? = when (raw.lowercase()) {
        "grass" -> PokemonType.GRASS
        "fire" -> PokemonType.FIRE
        "water" -> PokemonType.WATER
        "lightning", "electric" -> PokemonType.LIGHTNING
        "psychic" -> PokemonType.PSYCHIC
        "fighting" -> PokemonType.FIGHTING
        "darkness", "dark" -> PokemonType.DARKNESS
        "metal", "steel" -> PokemonType.METAL
        "fairy" -> PokemonType.FAIRY
        "dragon" -> PokemonType.DRAGON
        "colorless", "normal" -> PokemonType.COLORLESS
        else -> null
    }
}
