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
        "id" to "d135779f-66d9-4444-8599-8e98d6fff86c", "name" to "General", "profile" to "GENERAL", "enabled" to true, "sortOrder" to 1L, "builtIn" to true,
    )

    private fun mug(categoryId: String = "d135779f-66d9-4444-8599-8e98d6fff86c") = mapOf<String, Any?>(
        "id" to "ec6ef230-f182-4039-8e79-4566b9c58adc", "name" to "Mug", "category" to "GENERAL", "brand" to null, "description" to null, "material" to null,
        "dietType" to null, "hsnCode" to null, "gstRateBasisPoints" to null, "apparelGst" to false,
        "createdAt" to 1L, "updatedAt" to 2L, "stockUnit" to "UNIT", "available" to true, "tracksStock" to true,
        "categoryId" to categoryId, "subcategory" to null, "preferredSupplierId" to null, "photoId" to null,
        "warrantyMonths" to null, "prescriptionRequired" to false, "metal" to null, "makingChargeBasisPoints" to null,
    )

    private fun mugVariant() = mapOf<String, Any?>(
        "id" to "6654c734-ccab-4f44-8ff0-825eb443dc7f", "productId" to "ec6ef230-f182-4039-8e79-4566b9c58adc", "sku" to "", "barcode" to "890", "size" to "", "colour" to "",
        "netQuantityMilli" to null, "netQuantityUnit" to null, "pricePaise" to "1000", "mrpPaise" to null, "costPaise" to null,
        "stockQuantity" to 3L, "lowStockThreshold" to 1L, "reorderQuantity" to 2L, "photoId" to null,
    )

    private fun asha(id: String = "a9f7e979-65d6-4f79-8a52-9102a973b8b9") = mapOf<String, Any?>(
        "id" to id, "name" to "Asha", "phone" to null, "email" to null, "gstin" to null, "stateCode" to null,
        "address" to null, "notes" to null, "creditLimitPaise" to null, "balancePaise" to "0", "onHold" to false,
        "createdAt" to 1L, "updatedAt" to 1L,
    )

    private fun invoice(id: String, original: String? = null) = mapOf<String, Any?>(
        "id" to id, "number" to "INV/$id", "type" to "SALE", "createdAt" to 10L,
        "seller" to mapOf("storeName" to "Shop", "address" to "Road", "gstin" to "27AAPFU0939F1ZV", "stateCode" to "27", "phone" to "", "fssai" to ""),
        "buyer" to null, "customerId" to "a9f7e979-65d6-4f79-8a52-9102a973b8b9", "originalInvoiceId" to original, "originalInvoiceNumber" to null, "note" to null,
        "placeOfSupply" to "27", "gstRegistered" to true, "pricesIncludeTax" to true, "interState" to false,
        "subtotalPaise" to "1000", "discountPaise" to "0", "taxablePaise" to "1000", "cgstPaise" to "0", "sgstPaise" to "0",
        "igstPaise" to "0", "roundOffPaise" to "0", "roundedToRupee" to false, "grandTotalPaise" to "1000", "savingsPaise" to "0",
        "billDiscountReason" to null,
    )

    private fun ledgerEntry(id: String, createdAt: Long) = mapOf<String, Any?>(
        "id" to id, "customerId" to "a9f7e979-65d6-4f79-8a52-9102a973b8b9", "type" to "SALE", "amountPaise" to "100", "invoiceId" to null,
        "invoiceNumber" to null, "method" to null, "reference" to null, "createdAt" to createdAt, "note" to null,
    )

    private fun loyaltyRow(id: String, createdAt: Long) = mapOf<String, Any?>(
        "id" to id, "customerId" to "a9f7e979-65d6-4f79-8a52-9102a973b8b9", "type" to "EARNED", "points" to 5L, "invoiceId" to null,
        "sourceInvoiceId" to null, "createdAt" to createdAt, "note" to null,
    )

    @Test
    fun aValidFile_makesAPlan_withTheRowsAndTheirLinks() {
        val plan = SharedRestorePlanner.plan(
            document(
                "settings" to listOf(shopSettings), "categories" to listOf(general), "products" to listOf(mug()),
                "variants" to listOf(mugVariant()), "customers" to listOf(asha()), "invoices" to listOf(invoice("9ce88802-f075-41e5-8e04-57ef51ece021")),
            ),
        )

        assertEquals("Shop", plan.shopName)
        assertEquals(1, plan.rowCounts["products"])
        assertEquals(listOf("product" to "ec6ef230-f182-4039-8e79-4566b9c58adc"), plan.products[0].links)
        val variant = plan.variants[0].build(LocalIds { _, _ -> 7L })
        assertEquals(7L, variant.productId)
        assertEquals(1000L, variant.priceMinor)
        assertNull(variant.sku)
    }

    @Test
    fun aProductWithAMissingCategory_isRefused() {
        val problem = assertThrows(RestoreProblem::class.java) {
            SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "products" to listOf(mug(categoryId = "cbfae827-dd5d-4718-80a5-2b626ea6354b"))))
        }
        assertTrue(problem.message!!.contains("cbfae827-dd5d-4718-80a5-2b626ea6354b"))
    }

    @Test
    fun twoRowsWithTheSameId_areRefused() {
        val problem = assertThrows(RestoreProblem::class.java) {
            SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "customers" to listOf(asha(), asha())))
        }
        assertTrue(problem.message!!.contains("same id"))
    }

    @Test
    fun aLoyaltyRow_becomesAPointsOnlyLedgerEntry_withItsOwnLink() {
        val plan = SharedRestorePlanner.plan(
            document(
                "settings" to listOf(shopSettings), "customers" to listOf(asha()),
                "customerLedger" to listOf(ledgerEntry("cd3dc8b6-cffb-41e4-863d-cbd857ca87da", 99L)), "loyaltyLedger" to listOf(loyaltyRow("73c99ccb-bdad-4ab0-89f5-981584198289", 99L)),
            ),
        )
        assertEquals(listOf("ledger" to "cd3dc8b6-cffb-41e4-863d-cbd857ca87da"), plan.ledger[0].links)
        assertEquals(listOf("loyalty" to "73c99ccb-bdad-4ab0-89f5-981584198289"), plan.ledger[1].links)
        val points = plan.ledger[1].build(LocalIds { _, _ -> 1L })
        assertEquals("POINTS_EARNED", points.type)
        assertEquals(0L, points.amountMinor)
        assertEquals(5, points.points)
        assertEquals(0, plan.ledger[0].build(LocalIds { _, _ -> 1L }).points)
    }

    @Test
    fun aLoyaltyRowOfAnUnknownType_isRefused() {
        val row = loyaltyRow("73c99ccb-bdad-4ab0-89f5-981584198289", 99L) + ("type" to "MYSTERY")
        val problem = assertThrows(RestoreProblem::class.java) {
            SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "customers" to listOf(asha()), "loyaltyLedger" to listOf(row)))
        }
        assertTrue(problem.message!!.contains("MYSTERY"))
    }

    @Test
    fun invoicesThatRefertoAnotherInvoice_arePlacedAfterIt() {
        val plan = SharedRestorePlanner.plan(
            document("settings" to listOf(shopSettings), "customers" to listOf(asha()), "invoices" to listOf(invoice("fc46f8e7-0489-490f-8373-d93109479ca5", original = "9ce88802-f075-41e5-8e04-57ef51ece021"), invoice("9ce88802-f075-41e5-8e04-57ef51ece021"))),
        )
        assertEquals("9ce88802-f075-41e5-8e04-57ef51ece021", plan.invoices[0].sharedId)
        assertEquals("fc46f8e7-0489-490f-8373-d93109479ca5", plan.invoices[1].sharedId)
    }

    @Test
    fun metalRatesAndTheLogo_areReadFromTheSettings_andTheLogoIsNotAProductPhoto() {
        val settings = shopSettings + mapOf(
            "metalRatesPaise" to mapOf("GOLD_22K" to "612300", "SILVER" to "9500"),
            "storeLogoPhotoId" to "11111111-2222-3333-4444-555555555555",
        )
        val photo = mapOf<String, Any?>(
            "id" to "11111111-2222-3333-4444-555555555555", "mimeType" to "image/jpeg",
            "dataBase64" to java.util.Base64.getEncoder().encodeToString("logo bytes".toByteArray()),
        )
        val tables = SharedFile.TABLES.associateWith { emptyList<Map<String, Any?>>() }.toMutableMap()
        tables["settings"] = listOf(settings)
        val document = SharedBackupReader.check(
            mapOf(
                "format" to "auxo-backup", "version" to 1L, "sharedModelVersion" to 1L, "createdAt" to 5L,
                "source" to mapOf("platform" to "web", "appVersion" to "1"), "shopName" to "Shop",
                "tables" to tables, "photos" to listOf(photo),
            ),
        )

        val plan = SharedRestorePlanner.plan(document)

        assertEquals(
            mapOf(com.auxo.pos.domain.jewellery.JewelleryMetal.GOLD_22K to com.auxo.pos.domain.model.Money(612300L),
                com.auxo.pos.domain.jewellery.JewelleryMetal.SILVER to com.auxo.pos.domain.model.Money(9500L)),
            plan.metalRates,
        )
        assertEquals("logo bytes", String(plan.logoBytes!!, Charsets.UTF_8))
        assertTrue(plan.photos.isEmpty())
    }

    @Test
    fun aRestoredPhoto_keepsItsIdentity_throughTheFileName() {
        val uuid = "01234567-89ab-cdef-0123-456789abcdef"
        val photo = mapOf<String, Any?>(
            "id" to uuid, "mimeType" to "image/jpeg",
            "dataBase64" to java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
        )
        val product = mugWithPhoto(uuid)
        val tables = SharedFile.TABLES.associateWith { emptyList<Map<String, Any?>>() }.toMutableMap()
        tables["settings"] = listOf(shopSettings)
        tables["categories"] = listOf(general)
        tables["products"] = listOf(product)
        val document = SharedBackupReader.check(
            mapOf(
                "format" to "auxo-backup", "version" to 1L, "sharedModelVersion" to 1L, "createdAt" to 5L,
                "source" to mapOf("platform" to "web", "appVersion" to "1"), "shopName" to "Shop",
                "tables" to tables, "photos" to listOf(photo),
            ),
        )

        val plan = SharedRestorePlanner.plan(document)

        assertEquals("01234567-89ab-cdef-0123-456789abcdef", SharedIds.photo(plan.photos[0].name))
        assertEquals(plan.photos[0].name, plan.products[0].build(LocalIds { _, _ -> 0L }).photoFile)
    }

    private fun mugWithPhoto(photoId: String) = mapOf<String, Any?>(
        "id" to "ec6ef230-f182-4039-8e79-4566b9c58adc", "name" to "Mug", "category" to "GENERAL", "brand" to null, "description" to null, "material" to null,
        "dietType" to null, "hsnCode" to null, "gstRateBasisPoints" to null, "apparelGst" to false,
        "createdAt" to 1L, "updatedAt" to 2L, "stockUnit" to "UNIT", "available" to true, "tracksStock" to true,
        "categoryId" to "d135779f-66d9-4444-8599-8e98d6fff86c", "subcategory" to null, "preferredSupplierId" to null, "photoId" to photoId,
        "warrantyMonths" to null, "prescriptionRequired" to false, "metal" to null, "makingChargeBasisPoints" to null,
    )

    @Test
    fun aPhotoWhoseIdIsNotAUuid_isRefused() {
        val photo = mapOf<String, Any?>("id" to "photo-1", "mimeType" to "image/jpeg", "dataBase64" to "AQID")
        val tables = SharedFile.TABLES.associateWith { emptyList<Map<String, Any?>>() }.toMutableMap()
        tables["settings"] = listOf(shopSettings)
        val doc = SharedBackupReader.check(mapOf(
            "format" to "auxo-backup", "version" to 1L, "sharedModelVersion" to 1L, "createdAt" to 5L,
            "source" to mapOf("platform" to "web", "appVersion" to "1"), "shopName" to "Shop",
            "tables" to tables, "photos" to listOf(photo),
        ))
        val problem = assertThrows(RestoreProblem::class.java) { SharedRestorePlanner.plan(doc) }
        assertTrue(problem.message!!.contains("UUID"))
    }

    @Test
    fun aRowIdThatIsNotAUuid_isRefused() {
        val problem = assertThrows(RestoreProblem::class.java) {
            SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "customers" to listOf(asha("customer-1"))))
        }
        assertTrue(problem.message!!.contains("not a valid UUID"))
    }

    @Test
    fun aLinkThatIsNotAUuid_isRefused() {
        val product = mug() + ("categoryId" to "General")
        val problem = assertThrows(RestoreProblem::class.java) {
            SharedRestorePlanner.plan(document("settings" to listOf(shopSettings), "categories" to listOf(general), "products" to listOf(product)))
        }
        assertTrue(problem.message!!.contains("category id"))
    }
}

