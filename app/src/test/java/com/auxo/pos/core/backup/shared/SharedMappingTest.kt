// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.ProductEntity
import com.auxo.pos.data.local.entity.ProductVariantEntity
import com.auxo.pos.data.local.entity.StockBatchEntity
import com.auxo.pos.data.local.entity.StoreSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMappingTest {
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @Test
    fun ids_are_stable_uuids_and_differ_by_table() {
        val first = SharedIds.derive("product", "7")
        assertTrue(uuid.matches(first))
        assertEquals(first, SharedIds.derive("product", "7"))
        assertFalse(first == SharedIds.derive("variant", "7"))
    }

    @Test
    fun json_escapes_text_and_writes_null_numbers_and_lists() {
        val json = SharedJson.encode(mapOf("a" to "x\"y\n", "b" to null, "c" to listOf(1, true)))
        assertEquals("""{"a":"x\"y\n","b":null,"c":[1,true]}""", json)
    }

    @Test
    fun settings_use_spec_names_and_defaults() {
        val settings = SharedMapping.settings(
            StoreSettingsEntity(
                storeName = "Shop", address = "Road", phone = "", gstin = "27AAPFU0939F1ZV", stateCode = "27",
                gstRegistered = true, pricesIncludeTax = true, roundOff = true, invoicePrefix = "INV",
                creditNotePrefix = "CN", upiId = "", upiPayeeName = "", receiptPaper = "THERMAL_80",
                receiptFooter = "Thanks", receiptShowHsn = true, receiptShowTaxBreakup = false,
                receiptShowSavings = true, receiptShowCustomer = true, receiptShowBarcode = true,
            ),
        )
        assertEquals("shop", settings["id"])
        assertEquals(false, settings["receiptShowTaxBreakdown"])
        assertEquals("en", settings["receiptLanguage"])
        assertEquals(emptyMap<String, String>(), settings["metalRatesPaise"])
    }

    @Test
    fun product_maps_gst_rule_and_references() {
        val product = SharedMapping.product(
            categoryIdOf = { SharedIds.derive("category", it.toString()) },
            p = ProductEntity(
                id = 7, name = "Shirt", brand = null, category = "GARMENT", description = null, material = null,
                dietType = null, createdAt = 1000, updatedAt = 2000, gstRule = "APPAREL", categoryId = 3,
                photoFile = "p.jpg", preferredSupplierId = 5,
            ),
        )
        assertEquals(SharedIds.derive("product", "7"), product["id"])
        assertEquals(SharedIds.derive("category", "3"), product["categoryId"])
        assertEquals(SharedIds.derive("photo", "p.jpg"), product["photoId"])
        assertEquals(SharedIds.derive("supplier", "5"), product["preferredSupplierId"])
        assertEquals(true, product["apparelGst"])
        assertEquals(true, product["available"])
        assertEquals("UNIT", product["stockUnit"])
    }

    @Test
    fun variant_money_is_paise_text_and_missing_text_is_empty() {
        val variant = SharedMapping.variant(
            ProductVariantEntity(
                id = 11, productId = 7, sku = null, barcode = "890", size = null, colour = "Blue",
                netQuantityMilli = null, netQuantityUnit = null, priceMinor = 49900, mrpMinor = 59900,
                costMinor = null, stockQuantity = 12, lowStockThreshold = 3, batchNumber = null,
                expiryEpochDay = null, reorderQuantity = 10,
            ),
        )
        assertEquals(SharedIds.derive("product", "7"), variant["productId"])
        assertEquals("49900", variant["pricePaise"])
        assertEquals("59900", variant["mrpPaise"])
        assertNull(variant["costPaise"])
        assertEquals("", variant["sku"])
        assertEquals("", variant["size"])
        assertEquals(12, variant["stockQuantity"])
        assertEquals(10, variant["reorderQuantity"])
    }

    @Test
    fun batch_expiry_day_becomes_an_iso_date() {
        val batch = SharedMapping.stockBatch(
            StockBatchEntity(id = 21, variantId = 11, batchNumber = "B1", expiryEpochDay = 0L, quantity = 5, receivedAt = 3000, unitCostMinor = 30000),
        )
        assertEquals("1970-01-01", batch["expiryDate"])
        assertEquals("30000", batch["unitCostPaise"])
        assertEquals(SharedIds.derive("variant", "11"), batch["variantId"])
    }
}
