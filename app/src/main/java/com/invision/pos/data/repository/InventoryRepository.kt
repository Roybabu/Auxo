package com.invision.pos.data.repository

import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductVariant
import com.invision.pos.domain.model.StockMovement
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

    suspend fun recordStockChange(movement: StockMovement): StockChangeResult
}
