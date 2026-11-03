// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.dao.SharedExportDao
import com.auxo.pos.data.local.entity.SharedIdLinkEntity
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
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.inventory.CountLine
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A fake of the export queries, holding only the rows a test sets. */
private class FakeDao(
    val settingRows: List<StoreSettingsEntity> = emptyList(),
    val productRows: List<ProductEntity> = emptyList(),
    val heldRows: List<HeldBillEntity> = emptyList(),
    val ledgerRows: List<LedgerEntryEntity> = emptyList(),
    val customerRows: List<CustomerEntity> = emptyList(),
    val linkRows: List<SharedIdLinkEntity> = emptyList(),
) : SharedExportDao {
    override suspend fun storeSettings() = settingRows
    override suspend fun sharedIdLinks(): List<SharedIdLinkEntity> = linkRows
    override suspend fun loyaltySettings(): List<LoyaltySettingsEntity> = emptyList()
    override suspend fun categories(): List<ShopCategoryEntity> = emptyList()
    override suspend fun subcategories(): List<ShopSubcategoryEntity> = emptyList()
    override suspend fun products() = productRows
    override suspend fun variants(): List<ProductVariantEntity> = emptyList()
    override suspend fun stockBatches(): List<StockBatchEntity> = emptyList()
    override suspend fun stockMovements(): List<StockMovementEntity> = emptyList()
    override suspend fun customers() = customerRows
    override suspend fun ledgerEntries() = ledgerRows
    override suspend fun invoices(): List<InvoiceEntity> = emptyList()
    override suspend fun invoiceLines(): List<InvoiceLineEntity> = emptyList()
    override suspend fun payments(): List<PaymentEntity> = emptyList()
    override suspend fun suppliers(): List<SupplierEntity> = emptyList()
    override suspend fun purchaseOrders(): List<PurchaseOrderEntity> = emptyList()
    override suspend fun purchaseOrderLines(): List<PurchaseOrderLineEntity> = emptyList()
    override suspend fun purchaseReceipts(): List<PurchaseReceiptEntity> = emptyList()
    override suspend fun purchaseReceiptLines(): List<PurchaseReceiptLineEntity> = emptyList()
    override suspend fun dayClosings(): List<DayClosingEntity> = emptyList()
    override suspend fun heldBills() = heldRows
    override suspend fun quotations(): List<QuotationEntity> = emptyList()
    override suspend fun stockCounts(): List<StockCountEntity> = emptyList()
    override suspend fun auditLog(): List<AuditLogEntity> = emptyList()
    override suspend fun numberSequences(): List<NumberSequenceEntity> = emptyList()
}

class SharedExportTest {
    private val shop = StoreSettingsEntity(storeName = "Shop", address = "Road", phone = "", gstin = "27AAPFU0939F1ZV",
        stateCode = "27", gstRegistered = true, pricesIncludeTax = true, roundOff = true, invoicePrefix = "INV",
        creditNotePrefix = "CN", upiId = "", upiPayeeName = "", receiptPaper = "THERMAL_80", receiptFooter = "Thanks",
        receiptShowHsn = true, receiptShowTaxBreakup = true, receiptShowSavings = true, receiptShowCustomer = true,
        receiptShowBarcode = true)

    private fun export(dao: SharedExportDao, photo: (String) -> File? = { null }, decodeCart: (String) -> Cart? = { Cart() }) =
        SharedExport(dao, decodeCart = decodeCart, decodeCounts = { emptyList() }, photoFile = photo)

    @Test
    fun builds_every_table_for_a_small_shop() = kotlinx.coroutines.runBlocking {
        val file = export(FakeDao(settingRows = listOf(shop))).build(createdAt = 5, appVersion = "1.0")
        val tables = file["tables"] as Map<*, *>
        assertEquals(29, tables.size)
        assertEquals(1, (tables["settings"] as List<*>).size)
        assertEquals("shop", ((tables["settings"] as List<*>)[0] as Map<*, *>)["id"])
        assertEquals("Shop", file["shopName"])
    }

    @Test
    fun refuses_when_there_are_no_shop_details() = kotlinx.coroutines.runBlocking {
        val error = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { export(FakeDao()).build(createdAt = 5, appVersion = "1.0") }
        }
        assertTrue(error.message!!.contains("shop details"))
    }

    @Test
    fun refuses_when_a_held_bill_cannot_be_read() = kotlinx.coroutines.runBlocking {
        val bill = HeldBillEntity(id = 3, label = "T1", createdAt = 1, updatedAt = 2, itemCount = 1, totalMinor = 100, payload = "damaged")
        val error = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { export(FakeDao(settingRows = listOf(shop), heldRows = listOf(bill)), decodeCart = { null }).build(5, "1.0") }
        }
        assertTrue(error.message!!.contains("held bill 3"))
    }

    @Test
    fun refuses_when_a_product_photo_file_is_missing() = kotlinx.coroutines.runBlocking {
        val product = ProductEntity(id = 7, name = "Mug", brand = null, category = "GENERAL", description = null, material = null,
            dietType = null, createdAt = 1, updatedAt = 2, photoFile = "missing.jpg")
        val error = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { export(FakeDao(settingRows = listOf(shop), productRows = listOf(product))).build(5, "1.0") }
        }
        assertTrue(error.message!!.contains("missing.jpg"))
    }

    @Test
    fun customer_balance_is_the_sum_of_their_ledger() = kotlinx.coroutines.runBlocking {
        val customer = CustomerEntity(id = 5, name = "Asha", phone = null, email = null, gstin = null, stateCode = null, address = null,
            creditLimitMinor = null, notes = null, createdAt = 1, updatedAt = 2)
        val ledger = listOf(
            LedgerEntryEntity(id = 1, customerId = 5, type = "SALE", amountMinor = 20000, invoiceId = null, method = null, reference = null, note = null, createdAt = 1),
            LedgerEntryEntity(id = 2, customerId = 5, type = "PAYMENT", amountMinor = -5000, invoiceId = null, method = "CASH", reference = null, note = null, createdAt = 2),
        )
        val file = export(FakeDao(settingRows = listOf(shop), customerRows = listOf(customer), ledgerRows = ledger)).build(5, "1.0")
        val row = ((file["tables"] as Map<*, *>)["customers"] as List<*>)[0] as Map<*, *>
        assertEquals("15000", row["balancePaise"])
    }

    @Test
    fun held_bill_lines_come_from_the_decoded_cart() = kotlinx.coroutines.runBlocking {
        val bill = HeldBillEntity(id = 3, label = "T1", createdAt = 1, updatedAt = 2, itemCount = 1, totalMinor = 10000, payload = "ok")
        val line = CartLine(variantId = 11, productId = 7, productName = "Shirt", variantLabel = "M", barcode = "890", hsnCode = null,
            unitPrice = Money(10000), mrp = null, gstRate = GstRate.Fixed(500), quantity = 1, availableStock = null)
        val file = export(FakeDao(settingRows = listOf(shop), heldRows = listOf(bill)), decodeCart = { Cart(lines = listOf(line)) })
            .build(5, "1.0")
        val lines = (file["tables"] as Map<*, *>)["heldBillLines"] as List<*>
        assertEquals(1, lines.size)
    }

    @Test
    fun aRecordWithAStoredLink_keepsTheLinkedSharedId_andOthersGetTheDerivedOne() = kotlinx.coroutines.runBlocking {
        val product = ProductEntity(id = 7, name = "Mug", brand = null, category = "GENERAL", description = null, material = null,
            dietType = null, createdAt = 1, updatedAt = 2, photoFile = null)
        val link = SharedIdLinkEntity(tableName = "product", localId = 7L, sharedId = "linked-product-id")

        val linked = export(FakeDao(settingRows = listOf(shop), productRows = listOf(product), linkRows = listOf(link))).build(5, "1.0")
        val linkedProducts = (linked["tables"] as Map<*, *>)["products"] as List<*>
        assertEquals("linked-product-id", (linkedProducts[0] as Map<*, *>)["id"])

        val unlinked = export(FakeDao(settingRows = listOf(shop), productRows = listOf(product))).build(5, "1.0")
        val unlinkedProducts = (unlinked["tables"] as Map<*, *>)["products"] as List<*>
        assertEquals(SharedIds.derive("product", "7"), (unlinkedProducts[0] as Map<*, *>)["id"])
    }

    /** Temporary: prints an Android-made shared file so the web app can open it (see the round-trip check). */
    @Test
    fun printsTheAndroidFileForTheWebRoundTrip() = kotlinx.coroutines.runBlocking {
        val product = ProductEntity(id = 7, name = "Mug", brand = null, category = "GENERAL", description = null, material = null,
            dietType = null, createdAt = 1, updatedAt = 2, photoFile = null)
        val customer = CustomerEntity(id = 3, name = "Asha", phone = "9999900000", email = null, gstin = null, stateCode = null,
            address = null, creditLimitMinor = null, notes = null, createdAt = 1, updatedAt = 1)
        val bytes = export(FakeDao(settingRows = listOf(shop), productRows = listOf(product), customerRows = listOf(customer)))
            .encrypt("long shared phrase 91", 1_700_000_000_000L, "1.0")
        org.junit.Assert.fail("AUXO-FIXTURE-BEGIN " + java.util.Base64.getEncoder().encodeToString(bytes) + " AUXO-FIXTURE-END")
    }
}
