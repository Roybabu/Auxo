// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.CustomerEntity
import com.auxo.pos.data.local.entity.InvoiceEntity
import com.auxo.pos.data.local.entity.InvoiceLineEntity
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.local.entity.PaymentEntity
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

    /** The customer's balance is the sum of their ledger entries; positive means the customer owes the shop. */
    fun customer(c: CustomerEntity, balanceMinor: Long): Map<String, Any?> = mapOf(
        "id" to SharedIds.derive("customer", c.id.toString()),
        "name" to c.name,
        "phone" to c.phone,
        "email" to c.email,
        "gstin" to c.gstin,
        "stateCode" to c.stateCode,
        "address" to c.address,
        "notes" to c.notes,
        "creditLimitPaise" to c.creditLimitMinor?.toString(),
        "balancePaise" to balanceMinor.toString(),
        // The Android app has no account hold, so every account is open.
        "onHold" to false,
        "createdAt" to c.createdAt,
        "updatedAt" to c.updatedAt,
    )

    fun ledgerEntry(e: LedgerEntryEntity, invoiceNumber: (Long) -> String?): Map<String, Any?> = mapOf(
        "id" to SharedIds.derive("ledger", e.id.toString()),
        "customerId" to SharedIds.derive("customer", e.customerId.toString()),
        "type" to e.type,
        "amountPaise" to e.amountMinor.toString(),
        "invoiceId" to e.invoiceId?.let { SharedIds.derive("invoice", it.toString()) },
        "invoiceNumber" to e.invoiceId?.let(invoiceNumber),
        "method" to e.method,
        "reference" to e.reference,
        "createdAt" to e.createdAt,
        "note" to e.note,
    )

    /**
     * The Android app keeps one discount per invoice. The shared format has a line discount and a bill discount:
     * the line discounts are the sum of the lines, and the rest of the invoice discount is the bill discount.
     * Loyalty redemption is not stored on the invoice in this app, so those fields are zero.
     */
    fun invoice(i: InvoiceEntity, lineDiscountMinor: Long, customerId: Long?): Map<String, Any?> {
        val billDiscount = (i.discountMinor - lineDiscountMinor).coerceAtLeast(0L)
        val buyer = if (i.buyerName == null && i.buyerPhone == null && i.buyerGstin == null) null else mapOf(
            "name" to i.buyerName,
            "phone" to i.buyerPhone,
            "gstin" to i.buyerGstin,
            "stateCode" to null,
        )
        return mapOf(
            "id" to SharedIds.derive("invoice", i.id.toString()),
            "number" to i.number,
            "type" to i.type,
            "createdAt" to i.createdAt,
            "seller" to mapOf(
                "storeName" to i.sellerName,
                "address" to i.sellerAddress,
                "gstin" to i.sellerGstin,
                "stateCode" to i.sellerStateCode,
                "phone" to i.sellerPhone,
                "fssai" to i.sellerFssai,
            ),
            "buyer" to buyer,
            "customerId" to customerId?.let { SharedIds.derive("customer", it.toString()) },
            "originalInvoiceId" to i.originalInvoiceId?.let { SharedIds.derive("invoice", it.toString()) },
            "originalInvoiceNumber" to i.originalInvoiceNumber,
            "note" to i.note,
            "returnReason" to null,
            "placeOfSupply" to i.placeOfSupply,
            "gstRegistered" to i.gstRegistered,
            "pricesIncludeTax" to i.pricesIncludeTax,
            "interState" to i.interState,
            "subtotalPaise" to i.subtotalMinor.toString(),
            "lineDiscountPaise" to lineDiscountMinor.toString(),
            "billDiscountPaise" to billDiscount.toString(),
            "discountPaise" to i.discountMinor.toString(),
            "billDiscountReason" to i.discountReason,
            "loyaltyPointsRedeemed" to 0,
            "loyaltyDiscountPaise" to "0",
            "taxablePaise" to i.taxableMinor.toString(),
            "cgstPaise" to i.cgstMinor.toString(),
            "sgstPaise" to i.sgstMinor.toString(),
            "igstPaise" to i.igstMinor.toString(),
            "roundOffPaise" to i.roundOffMinor.toString(),
            "roundedToRupee" to i.roundedToRupee,
            "grandTotalPaise" to i.grandTotalMinor.toString(),
            "savingsPaise" to i.savingsMinor.toString(),
        )
    }

    /** [productIdOf] finds the product a variant belongs to, because the shared line names the product as well. */
    fun invoiceLine(l: InvoiceLineEntity, productIdOf: (Long) -> Long?): Map<String, Any?> = mapOf(
        "id" to SharedIds.derive("invoiceLine", l.id.toString()),
        "invoiceId" to SharedIds.derive("invoice", l.invoiceId.toString()),
        "productId" to l.variantId?.let(productIdOf)?.let { SharedIds.derive("product", it.toString()) },
        "variantId" to l.variantId?.let { SharedIds.derive("variant", it.toString()) },
        "originalLineId" to l.originalLineId?.let { SharedIds.derive("invoiceLine", it.toString()) },
        "name" to l.productName,
        "variantLabel" to l.variantLabel,
        "barcode" to l.barcode,
        "hsnCode" to l.hsnCode,
        "customItem" to (l.variantId == null),
        "quantity" to l.quantity,
        "stockUnit" to (l.stockUnit ?: "UNIT"),
        "unitPricePaise" to l.unitPriceMinor.toString(),
        "mrpPaise" to l.mrpMinor?.toString(),
        // The app stores one discount per line, so it is the line discount; no part of the bill discount is spread to lines.
        "lineDiscountPaise" to l.discountMinor.toString(),
        "billDiscountSharePaise" to "0",
        "discountPaise" to l.discountMinor.toString(),
        "discountReason" to l.discountReason,
        "taxablePaise" to l.taxableMinor.toString(),
        "gstRateBasisPoints" to l.gstRateBasisPoints,
        "cgstPaise" to l.cgstMinor.toString(),
        "sgstPaise" to l.sgstMinor.toString(),
        "igstPaise" to l.igstMinor.toString(),
        "totalPaise" to l.totalMinor.toString(),
        "returnedQuantity" to l.returnedQuantity,
    )

    fun payment(p: PaymentEntity): Map<String, Any?> = mapOf(
        "id" to SharedIds.derive("payment", p.id.toString()),
        "invoiceId" to SharedIds.derive("invoice", p.invoiceId.toString()),
        "method" to p.method,
        "kind" to p.kind,
        "amountPaise" to p.amountMinor.toString(),
        "reference" to p.reference,
        "tenderedPaise" to p.tenderedMinor?.toString(),
        "changePaise" to p.changeMinor?.toString(),
        "createdAt" to p.createdAt,
    )
}
