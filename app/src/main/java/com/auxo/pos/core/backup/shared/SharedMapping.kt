// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.ProductEntity
import com.auxo.pos.data.local.entity.ProductVariantEntity
import com.auxo.pos.data.local.entity.StockBatchEntity
import com.auxo.pos.data.local.entity.StoreSettingsEntity
import java.time.LocalDate

/**
 * Maps Android records to the shared auxo-backup table rows (docs/BACKUP_FORMAT.md section 2).
 * Money is written as whole-paise text. Dates are YYYY-MM-DD. Only the tables in this file are mapped so far.
 */
object SharedMapping {
    private const val GST_RULE_APPAREL = "APPAREL"

    fun settings(s: StoreSettingsEntity): Map<String, Any?> = mapOf(
        "id" to "shop",
        "storeName" to s.storeName,
        "address" to s.address,
        "gstin" to s.gstin,
        "stateCode" to s.stateCode,
        "gstRegistered" to s.gstRegistered,
        "pricesIncludeTax" to s.pricesIncludeTax,
        "roundOff" to s.roundOff,
        "invoicePrefix" to s.invoicePrefix,
        "creditNotePrefix" to s.creditNotePrefix,
        "phone" to s.phone,
        "fssai" to s.fssai,
        "upiId" to s.upiId,
        "upiPayeeName" to s.upiPayeeName,
        "receiptPaper" to s.receiptPaper,
        "receiptFooter" to s.receiptFooter,
        "receiptLanguage" to (s.receiptLanguage ?: "en"),
        "receiptShowHsn" to s.receiptShowHsn,
        "receiptShowTaxBreakdown" to s.receiptShowTaxBreakup,
        "receiptShowCustomer" to s.receiptShowCustomer,
        "receiptShowSavings" to s.receiptShowSavings,
        "receiptShowBarcode" to s.receiptShowBarcode,
        "receiptShowUpiQr" to s.receiptShowUpiQr,
        "employeeDiscountLimitBp" to s.employeeDiscountLimitBp,
        "expiryWarningDays" to s.expiryWarningDays,
        // Metal rates are kept in app preferences, not the database; they are mapped in a later step.
        "metalRatesPaise" to emptyMap<String, String>(),
        "storeLogoPhotoId" to null,
    )

    fun product(p: ProductEntity): Map<String, Any?> = mapOf(
        "id" to SharedIds.derive("product", p.id.toString()),
        "name" to p.name,
        "category" to p.category,
        "brand" to p.brand,
        "createdAt" to p.createdAt,
        "updatedAt" to p.updatedAt,
        "stockUnit" to (p.stockUnit ?: "UNIT"),
        "gstRateBasisPoints" to p.gstRateBasisPoints,
        "apparelGst" to (p.gstRule == GST_RULE_APPAREL),
        "available" to p.available,
        "tracksStock" to p.tracksStock,
        "categoryId" to p.categoryId?.let { SharedIds.derive("category", it.toString()) },
        "subcategory" to p.subcategory,
        "description" to p.description,
        "material" to p.material,
        "dietType" to p.dietType,
        "hsnCode" to p.hsnCode,
        "preferredSupplierId" to p.preferredSupplierId?.let { SharedIds.derive("supplier", it.toString()) },
        "photoId" to p.photoFile?.let { SharedIds.derive("photo", it) },
        "warrantyMonths" to p.warrantyMonths,
        "prescriptionRequired" to p.prescriptionRequired,
        "metal" to p.metal,
        "makingChargeBasisPoints" to p.makingChargeBasisPoints,
    )

    fun variant(v: ProductVariantEntity): Map<String, Any?> = mapOf(
        "id" to SharedIds.derive("variant", v.id.toString()),
        "productId" to SharedIds.derive("product", v.productId.toString()),
        "sku" to (v.sku ?: ""),
        "barcode" to v.barcode,
        "size" to (v.size ?: ""),
        "colour" to (v.colour ?: ""),
        "pricePaise" to v.priceMinor.toString(),
        "mrpPaise" to v.mrpMinor?.toString(),
        "costPaise" to v.costMinor?.toString(),
        "stockQuantity" to v.stockQuantity,
        "lowStockThreshold" to v.lowStockThreshold,
        "reorderQuantity" to v.reorderQuantity,
        "netQuantityMilli" to v.netQuantityMilli,
        "netQuantityUnit" to v.netQuantityUnit,
        "photoId" to v.photoFile?.let { SharedIds.derive("photo", it) },
    )

    fun stockBatch(b: StockBatchEntity): Map<String, Any?> = mapOf(
        "id" to SharedIds.derive("batch", b.id.toString()),
        "variantId" to SharedIds.derive("variant", b.variantId.toString()),
        "batchNumber" to b.batchNumber,
        "expiryDate" to b.expiryEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
        "quantity" to b.quantity,
        "receivedAt" to b.receivedAt,
        "unitCostPaise" to b.unitCostMinor?.toString(),
    )
}
