// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.data.local.entity.HeldBillEntity
import com.auxo.pos.data.local.entity.QuotationEntity
import com.auxo.pos.domain.model.GstRate
import java.time.LocalDate

/**
 * Held bills and quotations keep their cart in a text payload. The cart is decoded first (by HeldCartCodec), then
 * mapped here to the shared shapes: a parent row, and one line row per cart line with the line as its snapshot.
 */
object SharedMappingCarts {
    private fun discountMap(discount: Discount?, reason: String): Map<String, Any?> = when (discount) {
        is Discount.Amount -> mapOf("kind" to "AMOUNT", "value" to discount.value.minorUnits.toString(), "reason" to reason)
        is Discount.Percent -> mapOf("kind" to "PERCENT", "value" to discount.basisPoints.toString(), "reason" to reason)
        null -> mapOf("kind" to "AMOUNT", "value" to "0", "reason" to reason)
    }

    /** One cart line in the shape the web app uses. Custom items have no product or variant ID. */
    fun cartLine(line: CartLine, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> {
        val fixedRate = (line.gstRate as? GstRate.Fixed)?.basisPoints
        return mapOf(
            "productId" to if (line.isCustom) null else ids.of("product", line.productId),
            "variantId" to if (line.isCustom) null else ids.of("variant", line.variantId),
            "name" to line.productName,
            "variantLabel" to line.variantLabel,
            "barcode" to line.barcode,
            "hsnCode" to line.hsnCode,
            "customItem" to line.isCustom,
            "restaurantItem" to line.isRestaurantItem,
            "quantity" to line.quantity,
            "stockUnit" to line.stockUnit.name,
            "unitPricePaise" to line.unitPrice.minorUnits.toString(),
            "mrpPaise" to line.mrp?.minorUnits?.toString(),
            "gstRateBasisPoints" to fixedRate,
            "apparelGst" to (line.gstRate is GstRate.Apparel),
            "discount" to line.discount?.let { discountMap(it, line.discountReason) },
            "discountReason" to line.discountReason,
        )
    }

    /** The held bill row. [shopStateCode] is used to tell whether a sale is inter-state. */
    fun heldBill(entity: HeldBillEntity, cart: Cart, shopStateCode: String, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> {
        val customer = cart.customer
        val placeOfSupply = cart.placeOfSupplyStateCode
        return mapOf(
            "id" to ids.of("heldBill", entity.id),
            "label" to entity.label,
            "createdAt" to entity.createdAt,
            "updatedAt" to entity.updatedAt,
            "itemCount" to entity.itemCount,
            "totalPaise" to entity.totalMinor.toString(),
            "tableNumber" to entity.tableNumber,
            "tokenNumber" to entity.tokenNumber,
            "discount" to discountMap(cart.billDiscount, cart.billDiscountReason),
            "interState" to (placeOfSupply != null && placeOfSupply != shopStateCode),
            "placeOfSupplyStateCode" to placeOfSupply,
            "customerId" to (customer.customerId?.let { ids.of("customer", it) } ?: ""),
            "buyerName" to customer.name.ifBlank { null },
            "buyerPhone" to customer.phone.ifBlank { null },
            "buyerGstin" to customer.gstin.ifBlank { null },
        )
    }

    fun heldBillLines(heldBillId: Long, cart: Cart, ids: SharedIdResolver = SharedIdResolver()): List<Map<String, Any?>> = cart.lines.mapIndexed { index, line ->
        mapOf(
            "id" to SharedIds.derive("heldBillLine", "$heldBillId|$index"),
            "heldBillId" to ids.of("heldBill", heldBillId),
            "sortOrder" to index,
            "snapshot" to cartLine(line, ids = ids),
        )
    }

    fun quotation(entity: QuotationEntity, cart: Cart, ids: SharedIdResolver = SharedIdResolver()): Map<String, Any?> = mapOf(
        "id" to ids.of("quotation", entity.id),
        "number" to entity.number,
        "createdAt" to entity.createdAt,
        "validUntil" to LocalDate.ofEpochDay(entity.validUntilDay).toString(),
        "customerName" to entity.customerName,
        "customerPhone" to entity.customerPhone,
        "itemCount" to entity.itemCount,
        "totalPaise" to entity.totalMinor.toString(),
        "status" to entity.status,
        "convertedInvoiceId" to entity.convertedInvoiceId?.let { ids.of("invoice", it) },
        "customerId" to (cart.customer.customerId?.let { ids.of("customer", it) }),
    )

    fun quotationLines(quotationId: Long, cart: Cart, ids: SharedIdResolver = SharedIdResolver()): List<Map<String, Any?>> = cart.lines.mapIndexed { index, line ->
        mapOf(
            "id" to SharedIds.derive("quotationLine", "$quotationId|$index"),
            "quotationId" to ids.of("quotation", quotationId),
            "sortOrder" to index,
            "snapshot" to cartLine(line, ids = ids),
        )
    }
}
