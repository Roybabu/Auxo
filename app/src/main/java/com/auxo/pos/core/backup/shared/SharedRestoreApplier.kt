// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import androidx.room.withTransaction
import com.auxo.pos.core.photos.ProductPhotoStore
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.entity.SharedIdLinkEntity
import java.io.File

/**
 * Writes a checked [RestorePlan] into the database. Every business table is emptied and refilled inside one
 * transaction, so a failure leaves the old data in place. Product photos are put back after the records are written.
 */
class SharedRestoreApplier(
    private val db: AuxoDatabase,
    private val photos: ProductPhotoStore?,
    private val workDir: File,
) {
    suspend fun apply(plan: RestorePlan) {
        val photoDir = File(workDir, "restore-photos").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val photoFiles = plan.photos.map { photo -> File(photoDir, photo.name).apply { writeBytes(photo.bytes) } }
            writeRecords(plan)
            photos?.replaceAll(photoFiles)
        } finally {
            photoDir.deleteRecursively()
        }
    }

    private suspend fun writeRecords(plan: RestorePlan) {
        // Local numbers of the records written so far, by link key and shared ID.
        val ids = HashMap<String, MutableMap<String, Long>>()
        val links = ArrayList<SharedIdLinkEntity>()
        val resolver = LocalIds { key, sharedId ->
            ids[key]?.get(sharedId) ?: throw IllegalStateException("A record was needed before it was written: $key $sharedId.")
        }
        db.withTransaction {
            CLEARED_TABLES.forEach { table -> db.openHelper.writableDatabase.execSQL("DELETE FROM `$table`") }
            val dao = db.restoreDao()
            dao.insertSettings(plan.settings)
            plan.loyaltySettings?.let { dao.insertLoyaltySettings(it) }
            plan.numberSequences.forEach { dao.insertNumberSequence(it) }
            // Parents are written before the rows that refer to them.
            write(plan.categories, ids, links, resolver) { dao.insertCategory(it) }
            write(plan.subcategories, ids, links, resolver) { dao.insertSubcategory(it) }
            write(plan.suppliers, ids, links, resolver) { dao.insertSupplier(it) }
            write(plan.products, ids, links, resolver) { dao.insertProduct(it) }
            write(plan.variants, ids, links, resolver) { dao.insertVariant(it) }
            write(plan.batches, ids, links, resolver) { dao.insertBatch(it) }
            write(plan.customers, ids, links, resolver) { dao.insertCustomer(it) }
            write(plan.invoices, ids, links, resolver) { dao.insertInvoice(it) }
            write(plan.invoiceLines, ids, links, resolver) { dao.insertInvoiceLine(it) }
            write(plan.ledger, ids, links, resolver) { dao.insertLedgerEntry(it) }
            write(plan.payments, ids, links, resolver) { dao.insertPayment(it) }
            write(plan.purchaseOrders, ids, links, resolver) { dao.insertPurchaseOrder(it) }
            write(plan.purchaseOrderLines, ids, links, resolver) { dao.insertPurchaseOrderLine(it) }
            write(plan.receipts, ids, links, resolver) { dao.insertReceipt(it) }
            write(plan.receiptLines, ids, links, resolver) { dao.insertReceiptLine(it) }
            write(plan.stockMovements, ids, links, resolver) { dao.insertStockMovement(it) }
            write(plan.dayClosings, ids, links, resolver) { dao.insertDayClosing(it) }
            write(plan.heldBills, ids, links, resolver) { dao.insertHeldBill(it) }
            write(plan.quotations, ids, links, resolver) { dao.insertQuotation(it) }
            write(plan.stockCounts, ids, links, resolver) { dao.insertStockCount(it) }
            write(plan.auditLog, ids, links, resolver) { dao.insertAuditLog(it) }
            db.sharedIdLinkDao().insertAll(links)
        }
    }

    /** Writes each row, then records its local number under every link key it has. */
    private suspend fun <T> write(
        rows: List<PlannedRow<T>>,
        ids: MutableMap<String, MutableMap<String, Long>>,
        links: MutableList<SharedIdLinkEntity>,
        resolver: LocalIds,
        insert: suspend (T) -> Long,
    ) {
        for (row in rows) {
            val localId = insert(row.build(resolver))
            for ((key, sharedId) in row.links) {
                ids.getOrPut(key) { HashMap() }[sharedId] = localId
                links += SharedIdLinkEntity(tableName = key, localId = localId, sharedId = sharedId)
            }
        }
    }

    private companion object {
        /** Every table a restore replaces. */
        val CLEARED_TABLES = listOf(
            "store_settings", "shop_categories", "shop_subcategories", "products", "product_variants",
            "stock_batches", "stock_movements", "customers", "customer_ledger", "invoices", "invoice_lines",
            "payments", "suppliers", "purchase_orders", "purchase_order_lines", "purchase_receipts",
            "purchase_receipt_lines", "day_closings", "loyalty_settings", "held_bills", "quotations",
            "stock_counts", "audit_log", "number_sequences", "shared_id_links", "applied_requests",
        )
    }
}
