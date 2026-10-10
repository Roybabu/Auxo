package com.invision.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.invision.pos.data.local.entity.ProductEntity
import com.invision.pos.data.local.entity.ProductVariantEntity
import com.invision.pos.data.local.entity.ProductWithVariants
import com.invision.pos.data.local.entity.StockMovementEntity
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

    /**
     * Inserts or updates a product and its variants in one transaction.
     * Variants missing from [variants] are deleted (with their stock history).
     * Stock on existing variants is never overwritten here; use [adjustStock].
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

        if (removedVariantIds.isNotEmpty()) deleteVariants(removedVariantIds)

        val existingStock = getProduct(productId)?.variants.orEmpty().associate { it.id to it.stockQuantity }

        for (variant in variants) {
            if (variant.id == 0L) {
                val variantId = insertVariant(variant.copy(productId = productId))
                if (variant.stockQuantity > 0) {
                    insertMovement(
                        StockMovementEntity(
                            variantId = variantId,
                            quantityChange = variant.stockQuantity,
                            reason = OPENING_STOCK_REASON,
                            note = null,
                            createdAt = nowMillis,
                        ),
                    )
                }
            } else {
                val stock = existingStock[variant.id] ?: variant.stockQuantity
                updateVariant(variant.copy(productId = productId, stockQuantity = stock))
            }
        }
        return productId
    }

    /** Changes stock and records the movement. Returns false if stock would go negative. */
    @Transaction
    open suspend fun adjustStock(movement: StockMovementEntity): Boolean {
        val updated = applyStockChange(movement.variantId, movement.quantityChange)
        if (updated == 0) return false
        insertMovement(movement)
        return true
    }

    private companion object {
        const val OPENING_STOCK_REASON = "INITIAL"
    }
}
