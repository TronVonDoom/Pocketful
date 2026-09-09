package app.pocketful.data

import app.pocketful.domain.TcgGame

/**
 * Which of the app's games a slice of the TCGdex catalog belongs to.
 *
 * TCGdex serves the printed Pokémon TCG and the Pokémon TCG Pocket mobile game out of one
 * index, filing the mobile game as one more era beside Base and Scarlet & Violet. It is
 * not one more era. Its cards are digital, nothing prices them, and a chronological set
 * list that includes them puts Genetic Apex between two Scarlet & Violet releases with no
 * indication that it is not a set anybody can open a pack of.
 *
 * So the app splits the catalog in two, and this is the seam: one constant, and the three
 * shapes that carry an era through the data layer.
 */

/** The one era in the Pokémon catalog that is the mobile game rather than cardboard. */
private const val POCKET_SERIES = "tcgp"

/**
 * The game an era belongs to.
 *
 * The split is on the series id and nothing else, because that is the only field upstream
 * that actually separates them -- there is no flag saying "digital", and the set ids
 * (`A1`, `P-A`, `B2a`) follow a scheme too close to the promo and subset codes on the
 * printed side to key off safely.
 *
 * An unknown or missing era falls to the printed game. That covers the overwhelming
 * majority of the catalog, and it means the failure mode of a catalog fetched without its
 * series information is the single undivided list the app had before this existed rather
 * than fifteen printed eras filed under the mobile game.
 */
fun gameOfSeries(seriesId: String?): TcgGame =
    if (seriesId.equals(POCKET_SERIES, ignoreCase = true)) {
        TcgGame.POKEMON_POCKET
    } else {
        TcgGame.POKEMON
    }

val RemoteSeries.game: TcgGame get() = gameOfSeries(id)

val RemoteSet.game: TcgGame get() = gameOfSeries(serie?.id)

val RemoteSetDetail.game: TcgGame get() = gameOfSeries(serie?.id)
