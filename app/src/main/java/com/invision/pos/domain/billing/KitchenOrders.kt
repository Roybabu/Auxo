package com.invision.pos.domain.billing

/** How an order is served. Only restaurants use it. */
enum class ServiceType { DINE_IN, TAKEAWAY }

/** One item on a kitchen ticket. */
data class KotLine(val name: String, val variant: String, val quantity: Int)

/** An item and how many of it the kitchen has already been told about. */
data class SentItem(val key: Long, val name: String, val variant: String, val quantity: Int)

/** What the kitchen has not heard yet: new items, and items the table no longer wants. */
data class KotDelta(val added: List<KotLine>, val cancelled: List<KotLine>) {
    val isEmpty: Boolean get() = added.isEmpty() && cancelled.isEmpty()
}

object KitchenOrders {
    const val TABLE_MAX = 10

    /** A table number as it is kept and compared: one line, trimmed, upper case, not longer than [TABLE_MAX]. */
    fun normalizeTable(text: String): String = text.replace(Regex("\\s+"), " ").trim().uppercase().take(TABLE_MAX)

    /** What the kitchen has been told so far for [lines]: everything currently ordered. */
    fun sentFrom(lines: List<CartLine>): List<SentItem> =
        lines.map { SentItem(it.variantId, it.productName, it.variantLabel, it.quantity) }

    /**
     * The difference between the bill now and what the kitchen was already told. An item with more units
     * than before is sent for the extra units only; one with fewer, or removed, is listed as cancelled.
     */
    fun delta(lines: List<CartLine>, sent: List<SentItem>): KotDelta {
        val before = sent.associateBy { it.key }
        val added = lines.mapNotNull { line ->
            val extra = line.quantity - (before[line.variantId]?.quantity ?: 0)
            if (extra > 0) KotLine(line.productName, line.variantLabel, extra) else null
        }
        val now = lines.associateBy { it.variantId }
        val cancelled = sent.mapNotNull { item ->
            val fewer = item.quantity - (now[item.key]?.quantity ?: 0)
            if (fewer > 0) KotLine(item.name, item.variant, fewer) else null
        }
        return KotDelta(added, cancelled)
    }
}

/** Everything printed on a kitchen order ticket (KOT). */
data class KitchenTicket(
    val shopName: String,
    val kotNumber: String,
    val serviceType: ServiceType,
    /** The table of a dine-in order. */
    val table: String?,
    /** The customer name or other label the cashier gave the order. */
    val label: String,
    val delta: KotDelta,
    /** True when this ticket is only the items added to a table the kitchen already knew about. */
    val isAddOn: Boolean,
    val createdAtMillis: Long,
)

/** What happened when an order was placed with the kitchen. */
sealed interface PlaceOrderResult {
    /** [isAddOn] is true when the table already had items sent earlier, so this ticket is for the extras. */
    data class Placed(
        val orderId: Long,
        val kotNumber: String,
        val delta: KotDelta,
        val isAddOn: Boolean,
        val kotCount: Int,
    ) : PlaceOrderResult

    /** Another open order already uses this table; [orderId] is that order. Nothing was saved. */
    data class TableInUse(val orderId: Long) : PlaceOrderResult

    /** Nothing changed since the last ticket. Nothing was saved. */
    data object NothingNew : PlaceOrderResult
}
