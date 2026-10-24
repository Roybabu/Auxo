// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.testutil

import com.auxo.pos.data.repository.BarcodeMatch
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.data.repository.StockChangeResult
import com.auxo.pos.domain.inventory.BatchAllocator
import com.auxo.pos.domain.inventory.BatchStock
import com.auxo.pos.domain.model.BatchDetails
import com.auxo.pos.domain.model.BatchWithProduct
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductVariant
import com.auxo.pos.domain.model.StockBatch
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.domain.model.StockMovementRecord
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
    private var nextBatchId = 1L
    private val appliedRequests = mutableSetOf<String>()

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

    override suspend fun recordStockChange(movement: StockMovement, batch: BatchDetails, requestId: String?): StockChangeResult {
        val product = products.value.firstOrNull { p -> p.variants.any { it.id == movement.variantId } }
            ?: return StockChangeResult.ItemMissing
        val variant = product.variants.first { it.id == movement.variantId }
        val newStock = variant.stockQuantity + movement.quantityChange
        if (newStock < 0) return StockChangeResult.WouldGoNegative
        if (requestId != null && !appliedRequests.add(requestId)) return StockChangeResult.Applied
        val tracked = product.category.tracksExpiry
        val batches = when {
            !tracked -> variant.batches
            movement.quantityChange > 0 -> variant.batches + StockBatch(
                id = nextBatchId++,
                variantId = variant.id,
                batchNumber = batch.batchNumber,
                expiryDate = batch.expiryDate,
                quantity = movement.quantityChange,
            )
            variant.batches.sumOf { it.quantity } >= -movement.quantityChange -> {
                val takes = BatchAllocator.allocate(variant.batches.map { BatchStock(it.id, it.quantity) }, -movement.quantityChange)
                    .orEmpty().associate { it.batchId to it.quantity }
                variant.batches.map { it.copy(quantity = it.quantity - (takes[it.id] ?: 0)) }.filter { it.quantity > 0 }
            }
            else -> variant.batches
        }
        replaceVariant(product, variant.copy(stockQuantity = newStock, batches = batches))
        movements.value += movement.copy(id = nextMovementId++)
        return StockChangeResult.Applied
    }

    override fun observeBatches(): Flow<List<BatchWithProduct>> = products.map { list ->
        list.flatMap { product ->
            product.variants.flatMap { variant ->
                variant.batches.filter { it.quantity > 0 }.map {
                    BatchWithProduct(it, product.id, product.name, variant.attributesLabel, product.category, product.stockUnit, variant.costPrice)
                }
            }
        }.sortedWith(compareBy({ it.batch.expiryDate == null }, { it.batch.expiryDate }))
    }

    override suspend fun writeOff(batchId: Long, quantity: Int, reason: StockChangeReason, note: String?, requestId: String): StockChangeResult {
        val product = products.value.firstOrNull { p -> p.variants.any { v -> v.batches.any { it.id == batchId } } }
            ?: return StockChangeResult.WouldGoNegative
        val variant = product.variants.first { v -> v.batches.any { it.id == batchId } }
        val batch = variant.batches.first { it.id == batchId }
        if (batch.quantity < quantity) return StockChangeResult.WouldGoNegative
        if (!appliedRequests.add(requestId)) return StockChangeResult.Applied
        val batches = variant.batches.map { if (it.id == batchId) it.copy(quantity = it.quantity - quantity) else it }.filter { it.quantity > 0 }
        replaceVariant(product, variant.copy(stockQuantity = variant.stockQuantity - quantity, batches = batches))
        movements.value += StockMovement(
            id = nextMovementId++,
            variantId = variant.id,
            quantityChange = -quantity,
            reason = reason,
            note = note,
            createdAtMillis = 0,
            batchId = batchId,
        )
        return StockChangeResult.Applied
    }

    override fun observeMovementHistory(fromMillis: Long, toMillis: Long): Flow<List<StockMovementRecord>> =
        combine(products, movements) { list, all ->
            val variants = list.flatMap { product -> product.variants.map { it.id to (product to it) } }.toMap()
            all.filter { it.createdAtMillis in fromMillis until toMillis }
                .sortedByDescending { it.createdAtMillis }
                .mapNotNull { movement ->
                    val (product, variant) = variants[movement.variantId] ?: return@mapNotNull null
                    StockMovementRecord(
                        movement = movement,
                        productId = product.id,
                        productName = product.name,
                        variantLabel = variant.attributesLabel,
                        category = product.category,
                        categoryId = product.categoryId,
                        stockUnit = product.stockUnit,
                        batchNumber = null,
                        reference = null,
                        unitCost = variant.costPrice,
                    )
                }
        }

    private fun replaceVariant(product: Product, variant: ProductVariant) {
        val updated = product.copy(variants = product.variants.map { if (it.id == variant.id) variant else it })
        products.value = products.value.map { if (it.id == product.id) updated else it }
    }
}
