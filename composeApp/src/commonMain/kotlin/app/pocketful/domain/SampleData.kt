package app.pocketful.domain

/**
 * Placeholder data so the binder UI can be built and judged before the catalog pipeline
 * exists. Prices are illustrative, not real market data.
 *
 * The catalog here is deliberately wider than what the binders hold. Card search is only
 * honest to test against a catalog with more in it than you own -- with a dozen entries
 * every query matches and the ranking never has to be right.
 */
object SampleData {

    private data class SetInfo(val code: String, val name: String, val total: String, val year: Int)

    private val BaseSet = SetInfo("base1", "Base Set", "102", 1999)
    private val Jungle = SetInfo("jungle", "Jungle", "64", 1999)
    private val Fossil = SetInfo("fossil", "Fossil", "62", 1999)
    private val Skies = SetInfo("evs", "Evolving Skies", "203", 2021)
    private val OneFiftyOne = SetInfo("sv351", "Scarlet & Violet 151", "165", 2023)

    private class Builder {
        val cards = mutableMapOf<CardId, Card>()
        val printings = mutableMapOf<PrintingId, Printing>()
        val variants = mutableMapOf<VariantId, Variant>()
        val copies = mutableMapOf<CopyId, Copy>()
        val prices = mutableMapOf<VariantId, PriceSnapshot>()

        /** Registers a card, printing and variant in one go and returns the variant id. */
        fun card(
            set: SetInfo,
            number: String,
            name: String,
            hp: Int?,
            type: PokemonType?,
            rarity: String,
            finish: Finish,
            priceDollars: Double,
            edition: Edition = Edition.UNLIMITED,
            supertype: Supertype = Supertype.POKEMON,
        ): VariantId {
            val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            val cardId = CardId("ptcg-${set.code}-$slug")
            val printingId = PrintingId("ptcg-${set.code}-${number.padStart(3, '0')}")
            val variantSuffix = when (edition) {
                Edition.FIRST_EDITION -> "1sted"
                else -> finish.name.lowercase()
            }
            val variantId = VariantId("${printingId.value}-$variantSuffix")

            cards[cardId] = Card(
                id = cardId,
                name = name,
                supertype = supertype,
                hp = hp,
                types = listOfNotNull(type),
            )
            printings[printingId] = Printing(
                id = printingId,
                cardId = cardId,
                setCode = set.code,
                setName = set.name,
                number = number,
                setTotal = set.total,
                rarity = rarity,
                illustrator = null,
                releaseYear = set.year,
            )
            variants[variantId] = Variant(
                id = variantId,
                printingId = printingId,
                finish = finish,
                edition = edition,
            )
            prices[variantId] = PriceSnapshot(
                variantId = variantId,
                market = Money.dollars(priceDollars),
                source = "sample",
                fetchedAtEpochSeconds = 0L,
            )
            return variantId
        }

        /** Records a physical copy of a variant and returns a slot holding it. */
        fun own(
            variantId: VariantId,
            condition: Condition = Condition.NEAR_MINT,
            paid: Double? = null,
            grade: Grade? = null,
            notes: String? = null,
        ): SlotContent {
            val copyId = CopyId("copy-" + (copies.size + 1))
            copies[copyId] = Copy(
                id = copyId,
                variantId = variantId,
                condition = condition,
                grade = grade,
                acquiredPrice = paid?.let { Money.dollars(it) },
                notes = notes,
            )
            return SlotContent.Filled(copyId)
        }

        fun want(variantId: VariantId): SlotContent = SlotContent.Wanted(variantId)

        /** Records a copy that lives in a container rather than a pocket. */
        fun stored(
            variantId: VariantId,
            condition: Condition = Condition.NEAR_MINT,
            paid: Double? = null,
            grade: Grade? = null,
        ): CopyId {
            val copyId = CopyId("copy-" + (copies.size + 1))
            copies[copyId] = Copy(
                id = copyId,
                variantId = variantId,
                condition = condition,
                grade = grade,
                acquiredPrice = paid?.let { Money.dollars(it) },
            )
            return copyId
        }
    }

    val snapshot: CollectionSnapshot = run {
        val b = Builder()

        // ------------------------------------------------------------- Base Set
        val alakazam = b.card(BaseSet, "1", "Alakazam", 80, PokemonType.PSYCHIC, "Rare Holo", Finish.HOLO, 34.0)
        val blastoise = b.card(BaseSet, "2", "Blastoise", 100, PokemonType.WATER, "Rare Holo", Finish.HOLO, 118.0)
        val chansey = b.card(BaseSet, "3", "Chansey", 120, PokemonType.COLORLESS, "Rare Holo", Finish.HOLO, 38.0)
        val charizard = b.card(BaseSet, "4", "Charizard", 120, PokemonType.FIRE, "Rare Holo", Finish.HOLO, 412.0)
        val clefairy = b.card(BaseSet, "5", "Clefairy", 40, PokemonType.FAIRY, "Rare Holo", Finish.HOLO, 41.0)
        val gyarados = b.card(BaseSet, "6", "Gyarados", 100, PokemonType.WATER, "Rare Holo", Finish.HOLO, 44.0)
        val hitmonchan = b.card(BaseSet, "7", "Hitmonchan", 70, PokemonType.FIGHTING, "Rare Holo", Finish.HOLO, 36.0)
        val machamp = b.card(
            BaseSet, "8", "Machamp", 100, PokemonType.FIGHTING, "Rare Holo", Finish.HOLO, 22.0,
            edition = Edition.FIRST_EDITION,
        )
        val magneton = b.card(BaseSet, "9", "Magneton", 60, PokemonType.LIGHTNING, "Rare Holo", Finish.HOLO, 31.0)
        val mewtwo = b.card(BaseSet, "10", "Mewtwo", 60, PokemonType.PSYCHIC, "Rare Holo", Finish.HOLO, 58.0)
        val nidoking = b.card(BaseSet, "11", "Nidoking", 90, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 39.0)
        val ninetales = b.card(BaseSet, "12", "Ninetales", 80, PokemonType.FIRE, "Rare Holo", Finish.HOLO, 42.0)
        val poliwrath = b.card(BaseSet, "13", "Poliwrath", 90, PokemonType.WATER, "Rare Holo", Finish.HOLO, 33.0)
        val raichu = b.card(BaseSet, "14", "Raichu", 80, PokemonType.LIGHTNING, "Rare Holo", Finish.HOLO, 46.0)
        val venusaur = b.card(BaseSet, "15", "Venusaur", 100, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 96.0)
        val zapdos = b.card(BaseSet, "16", "Zapdos", 90, PokemonType.LIGHTNING, "Rare Holo", Finish.HOLO, 52.0)
        val beedrill = b.card(BaseSet, "17", "Beedrill", 80, PokemonType.GRASS, "Rare", Finish.NON_HOLO, 4.0)
        val dragonair = b.card(BaseSet, "18", "Dragonair", 80, PokemonType.COLORLESS, "Rare", Finish.NON_HOLO, 6.0)
        val dugtrio = b.card(BaseSet, "19", "Dugtrio", 70, PokemonType.FIGHTING, "Rare", Finish.NON_HOLO, 3.5)
        val electabuzz = b.card(BaseSet, "20", "Electabuzz", 70, PokemonType.LIGHTNING, "Rare", Finish.NON_HOLO, 5.0)
        val electrode = b.card(BaseSet, "21", "Electrode", 80, PokemonType.LIGHTNING, "Rare", Finish.NON_HOLO, 3.0)
        val pidgeotto = b.card(BaseSet, "22", "Pidgeotto", 60, PokemonType.COLORLESS, "Rare", Finish.NON_HOLO, 3.0)
        val arcanine = b.card(BaseSet, "23", "Arcanine", 100, PokemonType.FIRE, "Uncommon", Finish.NON_HOLO, 4.5)
        val charmeleon = b.card(BaseSet, "24", "Charmeleon", 80, PokemonType.FIRE, "Uncommon", Finish.NON_HOLO, 6.0)
        val professorOak = b.card(
            BaseSet, "88", "Professor Oak", null, null, "Uncommon", Finish.NON_HOLO, 12.0,
            supertype = Supertype.TRAINER,
        )

        // --------------------------------------------------------------- Jungle
        val scyther = b.card(Jungle, "10", "Scyther", 70, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 26.0)
        val snorlax = b.card(Jungle, "11", "Snorlax", 90, PokemonType.COLORLESS, "Rare Holo", Finish.HOLO, 48.0)
        val vaporeon = b.card(Jungle, "12", "Vaporeon", 80, PokemonType.WATER, "Rare Holo", Finish.HOLO, 34.0)
        val venomoth = b.card(Jungle, "13", "Venomoth", 70, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 18.0)
        val victreebel = b.card(Jungle, "14", "Victreebel", 80, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 17.0)
        val vileplume = b.card(Jungle, "15", "Vileplume", 80, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 22.0)
        val wigglytuff = b.card(Jungle, "16", "Wigglytuff", 80, PokemonType.FAIRY, "Rare Holo", Finish.HOLO, 24.0)
        val clefable = b.card(Jungle, "1", "Clefable", 70, PokemonType.FAIRY, "Rare Holo", Finish.HOLO, 28.0)
        val flareon = b.card(Jungle, "3", "Flareon", 70, PokemonType.FIRE, "Rare Holo", Finish.HOLO, 36.0)
        val jolteon = b.card(Jungle, "4", "Jolteon", 70, PokemonType.LIGHTNING, "Rare Holo", Finish.HOLO, 35.0)
        val kangaskhan = b.card(Jungle, "5", "Kangaskhan", 90, PokemonType.COLORLESS, "Rare Holo", Finish.HOLO, 27.0)
        val mrMime = b.card(Jungle, "6", "Mr. Mime", 40, PokemonType.PSYCHIC, "Rare Holo", Finish.HOLO, 21.0)
        val nidoqueen = b.card(Jungle, "7", "Nidoqueen", 90, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 23.0)
        val pinsir = b.card(Jungle, "9", "Pinsir", 60, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 20.0)

        // --------------------------------------------------------------- Fossil
        val aerodactyl = b.card(Fossil, "1", "Aerodactyl", 60, PokemonType.FIGHTING, "Rare Holo", Finish.HOLO, 25.0)
        val articuno = b.card(Fossil, "2", "Articuno", 70, PokemonType.WATER, "Rare Holo", Finish.HOLO, 44.0)
        val ditto = b.card(Fossil, "3", "Ditto", 50, PokemonType.COLORLESS, "Rare Holo", Finish.HOLO, 29.0)
        val dragonite = b.card(Fossil, "4", "Dragonite", 100, PokemonType.COLORLESS, "Rare Holo", Finish.HOLO, 62.0)
        val gengar = b.card(Fossil, "5", "Gengar", 80, PokemonType.PSYCHIC, "Rare Holo", Finish.HOLO, 74.0)
        val haunter = b.card(Fossil, "6", "Haunter", 60, PokemonType.PSYCHIC, "Rare Holo", Finish.HOLO, 19.0)
        val hitmonlee = b.card(Fossil, "7", "Hitmonlee", 60, PokemonType.FIGHTING, "Rare Holo", Finish.HOLO, 22.0)
        val hypno = b.card(Fossil, "8", "Hypno", 90, PokemonType.PSYCHIC, "Rare Holo", Finish.HOLO, 20.0)
        val kabutops = b.card(Fossil, "9", "Kabutops", 60, PokemonType.FIGHTING, "Rare Holo", Finish.HOLO, 31.0)
        val lapras = b.card(Fossil, "10", "Lapras", 80, PokemonType.WATER, "Rare Holo", Finish.HOLO, 38.0)
        val moltres = b.card(Fossil, "12", "Moltres", 70, PokemonType.FIRE, "Rare Holo", Finish.HOLO, 40.0)
        val muk = b.card(Fossil, "13", "Muk", 70, PokemonType.GRASS, "Rare Holo", Finish.HOLO, 26.0)
        val zapdosFossil = b.card(Fossil, "15", "Zapdos", 80, PokemonType.LIGHTNING, "Rare Holo", Finish.HOLO, 42.0)

        // ------------------------------------------------------- Evolving Skies
        val umbreonVmax = b.card(Skies, "215", "Umbreon VMAX", 310, PokemonType.DARKNESS, "Secret Rare", Finish.FULL_ART, 640.0)
        val rayquazaVmax = b.card(Skies, "218", "Rayquaza VMAX", 320, PokemonType.DRAGON, "Secret Rare", Finish.FULL_ART, 210.0)
        val sylveonVmax = b.card(Skies, "212", "Sylveon VMAX", 320, PokemonType.PSYCHIC, "Secret Rare", Finish.FULL_ART, 145.0)
        val glaceonVmax = b.card(Skies, "209", "Glaceon VMAX", 310, PokemonType.WATER, "Secret Rare", Finish.FULL_ART, 120.0)
        val leafeonVmax = b.card(Skies, "205", "Leafeon VMAX", 320, PokemonType.GRASS, "Secret Rare", Finish.FULL_ART, 96.0)
        val espeonV = b.card(Skies, "64", "Espeon V", 210, PokemonType.PSYCHIC, "Ultra Rare", Finish.FULL_ART, 58.0)
        val dragoniteV = b.card(Skies, "191", "Dragonite V", 220, PokemonType.DRAGON, "Ultra Rare", Finish.FULL_ART, 44.0)
        val duraludonVmax = b.card(Skies, "123", "Duraludon VMAX", 320, PokemonType.METAL, "Rare Holo", Finish.HOLO, 18.0)

        // ------------------------------------------------------------------ 151
        val charizardEx = b.card(OneFiftyOne, "199", "Charizard ex", 330, PokemonType.FIRE, "Special Illustration", Finish.FULL_ART, 178.0)
        val blastoiseEx = b.card(OneFiftyOne, "201", "Blastoise ex", 330, PokemonType.WATER, "Special Illustration", Finish.FULL_ART, 92.0)
        val venusaurEx = b.card(OneFiftyOne, "198", "Venusaur ex", 340, PokemonType.GRASS, "Special Illustration", Finish.FULL_ART, 68.0)
        val mewEx = b.card(OneFiftyOne, "205", "Mew ex", 180, PokemonType.PSYCHIC, "Special Illustration", Finish.FULL_ART, 132.0)
        val alakazamEx = b.card(OneFiftyOne, "202", "Alakazam ex", 310, PokemonType.PSYCHIC, "Special Illustration", Finish.FULL_ART, 54.0)
        val zapdosEx = b.card(OneFiftyOne, "145", "Zapdos ex", 250, PokemonType.LIGHTNING, "Ultra Rare", Finish.TEXTURED, 26.0)
        val erika = b.card(
            OneFiftyOne, "203", "Erika's Invitation", null, null, "Special Illustration", Finish.FULL_ART, 46.0,
            supertype = Supertype.TRAINER,
        )
        val pikachu151 = b.card(OneFiftyOne, "25", "Pikachu", 60, PokemonType.LIGHTNING, "Illustration Rare", Finish.REVERSE_HOLO, 22.0)
        val eeveeReverse = b.card(OneFiftyOne, "133", "Eevee", 70, PokemonType.COLORLESS, "Common", Finish.REVERSE_HOLO, 3.0)

        // ------------------------------------------------------------- binders

        val baseSetBinder = Binder(
            id = BinderId("binder-base-set"),
            name = "Base Set",
            subtitle = "1999 · Unlimited master set",
            layout = BinderLayout.POCKET_9,
            sheetCount = 6,
            spineColor = 0xFFE0483B,
            slots = listOf(
                b.own(alakazam, Condition.NEAR_MINT, paid = 28.0),
                b.own(blastoise, Condition.LIGHTLY_PLAYED, paid = 90.0),
                b.own(chansey, Condition.NEAR_MINT, paid = 30.0),
                b.own(
                    charizard, Condition.NEAR_MINT, paid = 240.0,
                    grade = Grade(GradingCompany.PSA, "8", "94827361"),
                    notes = "Bought at a local show, 2023.",
                ),
                b.own(clefairy, Condition.NEAR_MINT, paid = 35.0),
                b.own(gyarados, Condition.MODERATELY_PLAYED, paid = 22.0),
                b.own(hitmonchan, Condition.NEAR_MINT, paid = 31.0),
                b.own(machamp, Condition.NEAR_MINT, paid = 18.0),
                b.own(magneton, Condition.LIGHTLY_PLAYED, paid = 24.0),

                b.own(mewtwo, Condition.NEAR_MINT, paid = 44.0),
                b.want(nidoking),
                b.own(ninetales, Condition.NEAR_MINT, paid = 36.0),
                b.want(poliwrath),
                b.own(raichu, Condition.LIGHTLY_PLAYED, paid = 30.0),
                b.own(venusaur, Condition.NEAR_MINT, paid = 74.0),
                b.want(zapdos),
                b.own(beedrill, Condition.NEAR_MINT, paid = 3.0),
                b.own(dragonair, Condition.NEAR_MINT, paid = 5.0),

                b.own(dugtrio, Condition.NEAR_MINT, paid = 3.0),
                b.own(electabuzz, Condition.NEAR_MINT, paid = 4.0),
                b.own(electrode, Condition.LIGHTLY_PLAYED, paid = 2.0),
                b.own(pidgeotto, Condition.NEAR_MINT, paid = 2.5),
                b.own(arcanine, Condition.NEAR_MINT, paid = 4.0),
                b.own(charmeleon, Condition.NEAR_MINT, paid = 5.0),
                b.own(professorOak, Condition.MODERATELY_PLAYED, paid = 8.0),
                SlotContent.Spacer("trainers"),
                SlotContent.Empty,
            ),
        )

        val vintageBinder = Binder(
            id = BinderId("binder-jungle-fossil"),
            name = "Jungle & Fossil",
            subtitle = "Holo runs, working set",
            layout = BinderLayout.POCKET_9,
            sheetCount = 5,
            spineColor = 0xFF4F9E5C,
            slots = listOf(
                b.own(clefable, Condition.NEAR_MINT, paid = 22.0),
                b.own(flareon, Condition.NEAR_MINT, paid = 30.0),
                b.own(jolteon, Condition.NEAR_MINT, paid = 29.0),
                b.own(kangaskhan, Condition.LIGHTLY_PLAYED, paid = 19.0),
                b.own(mrMime, Condition.NEAR_MINT, paid = 17.0),
                b.own(nidoqueen, Condition.NEAR_MINT, paid = 18.0),
                b.own(pinsir, Condition.NEAR_MINT, paid = 16.0),
                b.own(scyther, Condition.NEAR_MINT, paid = 20.0),
                b.own(snorlax, Condition.NEAR_MINT, paid = 40.0),

                b.own(vaporeon, Condition.NEAR_MINT, paid = 28.0),
                b.own(venomoth, Condition.LIGHTLY_PLAYED, paid = 12.0),
                b.want(victreebel),
                b.own(vileplume, Condition.NEAR_MINT, paid = 18.0),
                b.own(wigglytuff, Condition.NEAR_MINT, paid = 19.0),
                SlotContent.Spacer("fossil"),
                b.own(aerodactyl, Condition.NEAR_MINT, paid = 20.0),
                b.own(articuno, Condition.NEAR_MINT, paid = 36.0),
                b.own(ditto, Condition.NEAR_MINT, paid = 24.0),

                b.own(dragonite, Condition.NEAR_MINT, paid = 52.0),
                b.own(gengar, Condition.LIGHTLY_PLAYED, paid = 55.0),
                b.want(haunter),
                b.own(hitmonlee, Condition.NEAR_MINT, paid = 18.0),
                b.want(hypno),
                b.own(kabutops, Condition.NEAR_MINT, paid = 26.0),
                b.own(lapras, Condition.NEAR_MINT, paid = 31.0),
                b.want(moltres),
                b.own(muk, Condition.NEAR_MINT, paid = 21.0),

                b.own(zapdosFossil, Condition.NEAR_MINT, paid = 34.0),
            ),
        )

        val modernBinder = Binder(
            id = BinderId("binder-modern"),
            name = "Modern Chase",
            subtitle = "Alt arts and secret rares",
            layout = BinderLayout.POCKET_12,
            sheetCount = 4,
            spineColor = 0xFF6D5BD0,
            slots = listOf(
                b.own(umbreonVmax, Condition.MINT, paid = 480.0, notes = "Sealed from pack, straight to a sleeve."),
                b.own(rayquazaVmax, Condition.NEAR_MINT, paid = 180.0),
                b.want(sylveonVmax),
                b.own(glaceonVmax, Condition.NEAR_MINT, paid = 105.0),
                b.want(leafeonVmax),
                b.own(espeonV, Condition.NEAR_MINT, paid = 50.0),
                b.own(dragoniteV, Condition.NEAR_MINT, paid = 38.0),
                b.own(duraludonVmax, Condition.NEAR_MINT, paid = 15.0),
                SlotContent.Spacer("151"),
                b.own(charizardEx, Condition.MINT, paid = 150.0),
                b.want(blastoiseEx),
                b.own(venusaurEx, Condition.NEAR_MINT, paid = 60.0),

                b.own(mewEx, Condition.NEAR_MINT, paid = 118.0),
                b.own(alakazamEx, Condition.NEAR_MINT, paid = 48.0),
                b.own(zapdosEx, Condition.NEAR_MINT, paid = 24.0),
                b.want(erika),
                b.own(pikachu151, Condition.NEAR_MINT, paid = 19.0),
                b.own(eeveeReverse, Condition.NEAR_MINT, paid = 2.0),
            ),
        )

        // ---------------------------------------------------------- containers

        val gradedCase = Container(
            id = ContainerId("container-slabs"),
            name = "Graded slabs",
            subtitle = "Cased, out of the binder",
            kind = ContainerKind.SLAB,
            color = 0xFFE3B45C,
            copyIds = listOf(
                b.stored(charizardEx, Condition.MINT, paid = 150.0, grade = Grade(GradingCompany.PSA, "10", "72610044")),
                b.stored(umbreonVmax, Condition.MINT, paid = 470.0, grade = Grade(GradingCompany.CGC, "9.5")),
            ),
        )

        val bulkBox = Container(
            id = ContainerId("container-bulk"),
            name = "Bulk box",
            subtitle = "Sorted by set, waiting on pages",
            kind = ContainerKind.BOX,
            color = 0xFF64748B,
            copyIds = listOf(
                b.stored(pidgeotto, paid = 2.0),
                b.stored(electabuzz, paid = 3.5),
                b.stored(eeveeReverse, paid = 2.0),
                b.stored(dugtrio, Condition.LIGHTLY_PLAYED, paid = 2.0),
            ),
        )

        val leagueDeck = Container(
            id = ContainerId("container-deck"),
            name = "League deck",
            subtitle = "Whatever is sleeved this week",
            kind = ContainerKind.DECK,
            color = 0xFF2E9C97,
            copyIds = listOf(
                b.stored(dragoniteV, paid = 34.0),
                b.stored(zapdosEx, paid = 22.0),
            ),
        )

        // Deliberately empty, and deliberately not 9-pocket: the shelf, the cover art and
        // the page view all have to look right for a binder nobody has filled yet.
        val tradeBinder = Binder(
            id = BinderId("binder-trades"),
            name = "Trade Stock",
            subtitle = "Four across, three down",
            layout = BinderLayout.POCKET_12,
            sheetCount = 4,
            spineColor = 0xFFC08A4A,
            slots = emptyList(),
        )

        CollectionSnapshot(
            cards = b.cards,
            printings = b.printings,
            variants = b.variants,
            copies = b.copies,
            prices = b.prices,
            binders = listOf(baseSetBinder, vintageBinder, modernBinder, tradeBinder),
            containers = listOf(gradedCase, bulkBox, leagueDeck),
        )
    }
}
