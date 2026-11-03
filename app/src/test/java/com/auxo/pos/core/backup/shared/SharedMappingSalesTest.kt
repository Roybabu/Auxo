// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.CustomerEntity
import com.auxo.pos.data.local.entity.InvoiceEntity
import com.auxo.pos.data.local.entity.InvoiceLineEntity
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.local.entity.PaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedMappingSalesTest {
    private fun invoice(discountMinor: Long = 0L, buyerName: String? = null) = InvoiceEntity(
        id = 4, number = "INV/26-27/00001", type = "SALE", createdAt = 500,
        sellerName = "Shop", sellerAddress = "Road", sellerPhone = "9", sellerGstin = "27AAPFU0939F1ZV", sellerStateCode = "27",
        buyerName = buyerName, buyerPhone = null, buyerGstin = null, placeOfSupply = "27", gstRegistered = true,
        pricesIncludeTax = true, interState = false, subtotalMinor = 20000, discountMinor = discountMinor, taxableMinor = 19000,
        cgstMinor = 900, sgstMinor = 900, igstMinor = 0, roundOffMinor = 0, grandTotalMinor = 20000 - discountMinor,
        savingsMinor = discountMinor, originalInvoiceId = null, originalInvoiceNumber = null, note = null,
    )

    @Test
    fun customer_balance_comes_from_the_ledger_and_uses_spec_names() {
        val customer = SharedMapping.customer(
            CustomerEntity(id = 5, name = "Asha", phone = "9", email = null, gstin = null, stateCode = null, address = null,
                creditLimitMinor = 100000, notes = null, createdAt = 1, updatedAt = 2),
            balanceMinor = -5000,
        )
        assertEquals("-5000", customer["balancePaise"])
        assertEquals("100000", customer["creditLimitPaise"])
        assertEquals(false, customer["onHold"])
        assertEquals(SharedIds.derive("customer", "5"), customer["id"])
    }

    @Test
    fun ledger_entry_names_its_invoice_by_number() {
        val entry = SharedMapping.ledgerEntry(
            LedgerEntryEntity(id = 9, customerId = 5, type = "SALE", amountMinor = 20000, invoiceId = 4, method = null,
                reference = null, note = null, createdAt = 500),
        ) { id -> if (id == 4L) "INV/26-27/00001" else null }
        assertEquals("INV/26-27/00001", entry["invoiceNumber"])
        assertEquals(SharedIds.derive("invoice", "4"), entry["invoiceId"])
        assertEquals("SALE", entry["type"])
    }

    @Test
    fun invoice_keeps_the_whole_discount_because_the_app_does_not_store_the_split() {
        val row = SharedMapping.invoice(invoice(discountMinor = 500), customerId = 5)
        assertEquals("500", row["discountPaise"])
        assertEquals("500", row["lineDiscountPaise"])
        assertEquals("0", row["billDiscountPaise"])
        assertEquals(SharedIds.derive("customer", "5"), row["customerId"])
    }

    @Test
    fun invoice_has_a_seller_object_and_no_buyer_when_none_was_entered() {
        val row = SharedMapping.invoice(invoice(), customerId = null)
        assertEquals("Shop", (row["seller"] as Map<*, *>)["storeName"])
        assertNull(row["buyer"])
        assertNull(row["customerId"])
    }

    @Test
    fun invoice_line_names_its_product_through_the_variant() {
        val line = SharedMapping.invoiceLine(
            InvoiceLineEntity(id = 40, invoiceId = 4, variantId = 11, productName = "Shirt", variantLabel = "M",
                barcode = "890", hsnCode = null, quantity = 2, unitPriceMinor = 10000, mrpMinor = null, discountMinor = 300,
                taxableMinor = 19000, gstRateBasisPoints = 500, cgstMinor = 450, sgstMinor = 450, igstMinor = 0,
                totalMinor = 19000, returnedQuantity = 0, originalLineId = null),
        ) { variantId -> if (variantId == 11L) 7L else null }
        assertEquals(SharedIds.derive("product", "7"), line["productId"])
        assertEquals(SharedIds.derive("variant", "11"), line["variantId"])
        assertEquals(false, line["customItem"])
        assertEquals("UNIT", line["stockUnit"])
        assertEquals("300", line["lineDiscountPaise"])
    }

    @Test
    fun a_line_without_a_variant_is_a_custom_item() {
        val line = SharedMapping.invoiceLine(
            InvoiceLineEntity(id = 41, invoiceId = 4, variantId = null, productName = "Repair", variantLabel = "",
                barcode = "", hsnCode = null, quantity = 1, unitPriceMinor = 500, mrpMinor = null, discountMinor = 0,
                taxableMinor = 500, gstRateBasisPoints = 1800, cgstMinor = 45, sgstMinor = 45, igstMinor = 0,
                totalMinor = 590, returnedQuantity = 0, originalLineId = null),
        ) { null }
        assertEquals(true, line["customItem"])
        assertNull(line["productId"])
    }

    @Test
    fun payment_keeps_tender_and_change_as_paise_text() {
        val payment = SharedMapping.payment(
            PaymentEntity(id = 60, invoiceId = 4, method = "CASH", kind = "PAYMENT", amountMinor = 20000, reference = null,
                tenderedMinor = 20500, changeMinor = 500, createdAt = 500),
        )
        assertEquals("20500", payment["tenderedPaise"])
        assertEquals("500", payment["changePaise"])
        assertEquals(SharedIds.derive("invoice", "4"), payment["invoiceId"])
    }
}
