package com.invision.pos.testutil

import com.invision.pos.data.repository.BarcodeMatch
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.data.repository.StockChangeResult
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/** In-memory [InventoryRepository] that mirrors the Room implementation's rules. */
class FakeInventoryRepository(initial: List<Product> = emptyList()) : InventoryRepository {

    val products = MutableStateFlow(initial)
    val movements = MutableStateFlow<List<StockMovement>>(emptyList())

    /** When true, observing products fails, to exercise error states. */
    var failReads = false

    var saveCalls = 0
        private set

    private var nextProductId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    private var nextVariantId = (initial.flatMap { it.variants }.maxOfOrNull { it.id } ?: 0L) + 100
    private var nextMovementId = 1L

    override fun observeProducts(): Flow<List<Product>> =
        if (failReads) flow { throw IOException("Simulated read failure") } else products

    override fun observeProduct(productId: Long): Flow<Product?> =
        products.map { list -> list.firstOrNull { it.id == productId } }

    override suspend fun getProduct(productId: Long): Product? {
        if (failReads) throw IOException("Simulated read failure")
        return products.value.firstOrNull { it.id == productId }
    }

    override fun observeStockMovements(productId: Long, limit: Int): Flow<List<StockMovement>> =
        combine(products, movements) { list, all ->
            val variantIds = list.firstOrNull { it.id == productId }?.variants?.map { it.id }.orEmpty().toSet()
            all.filter { it.variantId in variantIds }.sortedByDescending { it.createdAtMillis }.take(limit)
        }

    override suspend fun findByBarcode(barcode: String): BarcodeMatch? =
        products.value.firstNotNullOfOrNull { product ->
            product.variants.firstOrNull { it.barcode == barcode }?.let { BarcodeMatch(product, it) }
        }

    override suspend fun isBarcodeInUse(barcode: String): Boolean =
        products.value.any { product -> product.variants.any { it.barcode == barcode } }

    override suspend fun saveProduct(product: Product): SaveProductResult {
        saveCalls++
        require(product.variants.all { it.barcode.isNotBlank() })
        val others = products.value.filter { it.id != product.id }.flatMap { it.variants }
        val barcodes = others.map { it.barcode }.toSet()
        val skus = others.mapNotNull { it.sku }.toSet()
        val barcodeConflicts = product.variants.map { it.barcode }.filter { it in barcodes }.toSet()
        val skuConflicts = product.variants.mapNotNull { it.sku }.filter { it in skus }.toSet()
        if (barcodeConflicts.isNotEmpty() || skuConflicts.isNotEmpty()) {
            return SaveProductResult.Conflict(barcodeConflicts, skuConflicts)
        }

        val id = if (product.id == 0L) nextProductId++ else product.id
        val existing = products.value.firstOrNull { it.id == id }
        val variants = product.variants.map { variant ->
            if (variant.id == 0L) {
                val variantId = nextVariantId++
                if (variant.stockQuantity > 0) {
                    movements.value += StockMovement(
                        id = nextMovementId++,
                        variantId = variantId,
                        quantityChange = variant.stockQuantity,
                        reason = StockChangeReason.INITIAL,
                        createdAtMillis = 0,
                    )
                }
                variant.copy(id = variantId, productId = id)
            } else {
                val stock = existing?.variants?.firstOrNull { it.id == variant.id }?.stockQuantity ?: variant.stockQuantity
                variant.copy(productId = id, stockQuantity = stock)
            }
        }
        val saved = product.copy(id = id, variants = variants)
        products.value = products.value.filterNot { it.id == id } + saved
        return SaveProductResult.Saved(id)
    }

    override suspend fun deleteProduct(productId: Long) {
        products.value = products.value.filterNot { it.id == productId }
    }

    override suspend fun recordStockChange(movement: StockMovement): StockChangeResult {
        val product = products.value.firstOrNull { p -> p.variants.any { it.id == movement.variantId } }
            ?: return StockChangeResult.WouldGoNegative
        val variant = product.variants.first { it.id == movement.variantId }
        val newStock = variant.stockQuantity + movement.quantityChange
        if (newStock < 0) return StockChangeResult.WouldGoNegative
        val updated = product.copy(
            variants = product.variants.map { if (it.id == variant.id) it.copy(stockQuantity = newStock) else it },
        )
        products.value = products.value.map { if (it.id == product.id) updated else it }
        movements.value += movement.copy(id = nextMovementId++)
        return StockChangeResult.Applied
    }
}
