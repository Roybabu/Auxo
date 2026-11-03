// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.AuditLogEntity
import com.auxo.pos.data.local.entity.LoyaltySettingsEntity
import com.auxo.pos.data.local.entity.NumberSequenceEntity
import com.auxo.pos.data.local.entity.StockCountEntity
import com.auxo.pos.domain.inventory.CountLine
import com.auxo.pos.data.local.entity.CustomerEntity
import com.auxo.pos.data.local.entity.DayClosingEntity
import com.auxo.pos.data.local.entity.StockMovementEntity
import com.auxo.pos.data.local.entity.InvoiceEntity
import com.auxo.pos.data.local.entity.InvoiceLineEntity
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.local.entity.PaymentEntity
import com.auxo.pos.data.local.entity.ProductEntity
import com.auxo.pos.data.local.entity.PurchaseOrderEntity
import com.auxo.pos.data.local.entity.PurchaseOrderLineEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptEntity
import com.auxo.pos.data.local.entity.PurchaseReceiptLineEntity
import com.auxo.pos.data.local.entity.ShopCategoryEntity
import com.auxo.pos.data.local.entity.ShopSubcategoryEntity
import com.auxo.pos.data.local.entity.SupplierEntity
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

    /** [categoryIdOf] gives the shared ID of a category row, so built-in categories match the web app. */
    fun product(p: ProductEntity, categoryIdOf: (Long) -> String?, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("product", p.id),
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
        "categoryId" to p.categoryId?.let(categoryIdOf),
        "subcategory" to p.subcategory,
        "description" to p.description,
        "material" to p.material,
        "dietType" to p.dietType,
        "hsnCode" to p.hsnCode,
        "preferredSupplierId" to p.preferredSupplierId?.let { ids.of("supplier", it) },
        "photoId" to p.photoFile?.let { SharedIds.derive("photo", it) },
        "warrantyMonths" to p.warrantyMonths,
        "prescriptionRequired" to p.prescriptionRequired,
        "metal" to p.metal,
        "makingChargeBasisPoints" to p.makingChargeBasisPoints,
    )

    fun variant(v: ProductVariantEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("variant", v.id),
        "productId" to ids.of("product", v.productId),
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

    fun stockBatch(b: StockBatchEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("batch", b.id),
        "variantId" to ids.of("variant", b.variantId),
        "batchNumber" to b.batchNumber,
        "expiryDate" to b.expiryEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
        "quantity" to b.quantity,
        "receivedAt" to b.receivedAt,
        "unitCostPaise" to b.unitCostMinor?.toString(),
    )

    /** The customer's balance is the sum of their ledger entries; positive means the customer owes the shop. */
    fun customer(c: CustomerEntity, balanceMinor: Long, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("customer", c.id),
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

    fun ledgerEntry(e: LedgerEntryEntity, invoiceNumber: (Long) -> String?, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("ledger", e.id),
        "customerId" to ids.of("customer", e.customerId),
        "type" to e.type,
        "amountPaise" to e.amountMinor.toString(),
        "invoiceId" to e.invoiceId?.let { ids.of("invoice", it) },
        "invoiceNumber" to e.invoiceId?.let(invoiceNumber),
        "method" to e.method,
        "reference" to e.reference,
        "createdAt" to e.createdAt,
        "note" to e.note,
    )

    /**
     * The Android app stores one discount for the invoice (line discounts plus each line's share of the bill discount).
     * It does not keep the two parts apart, so the whole amount is written as the line discount and the bill discount
     * is zero. The total discount is correct; only the split is not recorded.
     * Loyalty redemption is not stored on the invoice in this app, so those fields are zero.
     */
    fun invoice(i: InvoiceEntity, customerId: Long?, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> {
        val buyer = if (i.buyerName == null && i.buyerPhone == null && i.buyerGstin == null) null else mapOf(
            "name" to i.buyerName,
            "phone" to i.buyerPhone,
            "gstin" to i.buyerGstin,
            "stateCode" to null,
        )
        return mapOf(
            "id" to ids.of("invoice", i.id),
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
            "customerId" to customerId?.let { ids.of("customer", it) },
            "originalInvoiceId" to i.originalInvoiceId?.let { ids.of("invoice", it) },
            "originalInvoiceNumber" to i.originalInvoiceNumber,
            "note" to i.note,
            "returnReason" to null,
            "placeOfSupply" to i.placeOfSupply,
            "gstRegistered" to i.gstRegistered,
            "pricesIncludeTax" to i.pricesIncludeTax,
            "interState" to i.interState,
            "subtotalPaise" to i.subtotalMinor.toString(),
            "lineDiscountPaise" to i.discountMinor.toString(),
            "billDiscountPaise" to "0",
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
    fun invoiceLine(l: InvoiceLineEntity, productIdOf: (Long) -> Long?, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("invoiceLine", l.id),
        "invoiceId" to ids.of("invoice", l.invoiceId),
        "productId" to l.variantId?.let(productIdOf)?.let { ids.of("product", it) },
        "variantId" to l.variantId?.let { ids.of("variant", it) },
        "originalLineId" to l.originalLineId?.let { ids.of("invoiceLine", it) },
        "name" to l.productName,
        "variantLabel" to l.variantLabel,
        "barcode" to l.barcode,
        "hsnCode" to l.hsnCode,
        "customItem" to (l.variantId == null),
        "quantity" to l.quantity,
        "stockUnit" to (l.stockUnit ?: "UNIT"),
        "unitPricePaise" to l.unitPriceMinor.toString(),
        "mrpPaise" to l.mrpMinor?.toString(),
        // The app stores one discount per line, including that line's share of any bill discount, so it is written as the line discount.
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

    fun payment(p: PaymentEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("payment", p.id),
        "invoiceId" to ids.of("invoice", p.invoiceId),
        "method" to p.method,
        "kind" to p.kind,
        "amountPaise" to p.amountMinor.toString(),
        "reference" to p.reference,
        "tenderedPaise" to p.tenderedMinor?.toString(),
        "changePaise" to p.changeMinor?.toString(),
        "createdAt" to p.createdAt,
    )

    fun category(c: ShopCategoryEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.category(c.profile, c.builtIn, c.id),
        "name" to c.name,
        "profile" to c.profile,
        "enabled" to c.enabled,
        "sortOrder" to c.sortOrder,
        "builtIn" to c.builtIn,
    )

    fun subcategory(s: ShopSubcategoryEntity, categoryIdOf: (Long) -> String, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("subcategory", s.id),
        "categoryId" to categoryIdOf(s.categoryId),
        "name" to s.name,
        "sortOrder" to s.sortOrder,
    )

    fun supplier(s: SupplierEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("supplier", s.id),
        "name" to s.name,
        "contactPerson" to s.contactPerson,
        "phone" to s.phone,
        "email" to s.email,
        "gstin" to s.gstin,
        "stateCode" to s.stateCode,
        "address" to s.address,
        "notes" to s.notes,
        "active" to s.active,
        "createdAt" to s.createdAt,
        "updatedAt" to s.updatedAt,
    )

    fun purchaseOrder(o: PurchaseOrderEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("purchaseOrder", o.id),
        "number" to o.number,
        "supplierId" to ids.of("supplier", o.supplierId),
        "status" to o.status,
        "createdAt" to o.createdAt,
        "updatedAt" to o.updatedAt,
        "expectedDate" to o.expectedEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
        "note" to o.note,
    )

    fun purchaseOrderLine(l: PurchaseOrderLineEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("purchaseOrderLine", l.id),
        "orderId" to ids.of("purchaseOrder", l.orderId),
        "variantId" to l.variantId?.let { ids.of("variant", it) },
        "productName" to l.productName,
        "variantLabel" to l.variantLabel,
        "stockUnit" to l.stockUnit,
        "quantityOrdered" to l.quantityOrdered,
        "quantityReceived" to l.quantityReceived,
        "unitCostPaise" to l.unitCostMinor.toString(),
    )

    /** The app does not keep a request ID per receipt, so one is made from the receipt's own ID. */
    fun receipt(r: PurchaseReceiptEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("receipt", r.id),
        "number" to r.number,
        "supplierId" to r.supplierId?.let { ids.of("supplier", it) },
        "supplierName" to null,
        "orderId" to r.orderId?.let { ids.of("purchaseOrder", it) },
        "orderNumber" to null,
        "supplierInvoiceNumber" to r.supplierInvoiceNumber,
        "receivedAt" to r.receivedAt,
        "note" to r.note,
        "totalPaise" to r.totalMinor.toString(),
        "requestId" to "receipt-${r.id}",
    )

    fun receiptLine(l: PurchaseReceiptLineEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("receiptLine", l.id),
        "receiptId" to ids.of("receipt", l.receiptId),
        "variantId" to l.variantId?.let { ids.of("variant", it) },
        "orderLineId" to l.orderLineId?.let { ids.of("purchaseOrderLine", it) },
        "productName" to l.productName,
        "variantLabel" to l.variantLabel,
        "stockUnit" to l.stockUnit,
        "quantity" to l.quantity,
        "unitCostPaise" to l.unitCostMinor.toString(),
        "batchNumber" to l.batchNumber,
        "expiryDate" to l.expiryEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
        "batchId" to l.batchId?.let { ids.of("batch", it) },
    )

    fun dayClosing(d: DayClosingEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("dayClosing", d.id),
        "businessDate" to d.businessDate,
        "bills" to d.bills,
        "returns" to d.returns,
        "netSalesPaise" to d.netSalesMinor.toString(),
        "cashReceivedPaise" to d.cashReceivedMinor.toString(),
        "cashRefundedPaise" to d.cashRefundedMinor.toString(),
        "cashFromAccountsPaise" to d.cashFromAccountsMinor.toString(),
        "upiNetPaise" to d.upiNetMinor.toString(),
        "cardNetPaise" to d.cardNetMinor.toString(),
        "onAccountNetPaise" to d.onAccountNetMinor.toString(),
        "countedCashPaise" to d.countedCashMinor.toString(),
        "note" to d.note,
        "closedAt" to d.closedAt,
        "closedBy" to d.closedBy,
    )

    /** Actor IDs are kept as text for history only; they never create or match an account. */
    fun auditLog(a: AuditLogEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("audit", a.id),
        "createdAt" to a.createdAt,
        "actorId" to a.actorId.toString(),
        "actorName" to a.actorName,
        "actorRole" to a.actorRole,
        "action" to a.action,
        "entityType" to a.entityType,
        "entityId" to a.entityId?.toString(),
        "summary" to a.summary,
    )

    /** The receipt link is not a shared column yet, so it is not written here. */
    fun stockMovement(m: StockMovementEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("stockMovement", m.id),
        "variantId" to ids.of("variant", m.variantId),
        "quantityChange" to m.quantityChange,
        "reason" to m.reason,
        "note" to m.note,
        "createdAt" to m.createdAt,
        "batchId" to m.batchId?.let { ids.of("batch", it) },
        "invoiceId" to m.invoiceId?.let { ids.of("invoice", it) },
        "sourceInvoiceId" to null,
    )

    fun loyaltySettings(l: LoyaltySettingsEntity): Map<String, Any?> = mapOf(
        "id" to "rules",
        "enabled" to l.enabled,
        "spendPerPointPaise" to l.spendPerPointMinor.toString(),
        "pointValuePaise" to l.pointValueMinor.toString(),
    )

    /**
     * Points are stored on customer ledger entries. Positive points are written as EARNED and negative as REDEEMED.
     * The app does not record the difference between earned, reversed and bonus points, so those are not told apart.
     * Returns null for entries that carry no points.
     */
    fun loyaltyEntry(e: LedgerEntryEntity, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?>? {
        if (e.points == 0) return null
        return mapOf(
            "id" to ids.of("loyalty", e.id),
            "customerId" to ids.of("customer", e.customerId),
            "type" to if (e.points > 0) "EARNED" else "REDEEMED",
            "points" to e.points,
            "invoiceId" to e.invoiceId?.let { ids.of("invoice", it) },
            "sourceInvoiceId" to null,
            "createdAt" to e.createdAt,
            "note" to e.note,
        )
    }

    fun numberSequence(n: NumberSequenceEntity): Map<String, Any?> = mapOf(
        "name" to n.name,
        "nextValue" to n.nextValue,
    )

    /** Matched lines are the ones where the count agreed with the expected stock. */
    fun stockCount(c: StockCountEntity, lines: List<CountLine>, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("stockCount", c.id),
        "status" to c.status,
        "startedAt" to c.startedAt,
        "finishedAt" to c.finishedAt,
        "reason" to c.reason,
        "note" to c.note,
        "adjustedCount" to c.adjustedCount,
        "matchedCount" to lines.count { it.counted == it.expected },
    )

    fun stockCountLine(countId: Long, index: Int, line: CountLine, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to SharedIds.derive("stockCountLine", "$countId|$index"),
        "stockCountId" to ids.of("stockCount", countId),
        "variantId" to ids.of("variant", line.variantId),
        "expectedQuantity" to line.expected,
        "countedQuantity" to line.counted,
    )
}
