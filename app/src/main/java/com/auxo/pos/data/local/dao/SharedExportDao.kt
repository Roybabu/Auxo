// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.auxo.pos.data.local.entity.AuditLogEntity
import com.auxo.pos.data.local.entity.CustomerEntity
import com.auxo.pos.data.local.entity.DayClosingEntity
import com.auxo.pos.data.local.entity.HeldBillEntity
import com.auxo.pos.data.local.entity.InvoiceEntity
import com.auxo.pos.data.local.entity.InvoiceLineEntity
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.local.entity.LoyaltySettingsEntity
import com.auxo.pos.data.local.entity.NumberSequenceEntity
import com.auxo.pos.data.local.entity.PaymentEntity
import com.auxo.pos.data.local.entity.ProductEntity
import com.auxo.pos.data.local.entity.ProductVariantEntity
import com.auxo.pos.data.local.entity.PurchaseOrderEntity
import com.auxo.pos.data.local.entity.PurchaseOrderLineEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptLineEntity
import com.auxo.pos.data.local.entity.QuotationEntity
import com.auxo.pos.data.local.entity.ShopCategoryEntity
import com.auxo.pos.data.local.entity.ShopSubcategoryEntity
import com.auxo.pos.data.local.entity.StockBatchEntity
import com.auxo.pos.data.local.entity.StockCountEntity
import com.auxo.pos.data.local.entity.StockMovementEntity
import com.auxo.pos.data.local.entity.StoreSettingsEntity
import com.auxo.pos.data.local.entity.SupplierEntity

/** Read-only queries that gather every table for a shared backup export. Nothing here writes. */
@Dao
interface SharedExportDao {
    /** Every stored link from a record to the shared ID it had in a shared backup. */
    @Query("SELECT * FROM shared_id_links")
    suspend fun sharedIdLinks(): List<com.auxo.pos.data.local.entity.SharedIdLinkEntity>

    @Query("SELECT * FROM store_settings")
    suspend fun storeSettings(): List<StoreSettingsEntity>

    @Query("SELECT * FROM loyalty_settings")
    suspend fun loyaltySettings(): List<LoyaltySettingsEntity>

    @Query("SELECT * FROM shop_categories")
    suspend fun categories(): List<ShopCategoryEntity>

    @Query("SELECT * FROM shop_subcategories")
    suspend fun subcategories(): List<ShopSubcategoryEntity>

    @Query("SELECT * FROM products")
    suspend fun products(): List<ProductEntity>

    @Query("SELECT * FROM product_variants")
    suspend fun variants(): List<ProductVariantEntity>

    @Query("SELECT * FROM stock_batches")
    suspend fun stockBatches(): List<StockBatchEntity>

    @Query("SELECT * FROM stock_movements")
    suspend fun stockMovements(): List<StockMovementEntity>

    @Query("SELECT * FROM customers")
    suspend fun customers(): List<CustomerEntity>

    @Query("SELECT * FROM customer_ledger")
    suspend fun ledgerEntries(): List<LedgerEntryEntity>

    @Query("SELECT * FROM invoices")
    suspend fun invoices(): List<InvoiceEntity>

    @Query("SELECT * FROM invoice_lines")
    suspend fun invoiceLines(): List<InvoiceLineEntity>

    @Query("SELECT * FROM payments")
    suspend fun payments(): List<PaymentEntity>

    @Query("SELECT * FROM suppliers")
    suspend fun suppliers(): List<SupplierEntity>

    @Query("SELECT * FROM purchase_orders")
    suspend fun purchaseOrders(): List<PurchaseOrderEntity>

    @Query("SELECT * FROM purchase_order_lines")
    suspend fun purchaseOrderLines(): List<PurchaseOrderLineEntity>

    @Query("SELECT * FROM purchase_receipts")
    suspend fun purchaseReceipts(): List<PurchaseReceiptEntity>

    @Query("SELECT * FROM purchase_receipt_lines")
    suspend fun purchaseReceiptLines(): List<PurchaseReceiptLineEntity>

    @Query("SELECT * FROM day_closings")
    suspend fun dayClosings(): List<DayClosingEntity>

    @Query("SELECT * FROM held_bills")
    suspend fun heldBills(): List<HeldBillEntity>

    @Query("SELECT * FROM quotations")
    suspend fun quotations(): List<QuotationEntity>

    @Query("SELECT * FROM stock_counts")
    suspend fun stockCounts(): List<StockCountEntity>

    @Query("SELECT * FROM audit_log")
    suspend fun auditLog(): List<AuditLogEntity>

    @Query("SELECT * FROM number_sequences")
    suspend fun numberSequences(): List<NumberSequenceEntity>
}
