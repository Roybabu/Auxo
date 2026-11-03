// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.core.backup.SharedBackupContainer
import com.auxo.pos.data.local.dao.SharedExportDao
import com.auxo.pos.data.repository.HeldCartCodec
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.inventory.CountLine
import com.auxo.pos.domain.inventory.StockCounts
import java.io.File

/**
 * Gathers every table from the database, maps it to the shared format, and makes the encrypted file.
 * It only reads. Any record that cannot be read or mapped stops the export, so a backup is never partial.
 */
class SharedExport(
    private val dao: SharedExportDao,
    /** Turns a held bill or quotation payload into its cart. Injected so tests need no JSON library. */
    private val decodeCart: (String) -> Cart? = HeldCartCodec::decode,
    /** Turns a stock count payload into its lines. */
    private val decodeCounts: (String) -> List<CountLine> = StockCounts::decode,
    /** Finds a product or variant photo file by name, or null when it is missing. */
    private val photoFile: (String) -> File?,
) {
    suspend fun build(
        createdAt: Long,
        appVersion: String,
        metalRates: Map<String, String> = emptyMap(),
        logoJpeg: ByteArray? = null,
    ): Map<String, Any?> {
        val logoPhotoId = logoJpeg?.let { SharedIds.derive("photo", STORE_LOGO_NAME) }
        val logoPhoto = logoJpeg?.let {
            mapOf<String, Any?>("id" to logoPhotoId, "mimeType" to "image/jpeg", "dataBase64" to java.util.Base64.getEncoder().encodeToString(it))
        }
        val settings = dao.storeSettings().firstOrNull() ?: throw IllegalStateException("There are no shop details to back up.")
        val ids = SharedIdResolver(
            dao.sharedIdLinks().groupBy({ it.tableName }, { it.localId to it.sharedId }).mapValues { (_, pairs) -> pairs.toMap() },
        )
        val categories = dao.categories()
        val categoryById = categories.associateBy { it.id }
        val categoryIdOf: (Long) -> String? = { id -> categoryById[id]?.let { ids.category(it.profile, it.builtIn, id) } }

        val products = dao.products()
        val variants = dao.variants()
        val variantProduct = variants.associate { it.id to it.productId }

        val ledger = dao.ledgerEntries()
        val balanceByCustomer = ledger.groupingBy { it.customerId }.fold(0L) { total, entry -> total + entry.amountMinor }
        val invoices = dao.invoices()
        val invoiceNumber = invoices.associate { it.id to it.number }

        val heldBills = dao.heldBills()
        val heldRows = heldBills.map { bill ->
            val cart = readCart(bill.payload, "held bill ${bill.id}")
            Triple(bill, cart, SharedMappingCarts.heldBill(bill, cart, settings.stateCode, ids = ids))
        }
        val quotations = dao.quotations()
        val quoteRows = quotations.map { q -> Triple(q, readCart(q.payload, "quotation ${q.number}"), Unit) }
        val counts = dao.stockCounts()
        val countRows = counts.map { c -> Triple(c, decodeCounts(c.payload), Unit) }

        val supplierRows = dao.suppliers()
        val orderRows = dao.purchaseOrders()
        val supplierName = supplierRows.associate { it.id to it.name }
        val orderNumber = orderRows.associate { it.id to it.number }

        val photoNames = (products.mapNotNull { it.photoFile } + variants.mapNotNull { it.photoFile }).distinct()
        val photos = photoNames.map { name ->
            val file = photoFile(name) ?: throw IllegalStateException("A product photo is missing: $name")
            mapOf(
                "id" to SharedIds.photo(name),
                "mimeType" to "image/jpeg",
                "dataBase64" to java.util.Base64.getEncoder().encodeToString(file.readBytes()),
            )
        }

        val tables = linkedMapOf<String, List<Map<String, Any?>>>(
            "settings" to listOf(SharedMapping.settings(settings, metalRatesPaise = metalRates, storeLogoPhotoId = logoPhotoId)),
            "categories" to categories.map { SharedMapping.category(it, ids = ids) },
            "subcategories" to dao.subcategories().map { SharedMapping.subcategory(it, categoryIdOf = { id -> categoryById[id]?.let { c -> ids.category(c.profile, c.builtIn, id) } ?: ids.of("category", id) }, ids = ids) },
            "products" to products.map { SharedMapping.product(it, categoryIdOf, ids = ids) },
            "variants" to variants.map { SharedMapping.variant(it, ids = ids) },
            "stockBatches" to dao.stockBatches().map { SharedMapping.stockBatch(it, ids = ids) },
            "stockMovements" to dao.stockMovements().map { SharedMapping.stockMovement(it, ids = ids) },
            "customers" to dao.customers().map { SharedMapping.customer(it, balanceByCustomer[it.id] ?: 0L, ids = ids) },
            "customerLedger" to ledger.filterNot { SharedMapping.isPointsEntry(it) }.map { SharedMapping.ledgerEntry(it, { id -> invoiceNumber[id] }, ids = ids) },
            "invoices" to invoices.map { SharedMapping.invoice(it, it.customerId, ids = ids) },
            "invoiceLines" to dao.invoiceLines().map { line -> SharedMapping.invoiceLine(line, { variantId -> variantProduct[variantId] }, ids = ids) },
            "payments" to dao.payments().map { SharedMapping.payment(it, ids = ids) },
            "suppliers" to supplierRows.map { SharedMapping.supplier(it, ids = ids) },
            "purchaseOrders" to orderRows.map { SharedMapping.purchaseOrder(it, ids = ids) },
            "purchaseOrderLines" to dao.purchaseOrderLines().map { SharedMapping.purchaseOrderLine(it, ids = ids) },
            "receipts" to dao.purchaseReceipts().map { SharedMapping.receipt(it, supplierName = it.supplierId?.let { id -> supplierName[id] }, orderNumber = it.orderId?.let { id -> orderNumber[id] }, ids = ids) },
            "receiptLines" to dao.purchaseReceiptLines().map { SharedMapping.receiptLine(it, ids = ids) },
            "dayClosings" to dao.dayClosings().map { SharedMapping.dayClosing(it, ids = ids) },
            "loyaltySettings" to dao.loyaltySettings().map { SharedMapping.loyaltySettings(it) },
            "loyaltyLedger" to ledger.mapNotNull { SharedMapping.loyaltyEntry(it, ids = ids) },
            "heldBills" to heldRows.map { it.third },
            "heldBillLines" to heldRows.flatMap { (bill, cart, _) -> SharedMappingCarts.heldBillLines(bill.id, cart, ids = ids) },
            "quotations" to quoteRows.map { (q, cart, _) -> SharedMappingCarts.quotation(q, cart, ids = ids) },
            "quotationLines" to quoteRows.flatMap { (q, cart, _) -> SharedMappingCarts.quotationLines(q.id, cart, ids = ids) },
            "stockCounts" to countRows.map { (c, lines, _) -> SharedMapping.stockCount(c, lines, ids = ids) },
            "stockCountLines" to countRows.flatMap { (c, lines, _) -> lines.mapIndexed { i, line -> SharedMapping.stockCountLine(c.id, i, line, ids = ids) } },
            "auditLog" to dao.auditLog().map { SharedMapping.auditLog(it, ids = ids) },
            "numberSequences" to dao.numberSequences().map { SharedMapping.numberSequence(it) },
            "extensions" to emptyList(),
        )
        return SharedFile.assemble(tables, shopName = settings.storeName, createdAt = createdAt, appVersion = appVersion, photos = photos + listOfNotNull(logoPhoto))
    }

    /** The encrypted file, ready to be written. Uses the same container as the web app. */
    suspend fun encrypt(
        passphrase: String,
        createdAt: Long,
        appVersion: String,
        metalRates: Map<String, String> = emptyMap(),
        logoJpeg: ByteArray? = null,
    ): ByteArray {
        val json = SharedFile.toJson(build(createdAt, appVersion, metalRates, logoJpeg)).toByteArray(Charsets.UTF_8)
        return SharedBackupContainer.encrypt(json, passphrase)
    }

    private companion object {
        /** The name the logo is stored under in the photos list. */
        const val STORE_LOGO_NAME = "store-logo"
    }

    private fun readCart(payload: String, what: String): Cart =
        decodeCart(payload) ?: throw IllegalStateException("The $what could not be read, so the backup was not made.")
}
