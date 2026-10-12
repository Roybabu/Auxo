package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Money

/**
 * Rules about who may give how much discount, and the wording of a discount reason.
 *
 * A limit is a number of basis points (1000 = 10%) of the value being discounted. Comparing is done with whole
 * numbers only (discount x 10,000 against value x limit), so there is no rounding that could let a discount
 * slip a paisa over the limit.
 */
object DiscountPolicy {
    const val REASON_MAX = 60

    /** The choices offered for the Employee limit, in basis points. 0 means Employees cannot discount. */
    val LIMIT_CHOICES: List<Int> = listOf(0, 500, 1_000, 1_500, 2_000, 2_500, 5_000, 10_000)

    /**
     * True when [discount] is more than [limitBp] of [base]. Any discount is over a limit of 0.
     *
     * A percentage discount is rounded to a whole paisa, so 10% of Rs 4.99 is 50 paise, a hair over 10%.
     * [roundingSlack] is how many paise of that rounding to forgive (one per discount added up); it is
     * never forgiven against a limit of 0.
     */
    fun exceeds(discount: Money, base: Money, limitBp: Int, roundingSlack: Long = 0): Boolean {
        if (discount <= Money.ZERO) return false
        if (base <= Money.ZERO) return true
        val limit = limitBp.coerceAtLeast(0)
        val forgiven = if (limit == 0) 0L else roundingSlack
        return (discount.minorUnits - forgiven) * 10_000 > base.minorUnits * limit
    }

    /**
     * The discount given at the counter: line discounts plus the bill discount, leaving out loyalty points,
     * which the customer earned and which are not the cashier's to give.
     */
    fun counterDiscount(totals: BillTotals): Money =
        totals.lineDiscountTotal + (totals.billDiscountTotal - totals.loyaltyDiscount)

    /** True when the discounts on this bill are more than an Employee with [limitBp] may give. */
    fun billExceeds(totals: BillTotals, limitBp: Int): Boolean =
        exceeds(counterDiscount(totals), totals.subtotal, limitBp, roundingSlack = totals.lines.size + 1L) ||
            totals.lines.any { exceeds(it.lineDiscount, it.gross, limitBp, roundingSlack = 1) }

    /** The reason as it is kept and printed: trimmed, on one line, and not longer than [REASON_MAX]. */
    fun cleanReason(reason: String): String? =
        reason.replace(Regex("\\s+"), " ").trim().take(REASON_MAX).ifEmpty { null }

    /** The reasons on a bill, once each, line reasons first then the bill's own. */
    fun reasonsOn(lineReasons: List<String?>, billReason: String?): List<String> =
        (lineReasons + billReason).filterNotNull().filter { it.isNotBlank() }.distinct()
}

/** How a limit in basis points is written for people: 500 -> "5%", 1250 -> "12.5%". */
object DiscountLimits {
    fun label(limitBp: Int): String {
        val whole = limitBp / 100
        val part = limitBp % 100
        return if (part == 0) "$whole%" else "$whole.${part.toString().padStart(2, '0').trimEnd('0')}%"
    }
}
