package com.invision.pos.domain.model

/** Why a variant's stock changed. Every change is recorded as a [StockMovement]. */
enum class StockChangeReason {
    /** Opening stock entered when the variant was created. */
    INITIAL,
    RECEIVED,
    RETURNED,
    SOLD,
    DAMAGED,

    /** Stock count corrected after a physical count. */
    CORRECTION,

    /** Written off after the expiry date. */
    EXPIRED,

    /** Food that went bad before its expiry date. */
    SPOILED,

    /** Ingredients used up by dishes sold from the menu. */
    USED,
    ;

    /** Stock thrown away rather than sold or used. */
    val isWastage: Boolean get() = this == DAMAGED || this == EXPIRED || this == SPOILED

    companion object {
        /** Reasons offered when writing off a batch. */
        val WASTAGE: List<StockChangeReason> = listOf(EXPIRED, DAMAGED, SPOILED)
    }
}

data class StockMovement(
    val id: Long = 0,
    val variantId: Long,
    val quantityChange: Int,
    val reason: StockChangeReason,
    val note: String? = null,
    val createdAtMillis: Long,
    /** Batch the units came from or went into, for batch-tracked items. */
    val batchId: Long? = null,
    /** Sale or credit note behind the change. */
    val invoiceId: Long? = null,
    /** Goods receipt behind the change. */
    val receiptId: Long? = null,
)

/** A stock movement with the product details and references needed to list and report it. */
data class StockMovementRecord(
    val movement: StockMovement,
    val productId: Long,
    val productName: String,
    val variantLabel: String,
    val category: ProductCategory,
    val stockUnit: StockUnit,
    val batchNumber: String?,
    /** Invoice, credit note or goods receipt number. */
    val reference: String?,
    /** Current cost price of the variant, for valuing wastage. */
    val unitCost: Money?,
) {
    /** Value of the change at cost price; null when the cost is unknown. */
    val valueAtCost: Money? get() = unitCost?.let { stockUnit.costOf(movement.quantityChange, it) }
}
