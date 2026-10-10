package com.invision.pos.data.repository

import com.invision.pos.data.local.dao.InventoryDao
import com.invision.pos.data.local.toDomain
import com.invision.pos.data.local.toEntity
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.StockMovement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

class RoomInventoryRepository(
    private val dao: InventoryDao,
    private val clock: Clock,
) : InventoryRepository {

    override fun observeProducts(): Flow<List<Product>> =
        dao.observeProducts().map { rows -> rows.map { it.toDomain() } }

    override fun observeProduct(productId: Long): Flow<Product?> =
        dao.observeProduct(productId).map { it?.toDomain() }

    override suspend fun getProduct(productId: Long): Product? = dao.getProduct(productId)?.toDomain()

    override fun observeStockMovements(productId: Long, limit: Int): Flow<List<StockMovement>> =
        dao.observeMovementsForProduct(productId, limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun findByBarcode(barcode: String): BarcodeMatch? {
        val variant = dao.findVariantByBarcode(barcode.trim()) ?: return null
        val product = dao.getProduct(variant.productId)?.toDomain() ?: return null
        val match = product.variants.firstOrNull { it.id == variant.id } ?: return null
        return BarcodeMatch(product, match)
    }

    override suspend fun isBarcodeInUse(barcode: String): Boolean = dao.barcodeExists(barcode)

    override suspend fun saveProduct(product: Product): SaveProductResult {
        require(product.variants.all { it.barcode.isNotBlank() }) { "Every variant needs a barcode before saving" }

        val barcodes = product.variants.map { it.barcode }
        val skus = product.variants.mapNotNull { it.sku }
        val barcodesInUse = dao.barcodesUsedByOtherProducts(barcodes, product.id).toSet()
        val skusInUse = if (skus.isEmpty()) emptySet() else dao.skusUsedByOtherProducts(skus, product.id).toSet()
        if (barcodesInUse.isNotEmpty() || skusInUse.isNotEmpty()) {
            return SaveProductResult.Conflict(barcodesInUse, skusInUse)
        }

        val now = clock.millis()
        val keptIds = product.variants.map { it.id }.filter { it != 0L }.toSet()
        val removedIds = if (product.id == 0L) {
            emptyList()
        } else {
            dao.getProduct(product.id)?.variants.orEmpty().map { it.id }.filterNot { it in keptIds }
        }

        val productId = dao.saveProduct(
            product = product.toEntity(now),
            variants = product.variants.map { it.toEntity(product.id) },
            removedVariantIds = removedIds,
            nowMillis = now,
        )
        return SaveProductResult.Saved(productId)
    }

    override suspend fun deleteProduct(productId: Long) {
        dao.deleteProduct(productId)
    }

    override suspend fun recordStockChange(movement: StockMovement): StockChangeResult =
        if (dao.adjustStock(movement.toEntity())) StockChangeResult.Applied else StockChangeResult.WouldGoNegative
}
