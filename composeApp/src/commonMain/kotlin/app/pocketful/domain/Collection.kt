package app.pocketful.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Collection types: yours, mutable, never touched by a catalog update.
 */

/**
 * One physical card you own. Four copies of the same variant are four [Copy] rows,
 * because they can differ in condition, cost basis, and location.
 */
@Serializable
data class Copy(
    val id: CopyId,
    val variantId: VariantId,
    val condition: Condition = Condition.NEAR_MINT,
    val grade: Grade? = null,
    val acquiredPrice: Money? = null,
    val acquiredDate: String? = null,
    val notes: String? = null,
    val photoPath: String? = null,
    val location: Location = Location.Unassigned,
    /**
     * Offered in trade. A property of the physical copy rather than of the card, because
     * the whole point is that the spare is tradeable while the one in the binder is not.
     */
    val forTrade: Boolean = false,
) {
    val isGraded: Boolean get() = grade != null
}

enum class Condition(val label: String, val short: String, val multiplier: Double) {
    MINT("Mint", "M", 1.05),
    NEAR_MINT("Near Mint", "NM", 1.0),
    LIGHTLY_PLAYED("Lightly Played", "LP", 0.85),
    MODERATELY_PLAYED("Moderately Played", "MP", 0.65),
    HEAVILY_PLAYED("Heavily Played", "HP", 0.45),
    DAMAGED("Damaged", "DMG", 0.30),
}

@Serializable
data class Grade(
    val company: GradingCompany,
    val score: String,
    val certNumber: String? = null,
) {
    val label: String get() = "${company.name} $score"
}

enum class GradingCompany { PSA, BGS, CGC, SGC, TAG, ACE }

/**
 * A copy always lives somewhere, and "somewhere" is not always a binder. Modelling this
 * polymorphically is what lets a grading submission be tracked without a parallel system.
 */
@Serializable
sealed interface Location {
    @Serializable
    @SerialName("unassigned")
    data object Unassigned : Location

    @Serializable
    @SerialName("binder")
    data class BinderSlot(val binderId: BinderId, val ordinal: Int) : Location

    @Serializable
    @SerialName("container")
    data class InContainer(val containerId: ContainerId) : Location

    @Serializable
    @SerialName("grading")
    data class AtGrading(val company: GradingCompany, val submittedDate: String) : Location

    @Serializable
    @SerialName("lent")
    data class Lent(val toWhom: String, val since: String) : Location
}

/** Volatile, cached, purgeable. Kept apart so a price refresh never rewrites a copy. */
@Serializable
data class PriceSnapshot(
    val variantId: VariantId,
    val market: Money,
    val low: Money? = null,
    val high: Money? = null,
    val source: String,
    val fetchedAtEpochSeconds: Long,
)
