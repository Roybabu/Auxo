// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local

import com.auxo.pos.data.local.dao.InventoryDao
import com.auxo.pos.data.local.dao.OperationDao
import com.auxo.pos.data.local.dao.StockShortageException
import com.auxo.pos.data.local.entity.AppliedRequestEntity
import com.auxo.pos.data.local.entity.StockBatchEntity
import com.auxo.pos.data.local.entity.StockMovementEntity
import com.auxo.pos.data.local.entity.VariantStockRow
import com.auxo.pos.domain.inventory.BatchAllocator
import com.auxo.pos.domain.inventory.BatchStock
import com.auxo.pos.domain.inventory.BatchTake
import com.auxo.pos.domain.model.BatchDetails
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.StockChangeReason
import java.time.LocalDate

/** Thrown inside a transaction when an item to change no longer exists. */
class MissingItemException(val variantIds: Set<Long>) : RuntimeException("Item no longer exists")

/** What to record on each stock movement written by [StockWriter]. */
data class MovementInfo(
    val reason: StockChangeReason,
    val createdAt: Long,
    val note: String? = null,
    val invoiceId: Long? = null,
    val receiptId: Long? = null,
) {
    fun toEntity(variantId: Long, change: Int, batchId: Long?): StockMovementEntity = StockMovementEntity(
        variantId = variantId,
        quantityChange = change,
        reason = reason.name,
        note = note,
        createdAt = createdAt,
        invoiceId = invoiceId,
        batchId = batchId,
        receiptId = receiptId,
    )
}

/**
 * The only place stock changes. Every change is recorded as a movement, stock
 * never goes below zero, and for batch-tracked items (food, beverages,
 * ingredients) the batch quantities always add up to the variant's stock.
 *
 * Methods must run inside a database transaction: they throw to roll back.
 */
class StockWriter(private val dao: InventoryDao) {

    private fun isTracked(row: VariantStockRow): Boolean =
        enumValueOrNull<ProductCategory>(row.category)?.tracksExpiry == true

    private fun usable(batches: List<StockBatchEntity>, sellableOn: LocalDate?): List<StockBatchEntity> {
        val day = sellableOn?.toEpochDay() ?: return batches
        return batches.filter { it.expiryEpochDay == null || it.expiryEpochDay >= day }
    }

    /** False for a restaurant item sold without a stock count: nothing is checked, taken or put back for it. */
    suspend fun keepsStock(variantId: Long): Boolean = dao.variantStock(variantId)?.tracksStock ?: true

    /** Units of [variantId] that can be taken now; with [sellableOn], expired batches don't count. */
    suspend fun available(variantId: Long, sellableOn: LocalDate? = null): Int {
        val row = dao.variantStock(variantId) ?: return 0
        if (!isTracked(row)) return row.stockQuantity
        return usable(dao.batchesWithStock(variantId), sellableOn).sumOf { it.quantity }
    }

    /**
     * Takes [quantity] units out. Batch-tracked items are taken from the batch that
     * expires first; with [sellableOn], expired batches are skipped.
     *
     * @throws StockShortageException if there isn't enough
     */
    suspend fun remove(variantId: Long, quantity: Int, info: MovementInfo, sellableOn: LocalDate? = null) {
        require(quantity > 0) { "Quantity must be positive" }
        val row = dao.variantStock(variantId) ?: throw StockShortageException(setOf(variantId))
        val tracked = isTracked(row)
        if (tracked) {
            val batches = usable(dao.batchesWithStock(variantId), sellableOn)
            val takes = BatchAllocator.allocate(batches.map { BatchStock(it.id, it.quantity) }, quantity)
                ?: throw StockShortageException(setOf(variantId))
            for (take in takes) {
                if (dao.applyBatchChange(take.batchId, -take.quantity) == 0) throw StockShortageException(setOf(variantId))
                dao.insertMovement(info.toEntity(variantId, -take.quantity, take.batchId))
            }
        } else {
            dao.insertMovement(info.toEntity(variantId, -quantity, null))
        }
        if (dao.applyStockChange(variantId, -quantity) == 0) throw StockShortageException(setOf(variantId))
        if (tracked) dao.refreshBatchSummary(variantId)
    }

    /**
     * Adds [quantity] units. Batch-tracked items go into [intoBatchId], or else the
     * batch with the same number and expiry, or else a new batch.
     *
     * @return the batch the units went into, or null for items not kept in batches
     * @throws MissingItemException if the variant no longer exists
     */
    suspend fun add(
        variantId: Long,
        quantity: Int,
        info: MovementInfo,
        batch: BatchDetails = BatchDetails(),
        intoBatchId: Long? = null,
    ): Long? {
        require(quantity > 0) { "Quantity must be positive" }
        val row = dao.variantStock(variantId) ?: throw MissingItemException(setOf(variantId))
        if (dao.applyStockChange(variantId, quantity) == 0) throw MissingItemException(setOf(variantId))
        val batchId = if (isTracked(row)) {
            val expiry = batch.expiryDate?.toEpochDay()
            val id = intoBatchId
                ?: dao.findBatch(variantId, batch.batchNumber, expiry)?.id
                ?: dao.insertBatch(
                    StockBatchEntity(
                        variantId = variantId,
                        batchNumber = batch.batchNumber,
                        expiryEpochDay = expiry,
                        quantity = 0,
                        receivedAt = info.createdAt,
                        unitCostMinor = batch.unitCost?.minorUnits,
                    ),
                )
            if (dao.applyBatchChange(id, quantity) == 0) throw MissingItemException(setOf(variantId))
            id
        } else {
            null
        }
        dao.insertMovement(info.toEntity(variantId, quantity, batchId))
        if (batchId != null) dao.refreshBatchSummary(variantId)
        return batchId
    }

    /**
     * Takes [quantity] units out of one batch, for writing off wastage.
     *
     * @throws StockShortageException if the batch holds less
     */
    suspend fun removeFromBatch(batchId: Long, quantity: Int, info: MovementInfo) {
        require(quantity > 0) { "Quantity must be positive" }
        val batch = dao.getBatch(batchId) ?: throw StockShortageException(emptySet())
        if (dao.applyBatchChange(batchId, -quantity) == 0) throw StockShortageException(setOf(batch.variantId))
        if (dao.applyStockChange(batch.variantId, -quantity) == 0) throw StockShortageException(setOf(batch.variantId))
        dao.insertMovement(info.toEntity(batch.variantId, -quantity, batchId))
        dao.refreshBatchSummary(batch.variantId)
    }

    /**
     * Puts units returned against sale [saleId] back in stock: into the batches the
     * sale took them from where known, otherwise into a batch without a number or
     * expiry. Does nothing if the product was deleted.
     */
    suspend fun restoreFromSale(saleId: Long, variantId: Long, quantity: Int, info: MovementInfo) {
        require(quantity > 0) { "Quantity must be positive" }
        val row = dao.variantStock(variantId) ?: return
        if (!row.tracksStock) return
        if (!isTracked(row)) {
            add(variantId, quantity, info)
            return
        }
        val batches = dao.batchesSold(saleId, variantId).mapNotNull { sold ->
            dao.getBatch(sold.batchId)?.let { it to sold.quantity }
        }
        // The sale took the earliest expiry first, so put back into the latest first.
        val soldLastFirst = batches
            .sortedWith(compareBy<Pair<StockBatchEntity, Int>>({ it.first.expiryEpochDay == null }, { it.first.expiryEpochDay }, { it.first.receivedAt }, { it.first.id }))
            .reversed()
            .map { (batch, sold) -> BatchTake(batch.id, sold) }
        val returned = dao.batchesReturned(saleId, variantId).associate { it.batchId to it.quantity }
        val placement = BatchAllocator.placeReturn(soldLastFirst, returned, quantity)
        for (take in placement.takes) add(variantId, take.quantity, info, intoBatchId = take.batchId)
        if (placement.unplaced > 0) add(variantId, placement.unplaced, info)
    }
}

/** Outcome of claiming a one-time request id. */
sealed interface RequestClaim {
    /** First time this id is seen; go ahead. */
    data object New : RequestClaim

    /** Already saved; [resultId] is what it created. */
    data class AlreadyDone(val resultId: Long?) : RequestClaim
}

/** Records [requestId] as used. Call inside the transaction that does the work, so a rollback frees it again. */
suspend fun OperationDao.claim(requestId: String, kind: String, nowMillis: Long): RequestClaim =
    if (insertRequest(AppliedRequestEntity(requestId, kind, null, nowMillis)) == -1L) {
        RequestClaim.AlreadyDone(getRequest(requestId)?.resultId)
    } else {
        RequestClaim.New
    }
