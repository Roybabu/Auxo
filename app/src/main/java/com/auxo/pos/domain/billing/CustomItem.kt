package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money

/** What is wrong with a one-off item typed in at the counter. */
enum class CustomItemProblem { NAME_REQUIRED, PRICE_INVALID, QUANTITY_INVALID }

/** An item that is not in the shop's list yet, checked and ready to put on the bill. */
data class CustomItemDraft(
    val name: String,
    val unitPrice: Money,
    val quantity: Int,
    val gstBasisPoints: Int,
)

sealed interface CustomItemResult {
    data class Valid(val draft: CustomItemDraft) : CustomItemResult
    data class Invalid(val problems: Set<CustomItemProblem>) : CustomItemResult
}

object CustomItem {
    const val NAME_MAX = 60
    const val QUANTITY_MAX = 999

    /** Largest price accepted for one unit, in rupees. */
    private val PRICE_MAX = Money(1_00_00_000_00L)

    fun validate(name: String, price: String, quantity: String, gstBasisPoints: Int): CustomItemResult {
        val cleanName = name.trim()
        val money = Money.parseOrNull(price)?.takeIf { it > Money.ZERO && it <= PRICE_MAX }
        val count = quantity.trim().toIntOrNull()?.takeIf { it in 1..QUANTITY_MAX }
        val problems = buildSet {
            if (cleanName.isEmpty()) add(CustomItemProblem.NAME_REQUIRED)
            if (money == null) add(CustomItemProblem.PRICE_INVALID)
            if (count == null) add(CustomItemProblem.QUANTITY_INVALID)
        }
        if (problems.isNotEmpty() || money == null || count == null) return CustomItemResult.Invalid(problems)
        val rate = gstBasisPoints.coerceIn(0, GstRate.MAX_BASIS_POINTS)
        return CustomItemResult.Valid(CustomItemDraft(cleanName.take(NAME_MAX), money, count, rate))
    }
}
