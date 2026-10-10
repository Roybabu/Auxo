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
}

data class StockMovement(
    val id: Long = 0,
    val variantId: Long,
    val quantityChange: Int,
    val reason: StockChangeReason,
    val note: String? = null,
    val createdAtMillis: Long,
)
