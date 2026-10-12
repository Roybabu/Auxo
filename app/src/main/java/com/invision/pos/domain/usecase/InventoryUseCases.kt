package com.invision.pos.domain.usecase

import com.invision.pos.data.repository.BarcodeMatch
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.data.repository.StockChangeResult
import com.invision.pos.domain.barcode.InternalBarcodeGenerator
import com.invision.pos.domain.inventory.InventorySummary
import com.invision.pos.domain.inventory.InventorySummaryCalculator
import com.invision.pos.domain.inventory.StockAdjustment
import com.invision.pos.domain.inventory.StockAdjustmentCalculator
import com.invision.pos.domain.inventory.StockAdjustmentError
import com.invision.pos.domain.model.BatchDetails
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.model.StockUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** Saves a validated product, giving variants without a barcode a unique in-store EAN-13. */
class SaveProductUseCase(
    private val repository: InventoryRepository,
    private val barcodeGenerator: InternalBarcodeGenerator,
) {
    suspend operator fun invoke(product: Product): SaveProductResult {
        val taken = product.variants.map { it.barcode }.filter { it.isNotBlank() }.toMutableSet()
        val variants = product.variants.map { variant ->
            if (variant.barcode.isNotBlank()) {
                variant
            } else {
                val code = barcodeGenerator.nextUnique(taken) { repository.isBarcodeInUse(it) }
                taken += code
                variant.copy(barcode = code)
            }
        }
        return repository.saveProduct(product.copy(variants = variants))
    }
}

sealed interface DeleteProductResult {
    data object Deleted : DeleteProductResult
    data object NotFound : DeleteProductResult

    /** Nothing was deleted: removing the product would make this much stock vanish without a stock record. */
    data class StockOnHand(val units: Int) : DeleteProductResult
}

/**
 * Deletes a product only when no stock is left. Stock on hand has to be taken out first (sold, written off or
 * corrected), so the movement history explains where it went. Past invoices keep their own copy of the product's
 * name and price, so they are not changed.
 */
class DeleteProductUseCase(private val repository: InventoryRepository) {
    suspend operator fun invoke(productId: Long): DeleteProductResult {
        val product = repository.getProduct(productId) ?: return DeleteProductResult.NotFound
        val units = product.variants.sumOf { it.stockQuantity }
        if (units > 0) return DeleteProductResult.StockOnHand(units)
        repository.deleteProduct(productId)
        return DeleteProductResult.Deleted
    }
}

sealed interface AdjustStockResult {
    data object Done : AdjustStockResult
    data class Rejected(val error: StockAdjustmentError) : AdjustStockResult
}

class AdjustStockUseCase(
    private val repository: InventoryRepository,
    private val clock: Clock,
) {
    /**
     * @param unit how the item's stock is counted (ingredients: g or ml, typed in kg or L)
     * @param batch for batch-tracked items, the batch an increase goes into
     * @param requestId one id per dialog, so a repeated save can't apply twice
     */
    suspend operator fun invoke(
        variant: ProductVariant,
        reason: StockChangeReason,
        quantityText: String,
        note: String,
        unit: StockUnit = StockUnit.UNIT,
        batch: BatchDetails = BatchDetails(),
        requestId: String? = null,
    ): AdjustStockResult {
        val adjustment = StockAdjustmentCalculator.calculate(reason, quantityText, variant.stockQuantity, unit)
        if (adjustment is StockAdjustment.Invalid) return AdjustStockResult.Rejected(adjustment.error)
        adjustment as StockAdjustment.Valid

        val movement = StockMovement(
            variantId = variant.id,
            quantityChange = adjustment.quantityChange,
            reason = adjustment.reason,
            note = note.trim().ifEmpty { null },
            createdAtMillis = clock.millis(),
        )
        return when (repository.recordStockChange(movement, batch, requestId)) {
            StockChangeResult.Applied -> AdjustStockResult.Done
            // Stock changed elsewhere since the dialog opened.
            StockChangeResult.WouldGoNegative -> AdjustStockResult.Rejected(StockAdjustmentError.WOULD_GO_NEGATIVE)
            StockChangeResult.ItemMissing -> AdjustStockResult.Rejected(StockAdjustmentError.ITEM_MISSING)
        }
    }
}

class ObserveInventorySummaryUseCase(
    private val repository: InventoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<InventorySummary> =
        repository.observeProducts().map { products ->
            InventorySummaryCalculator.calculate(products, LocalDate.now(clock))
        }
}

class FindByBarcodeUseCase(private val repository: InventoryRepository) {
    suspend operator fun invoke(barcode: String): BarcodeMatch? {
        val code = barcode.trim()
        return if (code.isEmpty()) null else repository.findByBarcode(code)
    }
}
