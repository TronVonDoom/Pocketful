package app.pocketful.data

import app.pocketful.domain.CardId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Copy
import app.pocketful.domain.CopyId
import app.pocketful.domain.Edition
import app.pocketful.domain.Finish
import app.pocketful.domain.PokemonType
import app.pocketful.domain.Printing
import app.pocketful.domain.PrintingId
import app.pocketful.domain.TcgGame
import app.pocketful.domain.VariantId
import app.pocketful.domain.brief
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {

    private val index = CatalogJson.decodeFromString<IndexDoc>(PublishedFixtures.INDEX)
    private val baseSet = CatalogJson.decodeFromString<SetDoc>(PublishedFixtures.BASE_SET)

    private suspend fun loaded(): CardCatalog =
        CardCatalog().also { it.use(LoadedCatalog(index, mapOf(baseSet.id to baseSet))) }

    private fun variant(rows: CatalogRows, id: String) =
        assertNotNull(rows.variants[VariantId(id)], "no variant $id in ${rows.variants.keys}")

    // ---------------------------------------------------------------- reading

    @Test
    fun readsWhatTheEditorPublished() = runTest {
        val catalog = loaded()
        assertTrue(catalog.isLoaded)
        assertEquals(1, catalog.setCount)
        assertEquals(4, catalog.cardCount)
        assertEquals(index.publicUrl, catalog.publicUrl)

        val shape = catalog.catalogIndex()
        assertEquals(listOf("ptcg-en-base"), shape.series.map { it.id })
        val set = shape.sets.single()
        assertEquals("ptcg-en-base01", set.id)
        assertEquals("base01", set.code)
        assertEquals(102, set.officialCount)
        assertEquals("1999", set.releaseYear)
        assertEquals(TcgGame.POKEMON, set.game)
        assertEquals(CatalogSeriesRef("ptcg-en-base", "Base"), set.serie)
        assertNotNull(set.logo)
    }

    @Test
    fun anUnloadedCatalogIsEmptyRatherThanBroken() = runTest {
        val catalog = CardCatalog()
        assertTrue(!catalog.isLoaded)
        assertEquals(CatalogIndex(), catalog.catalogIndex())
        assertEquals(emptyList(), catalog.search("alakazam"))
        assertNull(catalog.card("ptcg-en-base01-1"))
    }

    @Test
    fun searchRanksNameMatchesFirstAndFindsNumbers() = runTest {
        val catalog = loaded()
        assertEquals("Alakazam", catalog.search("alak").first().name)
        assertEquals("Pikachu", catalog.search("58/102").first().name)
        assertEquals("Double Colorless Energy", catalog.search("colorless").first().name)
        assertEquals(emptyList(), catalog.search("a"))
    }

    @Test
    fun aSetListsItsCardsInPrintedOrder() = runTest {
        val catalog = loaded()
        assertEquals(
            listOf("ptcg-en-base01-1", "ptcg-en-base01-2", "ptcg-en-base01-58", "ptcg-en-base01-96"),
            catalog.cardsInSet("ptcg-en-base01").map { it.id },
        )
        assertEquals("1/102", catalog.cardsInSet("ptcg-en-base01").first().collectorNumber)
    }

    @Test
    fun picturesAreStemsUnderThePublicAddress() = runTest {
        val catalog = loaded()
        val alakazam = catalog.card("ptcg-en-base01-1")!!
        val stem = alakazam.toHit().image!!
        assertEquals(alakazam.card.image, "$stem.webp")
        assertEquals("${index.publicUrl}/${alakazam.card.thumb}", CardArt.thumb(stem))
        assertEquals(alakazam.card.thumb, CardArt.cacheKey(CardArt.thumb(stem)!!))

        // Published without a picture: no image of its own, and the catalog's card back instead.
        val pikachu = catalog.card("ptcg-en-base01-58")!!.toHit()
        assertNull(pikachu.image)
        assertEquals(CardArt.stemOf(index.catalogs.single().back), pikachu.back)
        assertEquals("${index.publicUrl}/${index.catalogs.single().back!!.removeSuffix(".webp")}.thumb.webp", CardArt.thumb(pikachu.image, pikachu.back))
    }

    // ---------------------------------------------------------------- rows

    @Test
    fun aCardBecomesACardAPrintingAndAVariantPerPrinting() = runTest {
        val catalog = loaded()
        val rows = CardImport.rowsFor(catalog.card("ptcg-en-base01-1")!!, catalog)

        val card = rows.cards.getValue(CardId("ptcg-en-base01-1"))
        assertEquals("Alakazam", card.name)
        assertEquals(80, card.hp)
        assertEquals(listOf(PokemonType.PSYCHIC), card.types)
        assertEquals(listOf("Stage 2"), card.subtypes)

        val printing = rows.printings.getValue(PrintingId("ptcg-en-base01-1"))
        assertEquals("ptcg-en-base01", printing.setCode)
        assertEquals("Base Set", printing.setName)
        assertEquals("1/102", printing.collectorNumber)
        assertEquals("Rare", printing.rarity)
        assertEquals(1999, printing.releaseYear)
        assertNotNull(printing.image)
        assertEquals(4, rows.variants.size)

        variant(rows, "ptcg-en-base01-1_holo").let {
            assertEquals(Finish.HOLO, it.finish)
            assertEquals(Edition.UNLIMITED, it.edition)
            assertNull(it.special)
        }
        variant(rows, "ptcg-en-base01-1_1st-edition-holo").let {
            assertEquals(Edition.FIRST_EDITION, it.edition)
            assertNull(it.special)
        }
        assertEquals(Edition.SHADOWLESS, variant(rows, "ptcg-en-base01-1_shadowless-holo").edition)
        variant(rows, "ptcg-en-base01-1_1999-2000-copyright-holo").let {
            assertEquals(Edition.UNLIMITED, it.edition)
            assertEquals("1999-2000-copyright", it.special)
            assertEquals("1999-2000 Copyright", it.specialLabel)
        }
    }

    @Test
    fun stampsMisprintsAndOwnPicturesSurviveTheMapping() = runTest {
        val catalog = loaded()
        val rows = CardImport.rowsFor(catalog.card("ptcg-en-base01-58")!!, catalog)
        assertEquals(7, rows.variants.size)

        variant(rows, "ptcg-en-base01-58_normal-poketour-99").let {
            assertEquals(Finish.NON_HOLO, it.finish)
            assertEquals("poketour-99", it.special)
            assertEquals("PokéTour '99", it.specialLabel)
            assertNotNull(it.image, "this printing was published with a picture of its own")
        }
        variant(rows, "ptcg-en-base01-58_shadowless-normal-red-cheeks").let {
            assertEquals(Edition.SHADOWLESS, it.edition)
            assertEquals("red-cheeks", it.special)
            assertEquals("Red Cheeks", it.specialLabel)
        }

        // A brief resolves the printing's own picture first, then the card's, then the back.
        val snapshot = CollectionSnapshot(cards = rows.cards, printings = rows.printings, variants = rows.variants)
        val stamped = snapshot.brief(VariantId("ptcg-en-base01-58_normal-poketour-99"))!!
        val plain = snapshot.brief(VariantId("ptcg-en-base01-58_normal"))!!
        assertEquals(rows.variants.getValue(VariantId("ptcg-en-base01-58_normal-poketour-99")).image, stamped.art)
        assertNull(plain.art)
        assertEquals(CardArt.stemOf(index.catalogs.single().back), plain.back)
    }

    @Test
    fun setBindersTakeThePlainestPrintingAndMasterSetsTakeEveryOne() = runTest {
        val catalog = loaded()
        val hits = catalog.cardsInSet("ptcg-en-base01")

        assertEquals(
            listOf("ptcg-en-base01-1_holo", "ptcg-en-base01-2_holo", "ptcg-en-base01-58_normal", "ptcg-en-base01-96_normal"),
            CardImport.checklist(catalog, hits).map { it.printingId },
        )

        val master = CardImport.masterSet(catalog, hits).map { it.printingId }
        assertEquals(19, master.size)
        assertEquals(
            listOf(
                "ptcg-en-base01-1_holo",
                "ptcg-en-base01-1_1st-edition-holo",
                "ptcg-en-base01-1_shadowless-holo",
                "ptcg-en-base01-1_1999-2000-copyright-holo",
            ),
            master.take(4),
        )

        val (rows, variantIds) = CardImport.rowsForPockets(catalog, CardImport.masterSet(catalog, hits))
        assertEquals(4, rows.cards.size)
        assertEquals(master, variantIds.map { it.value })
    }

    @Test
    fun anAddFallsBackToAPrintingThatExists() = runTest {
        val catalog = loaded()
        val alakazam = catalog.card("ptcg-en-base01-1")!!
        assertEquals(VariantId("ptcg-en-base01-1_holo"), CardImport.preferredVariant(alakazam, Finish.REVERSE_HOLO, catalog))
        assertEquals(VariantId("ptcg-en-base01-1_holo"), CardImport.preferredVariant(alakazam, Finish.NON_HOLO, catalog))
    }

    // ---------------------------------------------------------------- sync

    @Test
    fun aRefreshCarriesCorrectionsNewPrintingsAndPrices() = runTest {
        val catalog = loaded()
        val rows = CardImport.rowsFor(catalog.card("ptcg-en-base01-1")!!, catalog)
        val shadowless = VariantId("ptcg-en-base01-1_shadowless-holo")
        val holo = VariantId("ptcg-en-base01-1_holo")
        val handMade = Printing(
            id = PrintingId("custom-promo-7"), cardId = CardId("custom-promo-mew"), setCode = "promo", setName = "Promo",
            number = "7", setTotal = null, rarity = null, illustrator = null, releaseYear = null,
        )
        // What the collection holds: an old spelling, one printing missing, and a card added by hand.
        val snapshot = CollectionSnapshot(
            cards = rows.cards.mapValues { it.value.copy(name = "Alakazm") },
            printings = rows.printings + (handMade.id to handMade),
            variants = rows.variants - shadowless,
        )
        catalog.usePrices(
            PublishedPrices.parse(
                """{"schema":2,"fetchedAt":"2026-09-14T21:05:00Z","date":"2026-09-14","currency":"USD",""" +
                    """"printings":{"ptcg-en-base01-1_holo":12345,"ptcg-en-base01-1_shadowless-holo":99999},""" +
                    """"previous":{"date":"2026-09-13","printings":{"ptcg-en-base01-1_holo":12000}}}""",
            ),
        )

        val result = CatalogSync(catalog).refresh(snapshot, nowEpochSeconds = 100)

        assertEquals(1, result.matched)
        assertEquals(1, result.unmatched)
        assertEquals("Alakazam", result.cards.getValue(CardId("ptcg-en-base01-1")).name)
        assertTrue(shadowless in result.variants, "a printing published later reaches the collection")
        assertEquals(setOf(holo, shadowless), result.prices.keys)
        result.prices.getValue(holo).let {
            assertEquals(12345, it.market.cents)
            assertEquals(12000, it.previous?.cents)
            assertEquals("2026-09-13", it.previousDate)
        }
        assertNull(result.prices.getValue(shadowless).previous)
    }

    @Test
    fun lostRowsAreRebuiltFromTheVariantIdAlone() = runTest {
        val catalog = loaded()
        val id = VariantId("ptcg-en-base01-58_shadowless-normal-red-cheeks")
        val snapshot = CollectionSnapshot(copies = mapOf(CopyId("c1") to Copy(id = CopyId("c1"), variantId = id)))

        val recovered = CatalogSync(catalog).recoverOrphans(snapshot)
        assertEquals(7, recovered.count)
        assertTrue(id in recovered.variants)
        assertEquals("Pikachu", recovered.cards.getValue(CardId("ptcg-en-base01-58")).name)
    }

    // ---------------------------------------------------------------- ids and prices

    @Test
    fun idsSayWhatTheyAre() {
        assertEquals("ptcg-en-base01-4", CatalogIds.cardOf("ptcg-en-base01-4_1st-edition-holo"))
        assertEquals("ptcg-en-base01", CatalogIds.setOf("ptcg-en-base01-4_1st-edition-holo"))
        assertEquals("ptcg-en-cel01", CatalogIds.setOf("ptcg-en-cel01-15-102_holo"))
        assertEquals("ptcg-en-cel01-15-102", CatalogIds.cardOf("ptcg-en-cel01-15-102_holo"))
        assertEquals("ptcg-en-me02.5", CatalogIds.setOf("ptcg-en-me02.5-001_normal"))
        assertEquals("", CatalogIds.setOf("custom-base-charizard"))
        assertTrue(!CatalogIds.isCatalogId("custom-base-charizard-4"))
    }

    @Test
    fun onlyTheCurrentPriceFormatIsRead() {
        assertNotNull(PublishedPrices.parse("""{"schema":2,"printings":{}}"""))
        assertNull(PublishedPrices.parse("""{"schema":1,"cards":{"base1-4":{"holofoil":100}}}"""))
        val prices = PublishedPrices.parse("""{"schema":2,"printings":{"a_holo":0,"b_holo":250}}""")!!
        assertNull(prices.cents("a_holo"), "a zero is no quote, not a free card")
        assertEquals(250, prices.cents("b_holo"))
    }

    @Test
    fun historyLinesUpWithItsDates() {
        val doc = CatalogJson.decodeFromString<PriceHistoryDoc>(
            """{"schema":2,"set":"ptcg-en-base01","dates":["2026-09-12","2026-09-13","2026-09-14"],""" +
                """"printings":{"ptcg-en-base01-1_holo":[11000,null,12345]}}""",
        )
        assertEquals(
            listOf(PricePoint("2026-09-12", 11000), PricePoint("2026-09-14", 12345)),
            doc.series(VariantId("ptcg-en-base01-1_holo")),
        )
        assertEquals(emptyList(), doc.series(VariantId("ptcg-en-base01-2_holo")))
    }
}
