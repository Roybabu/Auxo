package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Money

/** One parked bill in the list: enough to recognise it and decide whether to resume it. */
data class HeldBillSummary(
    val id: Long,
    /** What the cashier called it (a customer name, a table); empty when they did not. */
    val label: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val itemCount: Int,
    /** The total when it was parked. Prices and stock are checked again when it is resumed. */
    val total: Money,
)

/** A parked bill with everything needed to put it back on the counter. */
data class HeldBill(val summary: HeldBillSummary, val cart: Cart)

object HeldBills {
    const val LABEL_MAX = 40

    /** The label as it is kept: one line, trimmed, not longer than [LABEL_MAX]. */
    fun cleanLabel(label: String): String = label.replace(Regex("\\s+"), " ").trim().take(LABEL_MAX)
}
