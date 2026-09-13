package app.pocketful.data

import app.pocketful.domain.Card
import app.pocketful.domain.CardId
import app.pocketful.domain.Edition
import app.pocketful.domain.Finish
import app.pocketful.domain.PokemonType
import app.pocketful.domain.Printing
import app.pocketful.domain.PrintingId
import app.pocketful.domain.Supertype
import app.pocketful.domain.Variant
import app.pocketful.domain.VariantId

/**
 * One pocket of a binder built from a set: a card, and the printing it is being held open for.
 *
 * A plain set binder is one pocket per card, in that card's plainest printing. A master set is
 * every printing of every card, so the same Charizard needs a pocket for its holo, its
 * shadowless holo and its 1st Edition holo, and the pocket has to say which one it waits for.
 */
data class SetPocket(val hit: SearchHit, val printingId: String)

/**
 * The catalog rows one or more cards turn into.
 *
 * The app keeps four levels -- card, printing, variant, copy -- and the published catalog
 * lines up with the first three: a catalog *card* is both the app's [Card] and its [Printing]
 * (they share the card's ID), and each catalog *printing* is a [Variant] under its ID. The IDs
 * are the catalog's own, so filing the same card twice updates it rather than making a second
 * one, and a collection keyed by them stays correct for as long as the catalog does.
 */
data class CatalogRows(
    val cards: Map<CardId, Card> = emptyMap(),
    val printings: Map<PrintingId, Printing> = emptyMap(),
    val variants: Map<VariantId, Variant> = emptyMap(),
) {
    operator fun plus(other: CatalogRows) = CatalogRows(
        cards = cards + other.cards,
        printings = printings + other.printings,
        variants = variants + other.variants,
    )

    val isEmpty: Boolean get() = variants.isEmpty()
}

object CardImport {

    /** Everything one catalog card is, as rows. */
    fun rowsFor(card: CatalogCard, catalog: CardCatalog): CatalogRows {
        val c = card.card
        val set = card.entry.set
        val language = card.entry.catalog.language
        val cardId = CardId(c.id)
        val printingId = PrintingId(c.id)

        val domainCard = Card(
            id = cardId,
            name = c.name,
            supertype = supertypeOf(c.category),
            subtypes = c.subtypes.map { catalog.termLabel("subtype", it, language) },
            hp = c.hp,
            types = c.types.mapNotNull(::typeOf),
            retreatCost = c.retreat,
            flavorText = c.flavorText,
        )
        val printing = Printing(
            id = printingId,
            cardId = cardId,
            setCode = set.id,
            setName = set.name,
            number = c.number,
            setTotal = null,
            printedNumber = c.printedNumber,
            rarity = c.rarity?.let { catalog.termLabel("rarity", it, language) },
            illustrator = c.illustrator,
            releaseYear = set.releaseDate?.take(4)?.toIntOrNull(),
            image = CardArt.stemOf(c.image),
            back = card.back,
        )
        val variants = c.printings.map { variantOf(it, printingId, language, catalog) }
        return CatalogRows(
            cards = mapOf(cardId to domainCard),
            printings = mapOf(printingId to printing),
            variants = variants.associateBy { it.id },
        )
    }

    /**
     * A catalog printing as a [Variant].
     *
     * The app's pickers and badges know three things about a press run -- its finish, its
     * edition and anything special about it -- so the printing's words are sorted into those:
     * `normal`, `holo` and `reverse` are finishes, `1st-edition` and `shadowless` editions, and
     * every other word (a foil pattern, a stamp, a misprint, a copyright line) is what makes it
     * special, with the catalog's own labels to say so.
     */
    fun variantOf(p: SetPrinting, printingId: PrintingId, language: String, catalog: CardCatalog): Variant {
        val finish = finishOf(p.finish)
        val edition = editionOf(p.edition)
        val extras = buildList {
            if (edition == Edition.UNLIMITED) p.edition?.let(::add)
            p.pattern?.let(::add)
            if (finish == Finish.OTHER) add(p.finish)
            addAll(p.stamps)
            p.error?.let(::add)
        }
        return Variant(
            id = VariantId(p.id),
            printingId = printingId,
            finish = finish,
            edition = edition,
            language = CardCatalog.languageOf(language),
            note = p.identify,
            special = extras.joinToString("+").ifEmpty { null },
            specialLabel = extras.joinToString(" · ") { catalog.wordLabel(it) }.ifEmpty { null },
            image = CardArt.stemOf(p.image),
        )
    }

    fun finishOf(word: String): Finish = when (word) {
        "normal" -> Finish.NON_HOLO
        "holo" -> Finish.HOLO
        "reverse" -> Finish.REVERSE_HOLO
        else -> Finish.OTHER
    }

    fun editionOf(word: String?): Edition = when (word) {
        "1st-edition" -> Edition.FIRST_EDITION
        "shadowless" -> Edition.SHADOWLESS
        else -> Edition.UNLIMITED
    }

    /**
     * A card's printings, plainest first: unlimited before other editions (and those in the
     * catalog's own order, so 1st Edition before Shadowless), plain before patterned or stamped,
     * then normal, holo, reverse. The head of this list is the printing a card stands for when
     * only one is wanted.
     */
    fun plainestFirst(printings: List<SetPrinting>, catalog: CardCatalog): List<SetPrinting> =
        printings.sortedWith(
            compareBy(
                { if (it.edition == null) 0 else 1 },
                { catalog.wordSort(it.edition) },
                { (if (it.pattern != null) 1 else 0) + it.stamps.size + (if (it.error != null) 1 else 0) },
                { finishOf(it.finish).ordinal },
                { it.variant },
            ),
        )

    /** The printing of a card an add should default to, given the finish a screen asked for. */
    fun preferredVariant(card: CatalogCard, finish: Finish, catalog: CardCatalog): VariantId? {
        val ordered = plainestFirst(card.card.printings, catalog)
        val chosen = ordered.firstOrNull { finishOf(it.finish) == finish } ?: ordered.firstOrNull()
        return chosen?.let { VariantId(it.id) }
    }

    /** One pocket per card, each in the plainest printing that card has. */
    fun checklist(catalog: CardCatalog, hits: List<SearchHit>): List<SetPocket> = hits.mapNotNull { hit ->
        val first = catalog.card(hit.id)?.card?.printings?.let { plainestFirst(it, catalog) }?.firstOrNull() ?: return@mapNotNull null
        SetPocket(hit, first.id)
    }

    /**
     * Every pocket a master set needs, card by card and plainest printing first -- a card's
     * printings sit together, the way a master set is built in the hand.
     */
    fun masterSet(catalog: CardCatalog, hits: List<SearchHit>): List<SetPocket> = hits.flatMap { hit ->
        catalog.card(hit.id)?.card?.printings?.let { plainestFirst(it, catalog) }.orEmpty().map { SetPocket(hit, it.id) }
    }

    /** The rows every pocket's card needs, and the variant each pocket holds open. */
    fun rowsForPockets(catalog: CardCatalog, pockets: List<SetPocket>): Pair<CatalogRows, List<VariantId>> {
        var rows = CatalogRows()
        val seen = mutableSetOf<String>()
        for (pocket in pockets) {
            if (!seen.add(pocket.hit.id)) continue
            val card = catalog.card(pocket.hit.id) ?: continue
            rows += rowsFor(card, catalog)
        }
        return rows to pockets.map { VariantId(it.printingId) }.filter { it in rows.variants }
    }

    fun supertypeOf(category: String?): Supertype = when (category?.lowercase()) {
        "trainer" -> Supertype.TRAINER
        "energy" -> Supertype.ENERGY
        else -> Supertype.POKEMON
    }

    fun typeOf(raw: String): PokemonType? = when (raw.lowercase()) {
        "grass" -> PokemonType.GRASS
        "fire" -> PokemonType.FIRE
        "water" -> PokemonType.WATER
        "lightning" -> PokemonType.LIGHTNING
        "psychic" -> PokemonType.PSYCHIC
        "fighting" -> PokemonType.FIGHTING
        "darkness" -> PokemonType.DARKNESS
        "metal" -> PokemonType.METAL
        "fairy" -> PokemonType.FAIRY
        "dragon" -> PokemonType.DRAGON
        "colorless" -> PokemonType.COLORLESS
        else -> null
    }
}
