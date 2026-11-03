// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
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

/**
 * Plain inserts for a restore. Each one writes exactly the row it is given, with no business rules, so a restored
 * record keeps the values in the file. Records with an auto-generated number return that number.
 */
@Dao
interface RestoreDao {
    @Insert
    suspend fun insertSettings(row: StoreSettingsEntity)

    @Insert
    suspend fun insertLoyaltySettings(row: LoyaltySettingsEntity)

    @Insert
    suspend fun insertNumberSequence(row: NumberSequenceEntity)

    @Insert
    suspend fun insertCategory(row: ShopCategoryEntity): Long

    @Insert
    suspend fun insertSubcategory(row: ShopSubcategoryEntity): Long

    @Insert
    suspend fun insertSupplier(row: SupplierEntity): Long

    @Insert
    suspend fun insertProduct(row: ProductEntity): Long

    @Insert
    suspend fun insertVariant(row: ProductVariantEntity): Long

    @Insert
    suspend fun insertBatch(row: StockBatchEntity): Long

    @Insert
    suspend fun insertCustomer(row: CustomerEntity): Long

    @Insert
    suspend fun insertInvoice(row: InvoiceEntity): Long

    @Insert
    suspend fun insertInvoiceLine(row: InvoiceLineEntity): Long

    @Insert
    suspend fun insertLedgerEntry(row: LedgerEntryEntity): Long

    @Insert
    suspend fun insertPayment(row: PaymentEntity): Long

    @Insert
    suspend fun insertPurchaseOrder(row: PurchaseOrderEntity): Long

    @Insert
    suspend fun insertPurchaseOrderLine(row: PurchaseOrderLineEntity): Long

    @Insert
    suspend fun insertReceipt(row: PurchaseReceiptEntity): Long

    @Insert
    suspend fun insertReceiptLine(row: PurchaseReceiptLineEntity): Long

    @Insert
    suspend fun insertStockMovement(row: StockMovementEntity): Long

    @Insert
    suspend fun insertDayClosing(row: DayClosingEntity): Long

    @Insert
    suspend fun insertHeldBill(row: HeldBillEntity): Long

    @Insert
    suspend fun insertQuotation(row: QuotationEntity): Long

    @Insert
    suspend fun insertStockCount(row: StockCountEntity): Long

    @Insert
    suspend fun insertAuditLog(row: AuditLogEntity): Long
}
