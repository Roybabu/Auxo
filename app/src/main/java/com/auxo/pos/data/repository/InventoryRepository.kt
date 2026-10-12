package com.auxo.pos.data.repository

import com.auxo.pos.domain.model.BatchDetails
import com.auxo.pos.domain.model.BatchWithProduct
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.domain.model.StockMovementRecord
import kotlinx.coroutines.flow.Flow

sealed interface SaveProductResult {
    data class Saved(val productId: Long) : SaveProductResult

    /** Barcodes or SKUs already belong to another product. */
    data class Conflict(
        val barcodesInUse: Set<String> = emptySet(),
        val skusInUse: Set<String> = emptySet(),
    ) : SaveProductResult
}

sealed interface StockChangeResult {
    data object Applied : StockChangeResult
    data object WouldGoNegative : StockChangeResult

    /** The item was deleted in the meantime. */
    data object ItemMissing : StockChangeResult
}

/** Barcode lookup result: the product and the variant the barcode belongs to. */
data class BarcodeMatch(val product: Product, val variant: ProductVariant)

interface InventoryRepository {
    fun observeProducts(): Flow<List<Product>>

    /** Emits null when the product does not exist (for example after it is deleted). */
    fun observeProduct(productId: Long): Flow<Product?>

    suspend fun getProduct(productId: Long): Product?

    fun observeStockMovements(productId: Long, limit: Int = 30): Flow<List<StockMovement>>

    suspend fun findByBarcode(barcode: String): BarcodeMatch?

    suspend fun isBarcodeInUse(barcode: String): Boolean

    /**
     * Saves a product with its variants. Every variant must already have a barcode.
     * Saved variants left out of [product] are deleted.
     */
    suspend fun saveProduct(product: Product): SaveProductResult

    suspend fun deleteProduct(productId: Long)

    /**
     * Records a manual stock change. For batch-tracked items a decrease comes out
     * of the batch that expires first, and an increase goes into the batch given by
     * [batch]. A [requestId] that was already used is ignored, so a retry can't
     * apply the change twice.
     */
    suspend fun recordStockChange(
        movement: StockMovement,
        batch: BatchDetails = BatchDetails(),
        requestId: String? = null,
    ): StockChangeResult

    /** Batches that still hold stock, earliest expiry first. */
    fun observeBatches(): Flow<List<BatchWithProduct>>

    /** Writes off units of one batch as wastage. A repeated [requestId] is ignored. */
    suspend fun writeOff(batchId: Long, quantity: Int, reason: StockChangeReason, note: String?, requestId: String): StockChangeResult

    /** Stock movements of every product between two instants, newest first. */
    fun observeMovementHistory(fromMillis: Long, toMillis: Long): Flow<List<StockMovementRecord>>
}
