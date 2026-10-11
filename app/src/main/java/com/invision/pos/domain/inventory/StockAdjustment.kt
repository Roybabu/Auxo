package com.invision.pos.domain.inventory

import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockUnit

enum class StockAdjustmentError {
    QUANTITY_REQUIRED,
    QUANTITY_INVALID,
    QUANTITY_TOO_LARGE,
    WOULD_GO_NEGATIVE,
    NO_CHANGE,

    /** The item was deleted in the meantime. */
    ITEM_MISSING,

    /** Batch number too long or expiry already passed. */
    BATCH_INVALID,
}

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

    /** Ingredients aren't sold or returned at the counter; the kitchen uses them up. */
    private val ingredientReasons: List<StockChangeReason> = listOf(
        StockChangeReason.RECEIVED,
        StockChangeReason.USED,
        StockChangeReason.DAMAGED,
        StockChangeReason.CORRECTION,
    )

    fun manualReasonsFor(category: ProductCategory): List<StockChangeReason> =
        if (category.isSold) manualReasons else ingredientReasons

    /**
     * @param unit how the item's stock is counted; ingredients are typed in kg or L
     *   and changed in grams or millilitres.
     */
    fun calculate(
        reason: StockChangeReason,
        quantityText: String,
        currentStock: Int,
        unit: StockUnit = StockUnit.UNIT,
    ): StockAdjustment {
        val text = quantityText.trim()
        if (text.isEmpty()) return StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_REQUIRED)
        val quantity = if (unit == StockUnit.UNIT) {
            if (!text.all { it in '0'..'9' }) return StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_INVALID)
            val whole = text.toIntOrNull() ?: return StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_TOO_LARGE)
            if (whole > MAX_QUANTITY) return StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_TOO_LARGE)
            whole
        } else {
            unit.parse(text, allowZero = true) ?: return StockAdjustment.Invalid(
                if (DECIMAL.matches(text)) StockAdjustmentError.QUANTITY_TOO_LARGE else StockAdjustmentError.QUANTITY_INVALID,
            )
        }

        val change = when (reason) {
            StockChangeReason.RECEIVED, StockChangeReason.RETURNED, StockChangeReason.INITIAL -> quantity
            StockChangeReason.SOLD, StockChangeReason.DAMAGED, StockChangeReason.EXPIRED,
            StockChangeReason.SPOILED, StockChangeReason.USED -> -quantity
            StockChangeReason.CORRECTION -> quantity - currentStock
        }

        return when {
            reason != StockChangeReason.CORRECTION && quantity == 0 ->
                StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_INVALID)
            change == 0 -> StockAdjustment.Invalid(StockAdjustmentError.NO_CHANGE)
            currentStock.toLong() + change < 0 -> StockAdjustment.Invalid(StockAdjustmentError.WOULD_GO_NEGATIVE)
            else -> StockAdjustment.Valid(change, reason)
        }
    }

    private val DECIMAL = Regex("""\d+(\.\d+)?""")
}
