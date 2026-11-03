// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRestorePlannerTest {

    private fun document(vararg tables: Pair<String, List<Map<String, Any?>>>): SharedBackupDocument {
        val all = SharedFile.TABLES.associateWith { emptyList<Map<String, Any?>>() }.toMutableMap()
        tables.forEach { (name, rows) -> all[name] = rows }
        return SharedBackupReader.check(
            mapOf(
                "format" to "auxo-backup", "version" to 1L, "sharedModelVersion" to 1L, "createdAt" to 5L,
                "source" to mapOf("platform" to "web", "appVersion" to "1"), "shopName" to "Shop",
                "tables" to all, "photos" to emptyList<Any?>(),
            ),
        )
    }

    private val shopSettings = mapOf<String, Any?>(
        "id" to "shop", "storeName" to "Shop", "address" to "Road", "gstin" to "27AAPFU0939F1ZV", "stateCode" to "27",
        "gstRegistered" to true, "pricesIncludeTax" to true, "roundOff" to true, "invoicePrefix" to "INV",
        "creditNotePrefix" to "CN", "phone" to "", "fssai" to "", "upiId" to "", "upiPayeeName" to "",
        "receiptPaper" to "THERMAL_80", "receiptFooter" to "Thanks", "receiptLanguage" to "en",
        "receiptShowHsn" to true, "receiptShowTaxBreakdown" to true, "receiptShowCustomer" to true,
        "receiptShowSavings" to true, "receiptShowBarcode" to true, "receiptShowUpiQr" to false,
        "employeeDiscountLimitBp" to 0L, "expiryWarningDays" to 30L,
    )

    private val general = mapOf<String, Any?>(
        "id" to "cat-general", "name" to "General", "profile" to "GENERAL", "enabled" to true, "sortOrder" to 1L, "builtIn" to true,
    )

    private fun mug(categoryId: String = "cat-general") = mapOf<String, Any?>(
        "id" to "p1", "name" to "Mug", "category" to "GENERAL", "brand" to null, "description" to null, "material" to null,
        "dietType" to null, "hsnCode" to null, "gstRateBasisPoints" to null, "apparelGst" to false,
        "createdAt" to 1L, "updatedAt" to 2L, "stockUnit" to "UNIT", "available" to true, "tracksStock" to true,
        "categoryId" to categoryId, "subcategory" to null, "preferredSupplierId" to null, "photoId" to null,
        "warrantyMonths" to null, "prescriptionRequired" to false, "metal" to null, "makingChargeBasisPoints" to null,
    )

    private fun mugVariant() = mapOf<String, Any?>(
        "id" to "v1", "productId" to "p1", "sku" to "", "barcode" to "890", "size" to "", "colour" to "",
        "netQuantityMilli" to null, "netQuantityUnit" to null, "pricePaise" to "1000", "mrpPaise" to null, "costPaise" to null,
        "stockQuantity" to 3L, "lowStockThreshold" to 1L, "reorderQuantity" to 2L, "photoId" to null,
    )

    private fun asha(id: String = "c1") = mapOf<String, Any?>(
        "id" to id, "name" to "Asha", "phone" to null, "email" to null, "gstin" to null, "stateCode" to null,
        "address" to null, "notes" to null, "creditLimitPaise" to null, "balancePaise" to "0", "onHold" to false,
        "createdAt" to 1L, "updatedAt" to 1L,
    )

    private fun invoice(id: String, original: String? = null) = mapOf<String, Any?>(
        "id" to id, "number" to "INV/$id", "type" to "SALE", "createdAt" to 10L,
        "seller" to mapOf("storeName" to "Shop", "address" to "Road", "gstin" to "27AAPFU0939F1ZV", "stateCode" to "27", "phone" to "", "fssai" to ""),
        "buyer" to null, "customerId" to "c1", "originalInvoiceId" to original, "originalInvoiceNumber" to null, "note" to null,
        "placeOfSupply" to "27", "gstRegistered" to true, "pricesIncludeTax" to true, "interState" to false,
        "subtotalPaise" to "1000", "discountPaise" to "0", "taxablePaise" to "1000", "cgstPaise" to "0", "sgstPaise" to "0",
        "igstPaise" to "0", "roundOffPaise" to "0", "roundedToRupee" to false, "grandTotalPaise" to "1000", "savingsPaise" to "0",
        "billDiscountReason" to null,
    )

    private fun ledgerEntry(id: String, createdAt: Long) = mapOf<String, Any?>(
        "id" to id, "customerId" to "c1", "type" to "SALE", "amountPaise" to "100", "invoiceId" to null,
        "invoiceNumber" to null, "method" to null, "reference" to null, "createdAt" to createdAt, "note" to null,
    )

    private fun loyaltyRow(id: String, createdAt: Long) = mapOf<String, Any?>(
        "id" to id, "customerId" to "c1", "type" to "EARNED", "points" to 5L, "invoiceId" to null,
        "sourceInvoiceId" to null, "createdAt" to createdAt, "note" to null,
    )

    @Test
    fun aValidFile_makesAPlan_withTheRowsAndTheirLinks() {
        val plan = SharedRestorePlanner.plan(
            document(
                "settings" to listOf(shopSettings), "categories" to listOf(general), "products" to listOf(mug()),
                "variants" to listOf(mugVariant()), "customers" to listOf(asha()), "invoices" to listOf(invoice("i1")),
            ),
        )

        assertEquals("Shop", plan.shopName)
        assertEquals(1, plan.rowCounts["products"])
        assertEquals(listOf("product" to "p1"), plan.products[0].links)
        val variant = plan.variants[0].build(LocalIds { _, _ -> 7L })
        assertEquals(7L, variant.productId)
        assertEquals(1000L, variant.priceMinor)
        assertNull(variant.sku)
    }

    @Test
    fun aProductWithAMissingCategory_isRefused() {
        val problem = assertThrows(RestoreProblem::class.java) {
            SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "products" to listOf(mug(categoryId = "cat-missing"))))
        }
        assertTrue(problem.message!!.contains("cat-missing"))
    }

    @Test
    fun twoRowsWithTheSameId_areRefused() {
        val problem = assertThrows(RestoreProblem::class.java) {
            SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "customers" to listOf(asha(), asha())))
        }
        assertTrue(problem.message!!.contains("same id"))
    }

    @Test
    fun aLoyaltyRow_thatMatchesNoLedgerEntry_isRefused() {
        val problem = assertThrows(RestoreProblem::class.java) {
            SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "customers" to listOf(asha()), "loyaltyLedger" to listOf(loyaltyRow("lp1", 99L))))
        }
        assertTrue(problem.message!!.contains("loyalty"))
    }

    @Test
    fun aLoyaltyRow_thatMatchesALedgerEntry_givesItThePoints_andItsOwnLink() {
        val plan = SharedRestorePlanner.plan(
            document(
                "settings" to listOf(shopSettings), "customers" to listOf(asha()),
                "customerLedger" to listOf(ledgerEntry("e1", 99L)), "loyaltyLedger" to listOf(loyaltyRow("lp1", 99L)),
            ),
        )
        val entry = plan.ledger[0].build(LocalIds { _, _ -> 1L })
        assertEquals(5, entry.points)
        assertEquals(listOf("ledger" to "e1", "loyalty" to "lp1"), plan.ledger[0].links)
    }

    @Test
    fun invoicesThatRefertoAnotherInvoice_arePlacedAfterIt() {
        val plan = SharedRestorePlanner.plan(
            document("settings" to listOf(shopSettings), "customers" to listOf(asha()), "invoices" to listOf(invoice("i2", original = "i1"), invoice("i1"))),
        )
        assertEquals("i1", plan.invoices[0].sharedId)
        assertEquals("i2", plan.invoices[1].sharedId)
    }

    @Test
    fun aHeldBill_keepsItsPlaceOfSupply_whenTheFileGivesOne() {
        val bill = mapOf<String, Any?>(
            "id" to "h1", "label" to "Table 2", "createdAt" to 1L, "updatedAt" to 1L, "itemCount" to 0L, "totalPaise" to "0",
            "tableNumber" to 2L, "tokenNumber" to null, "discount" to mapOf("kind" to "AMOUNT", "value" to "0", "reason" to ""),
            "interState" to true, "placeOfSupplyStateCode" to "29", "customerId" to "", "buyerName" to null,
            "buyerPhone" to null, "buyerGstin" to null,
        )
        val plan = SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "heldBills" to listOf(bill)))
        val payload = plan.heldBills[0].build(LocalIds { _, _ -> 0L }).payload
        assertEquals("29", com.auxo.pos.data.repository.HeldCartCodec.decode(payload)!!.placeOfSupplyStateCode)
    }
}

