package com.auxo.pos.data.repository

import androidx.room.withTransaction
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.MissingItemException
import com.auxo.pos.data.local.MovementInfo
import com.auxo.pos.data.local.RequestClaim
import com.auxo.pos.data.local.StockWriter
import com.auxo.pos.data.local.claim
import com.auxo.pos.data.local.dao.StockShortageException
import com.auxo.pos.data.local.toDomain
import com.auxo.pos.data.local.toEntity
import com.auxo.pos.domain.barcode.BarcodeRules
import com.auxo.pos.domain.model.BatchDetails
import com.auxo.pos.domain.model.BatchWithProduct
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.domain.model.StockMovementRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

class RoomInventoryRepository(
    private val database: AuxoDatabase,
    private val clock: Clock,
) : InventoryRepository {

    private val dao = database.inventoryDao()
    private val operations = database.operationDao()
    private val stock = StockWriter(dao)

    override fun observeProducts(): Flow<List<Product>> =
        dao.observeProducts().map { rows -> rows.map { it.toDomain() } }

    override fun observeProduct(productId: Long): Flow<Product?> =
        dao.observeProduct(productId).map { it?.toDomain() }

    override suspend fun getProduct(productId: Long): Product? = dao.getProduct(productId)?.toDomain()

    override fun observeStockMovements(productId: Long, limit: Int): Flow<List<StockMovement>> =
        dao.observeMovementsForProduct(productId, limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun findByBarcode(barcode: String): BarcodeMatch? {
        val variant = BarcodeRules.equivalents(barcode).firstNotNullOfOrNull { dao.findVariantByBarcode(it) } ?: return null
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
            dao.getProduct(product.id)?.variants.orEmpty().map { it.variant.id }.filterNot { it in keptIds }
        }

        // A product saved without a category (an import, an older caller) goes under the built-in one for its kind.
        val categoryId = product.categoryId ?: database.categoryDao().builtInFor(product.category.name)
        val productId = dao.saveProduct(
            product = product.copy(categoryId = categoryId).toEntity(now),
            variants = product.variants.map { it.toEntity(product.id) },
            removedVariantIds = removedIds,
            nowMillis = now,
        )
        return SaveProductResult.Saved(productId)
    }

    override suspend fun deleteProduct(productId: Long) {
        dao.deleteProduct(productId)
    }

    override suspend fun recordStockChange(movement: StockMovement, batch: BatchDetails, requestId: String?): StockChangeResult =
        try {
            database.withTransaction {
                val firstTime = requestId == null || operations.claim(requestId, "STOCK_CHANGE", clock.millis()) is RequestClaim.New
                val info = MovementInfo(movement.reason, movement.createdAtMillis, movement.note)
                if (firstTime && movement.quantityChange > 0) {
                    stock.add(movement.variantId, movement.quantityChange, info, batch)
                } else if (firstTime && movement.quantityChange < 0) {
                    stock.remove(movement.variantId, -movement.quantityChange, info)
                }
            }
            StockChangeResult.Applied
        } catch (e: StockShortageException) {
            StockChangeResult.WouldGoNegative
        } catch (e: MissingItemException) {
            StockChangeResult.ItemMissing
        }

    override fun observeBatches(): Flow<List<BatchWithProduct>> =
        dao.observeBatchesWithStock().map { rows -> rows.map { it.toDomain() } }

    override suspend fun writeOff(
        batchId: Long,
        quantity: Int,
        reason: StockChangeReason,
        note: String?,
        requestId: String,
    ): StockChangeResult = try {
        database.withTransaction {
            val now = clock.millis()
            if (operations.claim(requestId, "WRITE_OFF", now) is RequestClaim.New) {
                stock.removeFromBatch(batchId, quantity, MovementInfo(reason, now, note))
            }
        }
        StockChangeResult.Applied
    } catch (e: StockShortageException) {
        StockChangeResult.WouldGoNegative
    }

    override fun observeMovementHistory(fromMillis: Long, toMillis: Long): Flow<List<StockMovementRecord>> =
        dao.observeMovementHistory(fromMillis, toMillis).map { rows -> rows.map { it.toDomain() } }
}
