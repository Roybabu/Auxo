package com.invision.pos.domain.inventory

import com.invision.pos.domain.model.StockChangeReason

enum class StockAdjustmentError { QUANTITY_REQUIRED, QUANTITY_INVALID, QUANTITY_TOO_LARGE, WOULD_GO_NEGATIVE, NO_CHANGE }

sealed interface StockAdjustment {
    data class Valid(val quantityChange: Int, val reason: StockChangeReason) : StockAdjustment
    data class Invalid(val error: StockAdjustmentError) : StockAdjustment
}

/**
 * Turns what the user typed into a signed stock change.
 *
 * RECEIVED and RETURNED add stock, SOLD and DAMAGED remove it, and CORRECTION
 * sets the counted quantity (the change is the difference from current stock).
 */
object StockAdjustmentCalculator {

    const val MAX_QUANTITY = 1_000_000

    /** Reasons the user can pick when adjusting stock by hand. */
    val manualReasons: List<StockChangeReason> = listOf(
        StockChangeReason.RECEIVED,
        StockChangeReason.RETURNED,
        StockChangeReason.SOLD,
        StockChangeReason.DAMAGED,
        StockChangeReason.CORRECTION,
    )

    fun calculate(reason: StockChangeReason, quantityText: String, currentStock: Int): StockAdjustment {
        val text = quantityText.trim()
        if (text.isEmpty()) return StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_REQUIRED)
        if (!text.all { it in '0'..'9' }) return StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_INVALID)
        val quantity = text.toIntOrNull() ?: return StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_TOO_LARGE)
        if (quantity > MAX_QUANTITY) return StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_TOO_LARGE)

        val change = when (reason) {
            StockChangeReason.RECEIVED, StockChangeReason.RETURNED, StockChangeReason.INITIAL -> quantity
            StockChangeReason.SOLD, StockChangeReason.DAMAGED -> -quantity
            StockChangeReason.CORRECTION -> quantity - currentStock
        }

        return when {
            reason != StockChangeReason.CORRECTION && quantity == 0 ->
                StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_INVALID)
            change == 0 -> StockAdjustment.Invalid(StockAdjustmentError.NO_CHANGE)
            currentStock + change < 0 -> StockAdjustment.Invalid(StockAdjustmentError.WOULD_GO_NEGATIVE)
            else -> StockAdjustment.Valid(change, reason)
        }
    }
}
