package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.auxo.pos.data.local.enumValueOrNull
import com.auxo.pos.data.local.entity.BatchQuantityRow
import com.auxo.pos.data.local.entity.BatchRow
import com.auxo.pos.data.local.entity.MovementRow
import com.auxo.pos.data.local.entity.ProductEntity
import com.auxo.pos.data.local.entity.ProductVariantEntity
import com.auxo.pos.data.local.entity.ProductWithVariants
import com.auxo.pos.data.local.entity.StockBatchEntity
import com.auxo.pos.data.local.entity.StockMovementEntity
import com.auxo.pos.data.local.entity.VariantStockRow
import com.auxo.pos.domain.model.ProductCategory
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InventoryDao {

    // Products

    @Transaction
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    abstract fun observeProducts(): Flow<List<ProductWithVariants>>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId")
    abstract fun observeProduct(productId: Long): Flow<ProductWithVariants?>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId")
    abstract suspend fun getProduct(productId: Long): ProductWithVariants?

    @Insert
    abstract suspend fun insertProduct(product: ProductEntity): Long

    @Update
    abstract suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :productId")
    abstract suspend fun deleteProduct(productId: Long): Int

    // Variants

    @Insert
    abstract suspend fun insertVariant(variant: ProductVariantEntity): Long

    @Update
    abstract suspend fun updateVariant(variant: ProductVariantEntity)

    @Query("DELETE FROM product_variants WHERE id IN (:variantIds)")
    abstract suspend fun deleteVariants(variantIds: List<Long>)

    @Query("SELECT * FROM product_variants WHERE barcode = :barcode LIMIT 1")
    abstract suspend fun findVariantByBarcode(barcode: String): ProductVariantEntity?

    @Query("SELECT barcode FROM product_variants WHERE barcode IN (:barcodes) AND product_id != :excludeProductId")
    abstract suspend fun barcodesUsedByOtherProducts(barcodes: List<String>, excludeProductId: Long): List<String>

    @Query("SELECT sku FROM product_variants WHERE sku IN (:skus) AND product_id != :excludeProductId")
    abstract suspend fun skusUsedByOtherProducts(skus: List<String>, excludeProductId: Long): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM product_variants WHERE barcode = :barcode)")
    abstract suspend fun barcodeExists(barcode: String): Boolean

    @Query(
        "SELECT v.id AS id, p.category AS category, v.stock_quantity AS stock_quantity " +
            "FROM product_variants v JOIN products p ON p.id = v.product_id WHERE v.id = :variantId",
    )
    abstract suspend fun variantStock(variantId: Long): VariantStockRow?

    @Query("UPDATE product_variants SET cost_minor = :costMinor WHERE id = :variantId")
    abstract suspend fun setCostPrice(variantId: Long, costMinor: Long)

    // Stock

    /** Applies [change] only if stock stays at or above zero. Returns the number of rows updated. */
    @Query(
        "UPDATE product_variants SET stock_quantity = stock_quantity + :change " +
            "WHERE id = :variantId AND stock_quantity + :change >= 0",
    )
    abstract suspend fun applyStockChange(variantId: Long, change: Int): Int

    @Insert
    abstract suspend fun insertMovement(movement: StockMovementEntity): Long

    @Query(
        "SELECT * FROM stock_movements WHERE variant_id IN " +
            "(SELECT id FROM product_variants WHERE product_id = :productId) " +
            "ORDER BY created_at DESC, id DESC LIMIT :limit",
    )
    abstract fun observeMovementsForProduct(productId: Long, limit: Int): Flow<List<StockMovementEntity>>

    @Query(
        "SELECT m.*, p.id AS product_id, p.name AS product_name, p.category AS category, p.stock_unit AS stock_unit, " +
            "v.size AS size, v.colour AS colour, v.net_quantity_milli AS net_quantity_milli, " +
            "v.net_quantity_unit AS net_quantity_unit, v.cost_minor AS cost_minor, " +
            "b.batch_number AS batch_number_ref, i.number AS invoice_number, r.number AS receipt_number " +
            "FROM stock_movements m " +
            "JOIN product_variants v ON v.id = m.variant_id " +
            "JOIN products p ON p.id = v.product_id " +
            "LEFT JOIN stock_batches b ON b.id = m.batch_id " +
            "LEFT JOIN invoices i ON i.id = m.invoice_id " +
            "LEFT JOIN purchase_receipts r ON r.id = m.receipt_id " +
            "WHERE m.created_at >= :fromMillis AND m.created_at < :toMillis " +
            "ORDER BY m.created_at DESC, m.id DESC",
    )
    abstract fun observeMovementHistory(fromMillis: Long, toMillis: Long): Flow<List<MovementRow>>

    @Query("SELECT COALESCE(SUM(quantity_change), 0) FROM stock_movements WHERE variant_id = :variantId")
    abstract suspend fun movementTotal(variantId: Long): Int

    // Batches

    @Insert
    abstract suspend fun insertBatch(batch: StockBatchEntity): Long

    @Query("SELECT * FROM stock_batches WHERE id = :batchId")
    abstract suspend fun getBatch(batchId: Long): StockBatchEntity?

    /** Batches with stock, the one to sell first at the top: earliest expiry, then oldest. */
    @Query(
        "SELECT * FROM stock_batches WHERE variant_id = :variantId AND quantity > 0 " +
            "ORDER BY expiry_epoch_day IS NULL, expiry_epoch_day, received_at, id",
    )
    abstract suspend fun batchesWithStock(variantId: Long): List<StockBatchEntity>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_batches WHERE variant_id = :variantId")
    abstract suspend fun batchTotal(variantId: Long): Int

    @Query(
        "SELECT * FROM stock_batches WHERE variant_id = :variantId " +
            "AND batch_number IS :batchNumber AND expiry_epoch_day IS :expiryEpochDay ORDER BY id LIMIT 1",
    )
    abstract suspend fun findBatch(variantId: Long, batchNumber: String?, expiryEpochDay: Long?): StockBatchEntity?

    /** Applies [change] only if the batch stays at or above zero. */
    @Query("UPDATE stock_batches SET quantity = quantity + :change WHERE id = :batchId AND quantity + :change >= 0")
    abstract suspend fun applyBatchChange(batchId: Long, change: Int): Int

    /** Copies the next batch to sell onto the variant, so lists can show its expiry. */
    @Query(
        "UPDATE product_variants SET " +
            "batch_number = (SELECT b.batch_number FROM stock_batches b WHERE b.variant_id = :variantId AND b.quantity > 0 " +
            "ORDER BY b.expiry_epoch_day IS NULL, b.expiry_epoch_day, b.received_at, b.id LIMIT 1), " +
            "expiry_epoch_day = (SELECT b.expiry_epoch_day FROM stock_batches b WHERE b.variant_id = :variantId AND b.quantity > 0 " +
            "ORDER BY b.expiry_epoch_day IS NULL, b.expiry_epoch_day, b.received_at, b.id LIMIT 1) " +
            "WHERE id = :variantId",
    )
    abstract suspend fun refreshBatchSummary(variantId: Long)

    @Query(
        "SELECT b.*, p.id AS product_id, p.name AS product_name, p.category AS category, p.stock_unit AS stock_unit, " +
            "v.size AS size, v.colour AS colour, v.net_quantity_milli AS net_quantity_milli, " +
            "v.net_quantity_unit AS net_quantity_unit, v.cost_minor AS cost_minor " +
            "FROM stock_batches b JOIN product_variants v ON v.id = b.variant_id JOIN products p ON p.id = v.product_id " +
            "WHERE b.quantity > 0 ORDER BY b.expiry_epoch_day IS NULL, b.expiry_epoch_day, p.name COLLATE NOCASE, b.id",
    )
    abstract fun observeBatchesWithStock(): Flow<List<BatchRow>>

    /** Units a sale took from each batch of a variant. */
    @Query(
        "SELECT batch_id, -SUM(quantity_change) AS quantity FROM stock_movements " +
            "WHERE invoice_id = :saleId AND variant_id = :variantId AND reason = 'SOLD' AND batch_id IS NOT NULL " +
            "GROUP BY batch_id",
    )
    abstract suspend fun batchesSold(saleId: Long, variantId: Long): List<BatchQuantityRow>

    /** Units already put back into each batch by credit notes against a sale. */
    @Query(
        "SELECT batch_id, SUM(quantity_change) AS quantity FROM stock_movements " +
            "WHERE invoice_id IN (SELECT id FROM invoices WHERE original_invoice_id = :saleId) " +
            "AND variant_id = :variantId AND reason = 'RETURNED' AND batch_id IS NOT NULL GROUP BY batch_id",
    )
    abstract suspend fun batchesReturned(saleId: Long, variantId: Long): List<BatchQuantityRow>

    /**
     * Inserts or updates a product and its variants in one transaction.
     * Variants missing from [variants] are deleted (with their stock history).
     * Stock on existing variants is never overwritten here; it changes through
     * stock adjustments, receipts and sales. Opening stock of a new batch-tracked
     * variant becomes its first batch.
     *
     * @return the product id
     */
    @Transaction
    open suspend fun saveProduct(
        product: ProductEntity,
        variants: List<ProductVariantEntity>,
        removedVariantIds: List<Long>,
        nowMillis: Long,
    ): Long {
        val productId = if (product.id == 0L) {
            insertProduct(product)
        } else {
            updateProduct(product)
            product.id
        }
        val tracked = enumValueOrNull<ProductCategory>(product.category)?.tracksExpiry == true

        if (removedVariantIds.isNotEmpty()) deleteVariants(removedVariantIds)

        val existing = getProduct(productId)?.variants.orEmpty().associate { it.variant.id to it.variant }

        for (variant in variants) {
            if (variant.id == 0L) {
                val variantId = insertVariant(variant.copy(productId = productId))
                if (variant.stockQuantity > 0) {
                    val batchId = if (tracked) {
                        insertBatch(
                            StockBatchEntity(
                                variantId = variantId,
                                batchNumber = variant.batchNumber,
                                expiryEpochDay = variant.expiryEpochDay,
                                quantity = variant.stockQuantity,
                                receivedAt = nowMillis,
                                unitCostMinor = variant.costMinor,
                            ),
                        )
                    } else {
                        null
                    }
                    insertMovement(
                        StockMovementEntity(
                            variantId = variantId,
                            quantityChange = variant.stockQuantity,
                            reason = OPENING_STOCK_REASON,
                            note = null,
                            createdAt = nowMillis,
                            batchId = batchId,
                        ),
                    )
                }
            } else {
                val saved = existing[variant.id]
                updateVariant(
                    variant.copy(
                        productId = productId,
                        stockQuantity = saved?.stockQuantity ?: variant.stockQuantity,
                        // For batch-tracked items these follow the batches.
                        batchNumber = if (tracked && saved != null) saved.batchNumber else variant.batchNumber,
                        expiryEpochDay = if (tracked && saved != null) saved.expiryEpochDay else variant.expiryEpochDay,
                    ),
                )
            }
        }
        return productId
    }

    private companion object {
        const val OPENING_STOCK_REASON = "INITIAL"
    }
}
