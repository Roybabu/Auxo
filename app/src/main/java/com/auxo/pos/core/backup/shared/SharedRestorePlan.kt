// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.core.photos.PhotoFiles
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
import com.auxo.pos.data.repository.HeldCartCodec
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.inventory.CountLine
import com.auxo.pos.domain.inventory.StockCounts
import com.auxo.pos.domain.jewellery.JewelleryMetal
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.StockUnit

/** Looks up the local number of a record being restored, from its link key (such as "product") and its shared ID. */
fun interface LocalIds {
    fun of(key: String, sharedId: String): Long
}

/**
 * A record to write. [links] are the link keys it is recorded under, each with its shared ID. [build] makes the record
 * once the records it refers to have local numbers.
 */
class PlannedRow<T>(
    val sharedId: String,
    val links: List<Pair<String, String>>,
    val build: (LocalIds) -> T,
)

/** A product photo to put back, under a new file name. */
class PlannedPhoto(val name: String, val bytes: ByteArray)

/** Everything a restore will write. Making it checks the whole file and touches no database. */
class RestorePlan(
    val shopName: String,
    val createdAtMillis: Long,
    val rowCounts: Map<String, Int>,
    val settings: StoreSettingsEntity,
    val loyaltySettings: LoyaltySettingsEntity?,
    val numberSequences: List<NumberSequenceEntity>,
    val categories: List<PlannedRow<ShopCategoryEntity>>,
    val subcategories: List<PlannedRow<ShopSubcategoryEntity>>,
    val suppliers: List<PlannedRow<SupplierEntity>>,
    val products: List<PlannedRow<ProductEntity>>,
    val variants: List<PlannedRow<ProductVariantEntity>>,
    val batches: List<PlannedRow<StockBatchEntity>>,
    val customers: List<PlannedRow<CustomerEntity>>,
    val invoices: List<PlannedRow<InvoiceEntity>>,
    val invoiceLines: List<PlannedRow<InvoiceLineEntity>>,
    val ledger: List<PlannedRow<LedgerEntryEntity>>,
    val payments: List<PlannedRow<PaymentEntity>>,
    val purchaseOrders: List<PlannedRow<PurchaseOrderEntity>>,
    val purchaseOrderLines: List<PlannedRow<PurchaseOrderLineEntity>>,
    val receipts: List<PlannedRow<PurchaseReceiptEntity>>,
    val receiptLines: List<PlannedRow<PurchaseReceiptLineEntity>>,
    val stockMovements: List<PlannedRow<StockMovementEntity>>,
    val dayClosings: List<PlannedRow<DayClosingEntity>>,
    val heldBills: List<PlannedRow<HeldBillEntity>>,
    val quotations: List<PlannedRow<QuotationEntity>>,
    val stockCounts: List<PlannedRow<StockCountEntity>>,
    val auditLog: List<PlannedRow<AuditLogEntity>>,
    val photos: List<PlannedPhoto>,
    /** Today's rate per gram for each metal; empty when the file has none. */
    val metalRates: Map<JewelleryMetal, Money>,
    /** The shop logo as a JPEG, or null when the file has none. */
    val logoBytes: ByteArray?,
)

/**
 * Reads a checked shared backup into the records the app stores. Every reference is checked against the file, every
 * row is built once with a checking lookup, and only then is the plan returned. Nothing is written here.
 */
object SharedRestorePlanner {

    private const val GST_RULE_APPAREL = "APPAREL"

    /** The file table behind each link key that references use. */
    private val TABLE_OF_KEY: Map<String, String> = mapOf(
        "category" to "categories", "subcategory" to "subcategories", "supplier" to "suppliers",
        "product" to "products", "variant" to "variants", "batch" to "stockBatches",
        "customer" to "customers", "ledger" to "customerLedger", "invoice" to "invoices",
        "invoiceLine" to "invoiceLines", "payment" to "payments", "purchaseOrder" to "purchaseOrders",
        "purchaseOrderLine" to "purchaseOrderLines", "receipt" to "receipts", "receiptLine" to "receiptLines",
        "stockMovement" to "stockMovements", "dayClosing" to "dayClosings", "heldBill" to "heldBills",
        "quotation" to "quotations", "stockCount" to "stockCounts", "audit" to "auditLog", "loyalty" to "loyaltyLedger",
    )

    /** Reads and checks [document]. Throws [RestoreProblem] with the first problem found. */
    fun plan(document: SharedBackupDocument): RestorePlan {
        val tables: Map<String, List<SharedRow>> = SharedFile.TABLES.associateWith { name ->
            document.tables[name].orEmpty().mapIndexed { index, row -> SharedRow(name, index + 1, row) }
        }
        val knownIds: Map<String, Set<String>> = tables.mapValues { (name, rows) -> uniqueIds(name, rows) }
        val photoNames = newPhotoNames(document)
        val ctx = Ctx(photoNames)
        val checking = LocalIds { key, sharedId ->
            val table = TABLE_OF_KEY[key] ?: throw IllegalStateException("No table for link key $key")
            if (knownIds[table]?.contains(sharedId) == true) {
                0L
            } else {
                throw RestoreProblem("A link points to a ${plainName(key)} (\"$sharedId\") that is not in the file.")
            }
        }

        val settings = planSettings(tables.getValue("settings"))
        val settingsRow = tables.getValue("settings").singleOrNull()
        val metalRates = settingsRow?.record("metalRatesPaise")?.let { record ->
            record.keys().associate { key ->
                val metal = JewelleryMetal.entries.firstOrNull { it.name == key }
                    ?: throw RestoreProblem("The backup has a metal rate for a metal this app does not know ($key).")
                metal to Money(record.money(key))
            }
        }.orEmpty()
        val logoPhotoId = settingsRow?.refOrNull("storeLogoPhotoId")
        val logoBytes = logoPhotoId?.let { id ->
            document.photos.firstOrNull { it.id == id }?.bytes
                ?: throw RestoreProblem("The shop logo refers to a photo that is not in the file.")
        }
        val loyaltyRows = tables.getValue("loyaltySettings")
        if (loyaltyRows.size > 1) throw RestoreProblem("The backup has more than one loyalty settings row.")

        val categories = planCategories(tables.getValue("categories"))
        val subcategories = planSubcategories(tables.getValue("subcategories"))
        val suppliers = planSuppliers(tables.getValue("suppliers"))
        val products = planProducts(tables.getValue("products"), ctx)
        val variants = planVariants(tables.getValue("variants"), ctx)
        val batches = planBatches(tables.getValue("stockBatches"))
        val customers = planCustomers(tables.getValue("customers"))
        val invoices = planInvoices(parentsFirst(tables.getValue("invoices"), "originalInvoiceId"))
        val invoiceLines = planInvoiceLines(parentsFirst(tables.getValue("invoiceLines"), "originalLineId"))
        val ledger = planLedger(tables.getValue("customerLedger")) + planLoyalty(tables.getValue("loyaltyLedger"))
        val payments = planPayments(tables.getValue("payments"))
        val purchaseOrders = planPurchaseOrders(tables.getValue("purchaseOrders"))
        val purchaseOrderLines = planPurchaseOrderLines(tables.getValue("purchaseOrderLines"))
        val receipts = planReceipts(tables.getValue("receipts"))
        val receiptLines = planReceiptLines(tables.getValue("receiptLines"))
        val stockMovements = planStockMovements(tables.getValue("stockMovements"))
        val dayClosings = planDayClosings(tables.getValue("dayClosings"))
        val heldBills = planHeldBills(tables.getValue("heldBills"), tables.getValue("heldBillLines"), knownIds.getValue("heldBills"))
        val quotations = planQuotations(tables.getValue("quotations"), tables.getValue("quotationLines"), knownIds.getValue("quotations"))
        val stockCounts = planStockCounts(tables.getValue("stockCounts"), tables.getValue("stockCountLines"), knownIds.getValue("stockCounts"))
        val auditLog = planAuditLog(tables.getValue("auditLog"))

        // Build every row once with the checking lookup, so a bad reference or field is found before anything is written.
        listOf<List<PlannedRow<*>>>(
            categories, subcategories, suppliers, products, variants, batches, customers, invoices, invoiceLines,
            ledger, payments, purchaseOrders, purchaseOrderLines, receipts, receiptLines, stockMovements,
            dayClosings, heldBills, quotations, stockCounts, auditLog,
        ).forEach { rows -> rows.forEach { it.build(checking) } }

        return RestorePlan(
            shopName = document.shopName,
            createdAtMillis = document.createdAtMillis,
            rowCounts = SharedFile.TABLES.associateWith { document.rowCount(it) },
            settings = settings,
            loyaltySettings = loyaltyRows.singleOrNull()?.let(::planLoyaltySettings),
            numberSequences = tables.getValue("numberSequences").map { row ->
                NumberSequenceEntity(name = row.string("name"), nextValue = row.long("nextValue"))
            },
            categories = categories,
            subcategories = subcategories,
            suppliers = suppliers,
            products = products,
            variants = variants,
            batches = batches,
            customers = customers,
            invoices = invoices,
            invoiceLines = invoiceLines,
            ledger = ledger,
            payments = payments,
            purchaseOrders = purchaseOrders,
            purchaseOrderLines = purchaseOrderLines,
            receipts = receipts,
            receiptLines = receiptLines,
            stockMovements = stockMovements,
            dayClosings = dayClosings,
            heldBills = heldBills,
            quotations = quotations,
            stockCounts = stockCounts,
            auditLog = auditLog,
            // The logo is not a product photo, so it is kept apart from the photos put back for products.
            photos = document.photos.filter { it.id != logoPhotoId }.map { PlannedPhoto(photoNames.getValue(it.id), it.bytes) },
            metalRates = metalRates,
            logoBytes = logoBytes,
        )
    }

    private fun SharedBackupDocument.rowCount(table: String): Int = tables[table]?.size ?: 0

    /** Photos get new file names, so the names never come from the file. */
    private fun newPhotoNames(document: SharedBackupDocument): Map<String, String> {
        val ids = document.photos.map { it.id }
        if (ids.toSet().size != ids.size) throw RestoreProblem("The backup holds two photos with the same id.")
        // A photo whose ID is a UUID keeps it: its file is named from the UUID, so a re-export gives the same ID.
        return ids.associateWith { id ->
            SharedIds.photoFileName(id) ?: throw RestoreProblem("A photo has an id (\"$id\") that is not a valid UUID.")
        }
    }

    private fun uniqueIds(table: String, rows: List<SharedRow>): Set<String> {
        val ids = rows.mapNotNull { it.stringOrNull("id") }
        if (ids.toSet().size != ids.size) throw RestoreProblem("Two rows in ${plainName(table)} have the same id.")
        return ids.toSet()
    }

    /** Orders rows so that each row comes after the row its [key] points to. A loop is refused. */
    private fun parentsFirst(rows: List<SharedRow>, key: String): List<SharedRow> {
        val inFile = rows.map { it.id() }.toSet()
        val placed = HashSet<String>()
        val ordered = ArrayList<SharedRow>()
        var pending = rows
        while (pending.isNotEmpty()) {
            val ready = pending.filter { row ->
                val parent = row.refOrNull(key)
                parent == null || parent !in inFile || parent in placed
            }
            if (ready.isEmpty()) throw RestoreProblem("Some rows refer to each other in a loop (\"$key\").")
            ordered += ready
            ready.forEach { placed += it.id() }
            pending = pending.filterNot { it in ready.toSet() }
        }
        return ordered
    }

    /** Lines belong to a parent row; a line whose parent is not in the file is refused. */
    private fun groupLines(lines: List<SharedRow>, parentKey: String, parents: Set<String>, what: String): Map<String, List<SharedRow>> {
        val grouped = lines.groupBy { it.ref(parentKey) }
        val orphan = grouped.keys.firstOrNull { it !in parents }
        if (orphan != null) throw RestoreProblem("A $what line refers to a $what that is not in the file ($orphan).")
        return grouped
    }

    private fun <T> planRow(sharedId: String, key: String, build: (LocalIds) -> T): PlannedRow<T> =
        PlannedRow(sharedId, listOf(key to sharedId), build)

    private fun planSettings(rows: List<SharedRow>): StoreSettingsEntity {
        val row = rows.singleOrNull() ?: throw RestoreProblem("The backup must hold exactly one shop settings row.")
        return StoreSettingsEntity(
            id = 1,
            storeName = row.string("storeName"),
            address = row.string("address"),
            phone = row.string("phone"),
            gstin = row.string("gstin"),
            stateCode = row.string("stateCode"),
            gstRegistered = row.bool("gstRegistered"),
            pricesIncludeTax = row.bool("pricesIncludeTax"),
            roundOff = row.bool("roundOff"),
            invoicePrefix = row.string("invoicePrefix"),
            creditNotePrefix = row.string("creditNotePrefix"),
            upiId = row.string("upiId"),
            upiPayeeName = row.string("upiPayeeName"),
            receiptPaper = row.string("receiptPaper"),
            receiptFooter = row.string("receiptFooter"),
            receiptShowHsn = row.bool("receiptShowHsn"),
            receiptShowTaxBreakup = row.bool("receiptShowTaxBreakdown"),
            receiptShowSavings = row.bool("receiptShowSavings"),
            receiptShowCustomer = row.bool("receiptShowCustomer"),
            receiptShowBarcode = row.bool("receiptShowBarcode"),
            receiptLanguage = row.stringOrNull("receiptLanguage"),
            receiptShowUpiQr = row.bool("receiptShowUpiQr"),
            employeeDiscountLimitBp = row.int("employeeDiscountLimitBp"),
            expiryWarningDays = row.int("expiryWarningDays"),
            fssai = row.string("fssai"),
        )
    }

    private fun planLoyaltySettings(row: SharedRow): LoyaltySettingsEntity = LoyaltySettingsEntity(
        id = 1,
        enabled = row.bool("enabled"),
        spendPerPointMinor = row.money("spendPerPointPaise"),
        pointValueMinor = row.money("pointValuePaise"),
    )

    private fun planCategories(rows: List<SharedRow>): List<PlannedRow<ShopCategoryEntity>> = rows.map { row ->
        planRow(row.id(), "category") {
            ShopCategoryEntity(
                id = 0,
                name = row.stringOrNull("name"),
                profile = row.string("profile"),
                enabled = row.bool("enabled"),
                sortOrder = row.int("sortOrder"),
                builtIn = row.bool("builtIn"),
            )
        }
    }

    private fun planSubcategories(rows: List<SharedRow>): List<PlannedRow<ShopSubcategoryEntity>> = rows.map { row ->
        planRow(row.id(), "subcategory") { ids ->
            ShopSubcategoryEntity(
                id = 0,
                categoryId = ids.of("category", row.ref("categoryId")),
                name = row.string("name"),
                sortOrder = row.int("sortOrder"),
            )
        }
    }

    private fun planSuppliers(rows: List<SharedRow>): List<PlannedRow<SupplierEntity>> = rows.map { row ->
        planRow(row.id(), "supplier") {
            SupplierEntity(
                id = 0,
                name = row.string("name"),
                contactPerson = row.stringOrNull("contactPerson"),
                phone = row.stringOrNull("phone"),
                email = row.stringOrNull("email"),
                gstin = row.stringOrNull("gstin"),
                stateCode = row.stringOrNull("stateCode"),
                address = row.stringOrNull("address"),
                notes = row.stringOrNull("notes"),
                active = row.bool("active"),
                createdAt = row.long("createdAt"),
                updatedAt = row.long("updatedAt"),
            )
        }
    }

    private fun planProducts(rows: List<SharedRow>, ctx: Ctx): List<PlannedRow<ProductEntity>> = rows.map { row ->
        planRow(row.id(), "product") { ids ->
            ProductEntity(
                id = 0,
                name = row.string("name"),
                brand = row.stringOrNull("brand"),
                category = row.string("category"),
                description = row.stringOrNull("description"),
                material = row.stringOrNull("material"),
                dietType = row.stringOrNull("dietType"),
                hsnCode = row.stringOrNull("hsnCode"),
                gstRule = if (row.bool("apparelGst")) GST_RULE_APPAREL else null,
                gstRateBasisPoints = row.intOrNull("gstRateBasisPoints"),
                createdAt = row.long("createdAt"),
                updatedAt = row.long("updatedAt"),
                stockUnit = row.string("stockUnit"),
                preferredSupplierId = row.refOrNull("preferredSupplierId")?.let { ids.of("supplier", it) },
                photoFile = ctx.photoFile(row, "photoId"),
                subcategory = row.stringOrNull("subcategory"),
                available = row.bool("available"),
                tracksStock = row.bool("tracksStock"),
                categoryId = row.refOrNull("categoryId")?.let { ids.of("category", it) },
                warrantyMonths = row.intOrNull("warrantyMonths"),
                prescriptionRequired = row.boolOrNull("prescriptionRequired") ?: false,
                metal = row.stringOrNull("metal"),
                makingChargeBasisPoints = row.intOrNull("makingChargeBasisPoints"),
            )
        }
    }

    private fun planVariants(rows: List<SharedRow>, ctx: Ctx): List<PlannedRow<ProductVariantEntity>> = rows.map { row ->
        planRow(row.id(), "variant") { ids ->
            ProductVariantEntity(
                id = 0,
                productId = ids.of("product", row.ref("productId")),
                sku = row.stringOrNull("sku")?.takeIf { it.isNotEmpty() },
                barcode = row.string("barcode"),
                size = row.stringOrNull("size")?.takeIf { it.isNotEmpty() },
                colour = row.stringOrNull("colour")?.takeIf { it.isNotEmpty() },
                netQuantityMilli = row.longOrNull("netQuantityMilli"),
                netQuantityUnit = row.stringOrNull("netQuantityUnit"),
                priceMinor = row.money("pricePaise"),
                mrpMinor = row.moneyOrNull("mrpPaise"),
                costMinor = row.moneyOrNull("costPaise"),
                stockQuantity = row.int("stockQuantity"),
                lowStockThreshold = row.int("lowStockThreshold"),
                batchNumber = null,
                expiryEpochDay = null,
                reorderQuantity = row.int("reorderQuantity"),
                photoFile = ctx.photoFile(row, "photoId"),
            )
        }
    }

    private fun planBatches(rows: List<SharedRow>): List<PlannedRow<StockBatchEntity>> = rows.map { row ->
        planRow(row.id(), "batch") { ids ->
            StockBatchEntity(
                id = 0,
                variantId = ids.of("variant", row.ref("variantId")),
                batchNumber = row.stringOrNull("batchNumber"),
                expiryEpochDay = row.epochDayOrNull("expiryDate"),
                quantity = row.int("quantity"),
                receivedAt = row.long("receivedAt"),
                unitCostMinor = row.moneyOrNull("unitCostPaise"),
            )
        }
    }

    private fun planCustomers(rows: List<SharedRow>): List<PlannedRow<CustomerEntity>> = rows.map { row ->
        planRow(row.id(), "customer") {
            CustomerEntity(
                id = 0,
                name = row.string("name"),
                phone = row.stringOrNull("phone"),
                email = row.stringOrNull("email"),
                gstin = row.stringOrNull("gstin"),
                stateCode = row.stringOrNull("stateCode"),
                address = row.stringOrNull("address"),
                creditLimitMinor = row.moneyOrNull("creditLimitPaise"),
                notes = row.stringOrNull("notes"),
                createdAt = row.long("createdAt"),
                updatedAt = row.long("updatedAt"),
            )
        }
    }

    private fun planInvoices(rows: List<SharedRow>): List<PlannedRow<InvoiceEntity>> = rows.map { row ->
        val seller = row.requireRecord("seller")
        val buyer = row.record("buyer")
        planRow(row.id(), "invoice") { ids ->
            InvoiceEntity(
                id = 0,
                number = row.string("number"),
                type = row.string("type"),
                createdAt = row.long("createdAt"),
                sellerName = seller.string("storeName"),
                sellerAddress = seller.string("address"),
                sellerPhone = seller.stringOrNull("phone") ?: "",
                sellerGstin = seller.string("gstin"),
                sellerStateCode = seller.string("stateCode"),
                sellerFssai = seller.stringOrNull("fssai") ?: "",
                buyerName = buyer?.stringOrNull("name"),
                buyerPhone = buyer?.stringOrNull("phone"),
                buyerGstin = buyer?.stringOrNull("gstin"),
                placeOfSupply = row.string("placeOfSupply"),
                gstRegistered = row.bool("gstRegistered"),
                pricesIncludeTax = row.bool("pricesIncludeTax"),
                interState = row.bool("interState"),
                subtotalMinor = row.money("subtotalPaise"),
                discountMinor = row.money("discountPaise"),
                taxableMinor = row.money("taxablePaise"),
                cgstMinor = row.money("cgstPaise"),
                sgstMinor = row.money("sgstPaise"),
                igstMinor = row.money("igstPaise"),
                roundOffMinor = row.money("roundOffPaise"),
                roundedToRupee = row.bool("roundedToRupee"),
                grandTotalMinor = row.money("grandTotalPaise"),
                savingsMinor = row.money("savingsPaise"),
                originalInvoiceId = row.refOrNull("originalInvoiceId")?.let { ids.of("invoice", it) },
                originalInvoiceNumber = row.stringOrNull("originalInvoiceNumber"),
                note = row.stringOrNull("note"),
                customerId = row.refOrNull("customerId")?.let { ids.of("customer", it) },
                discountReason = row.stringOrNull("billDiscountReason"),
            )
        }
    }

    private fun planInvoiceLines(rows: List<SharedRow>): List<PlannedRow<InvoiceLineEntity>> = rows.map { row ->
        planRow(row.id(), "invoiceLine") { ids ->
            InvoiceLineEntity(
                id = 0,
                invoiceId = ids.of("invoice", row.ref("invoiceId")),
                variantId = row.refOrNull("variantId")?.let { ids.of("variant", it) },
                productName = row.string("name"),
                variantLabel = row.string("variantLabel"),
                barcode = row.string("barcode"),
                hsnCode = row.stringOrNull("hsnCode"),
                quantity = row.int("quantity"),
                unitPriceMinor = row.money("unitPricePaise"),
                mrpMinor = row.moneyOrNull("mrpPaise"),
                discountMinor = row.money("discountPaise"),
                taxableMinor = row.money("taxablePaise"),
                gstRateBasisPoints = row.int("gstRateBasisPoints"),
                cgstMinor = row.money("cgstPaise"),
                sgstMinor = row.money("sgstPaise"),
                igstMinor = row.money("igstPaise"),
                totalMinor = row.money("totalPaise"),
                returnedQuantity = row.int("returnedQuantity"),
                originalLineId = row.refOrNull("originalLineId")?.let { ids.of("invoiceLine", it) },
                menuItemId = null,
                discountReason = row.stringOrNull("discountReason"),
                stockUnit = row.stringOrNull("stockUnit"),
            )
        }
    }

    private fun planLedger(rows: List<SharedRow>): List<PlannedRow<LedgerEntryEntity>> = rows.map { row ->
        planRow(row.id(), "ledger") { ids ->
            LedgerEntryEntity(
                id = 0,
                customerId = ids.of("customer", row.ref("customerId")),
                type = row.string("type"),
                amountMinor = row.money("amountPaise"),
                invoiceId = row.refOrNull("invoiceId")?.let { ids.of("invoice", it) },
                method = row.stringOrNull("method"),
                reference = row.stringOrNull("reference"),
                note = row.stringOrNull("note"),
                createdAt = row.long("createdAt"),
                points = 0,
            )
        }
    }

    /** Each loyalty row becomes a points-only ledger entry (zero money), as the app already stores points. */
    private fun planLoyalty(rows: List<SharedRow>): List<PlannedRow<LedgerEntryEntity>> = rows.map { row ->
        val type = when (val kind = row.string("type")) {
            "EARNED", "ON_TIME_BONUS" -> "POINTS_EARNED"
            "REDEEMED" -> "POINTS_REDEEMED"
            "REVERSED" -> "POINTS_REVERSED"
            else -> throw RestoreProblem("A loyalty row has a type this app does not know ($kind).")
        }
        planRow(row.id(), "loyalty") { ids ->
            LedgerEntryEntity(
                id = 0,
                customerId = ids.of("customer", row.ref("customerId")),
                type = type,
                amountMinor = 0,
                invoiceId = row.refOrNull("invoiceId")?.let { ids.of("invoice", it) },
                method = null,
                reference = null,
                note = row.stringOrNull("note"),
                createdAt = row.long("createdAt"),
                points = row.int("points"),
            )
        }
    }

    private fun planPayments(rows: List<SharedRow>): List<PlannedRow<PaymentEntity>> = rows.map { row ->
        planRow(row.id(), "payment") { ids ->
            PaymentEntity(
                id = 0,
                invoiceId = ids.of("invoice", row.ref("invoiceId")),
                method = row.string("method"),
                kind = row.string("kind"),
                amountMinor = row.money("amountPaise"),
                reference = row.stringOrNull("reference"),
                tenderedMinor = row.moneyOrNull("tenderedPaise"),
                changeMinor = row.moneyOrNull("changePaise"),
                createdAt = row.long("createdAt"),
            )
        }
    }

    private fun planPurchaseOrders(rows: List<SharedRow>): List<PlannedRow<PurchaseOrderEntity>> = rows.map { row ->
        planRow(row.id(), "purchaseOrder") { ids ->
            PurchaseOrderEntity(
                id = 0,
                number = row.string("number"),
                supplierId = ids.of("supplier", row.ref("supplierId")),
                status = row.string("status"),
                createdAt = row.long("createdAt"),
                updatedAt = row.long("updatedAt"),
                expectedEpochDay = row.epochDayOrNull("expectedDate"),
                note = row.stringOrNull("note"),
            )
        }
    }

    private fun planPurchaseOrderLines(rows: List<SharedRow>): List<PlannedRow<PurchaseOrderLineEntity>> = rows.map { row ->
        planRow(row.id(), "purchaseOrderLine") { ids ->
            PurchaseOrderLineEntity(
                id = 0,
                orderId = ids.of("purchaseOrder", row.ref("orderId")),
                variantId = row.refOrNull("variantId")?.let { ids.of("variant", it) },
                productName = row.string("productName"),
                variantLabel = row.string("variantLabel"),
                stockUnit = row.string("stockUnit"),
                quantityOrdered = row.int("quantityOrdered"),
                quantityReceived = row.int("quantityReceived"),
                unitCostMinor = row.money("unitCostPaise"),
            )
        }
    }

    private fun planReceipts(rows: List<SharedRow>): List<PlannedRow<PurchaseReceiptEntity>> = rows.map { row ->
        planRow(row.id(), "receipt") { ids ->
            PurchaseReceiptEntity(
                id = 0,
                number = row.string("number"),
                supplierId = row.refOrNull("supplierId")?.let { ids.of("supplier", it) },
                orderId = row.refOrNull("orderId")?.let { ids.of("purchaseOrder", it) },
                supplierInvoiceNumber = row.stringOrNull("supplierInvoiceNumber"),
                receivedAt = row.long("receivedAt"),
                note = row.stringOrNull("note"),
                totalMinor = row.money("totalPaise"),
            )
        }
    }

    private fun planReceiptLines(rows: List<SharedRow>): List<PlannedRow<PurchaseReceiptLineEntity>> = rows.map { row ->
        planRow(row.id(), "receiptLine") { ids ->
            PurchaseReceiptLineEntity(
                id = 0,
                receiptId = ids.of("receipt", row.ref("receiptId")),
                variantId = row.refOrNull("variantId")?.let { ids.of("variant", it) },
                orderLineId = row.refOrNull("orderLineId")?.let { ids.of("purchaseOrderLine", it) },
                productName = row.string("productName"),
                variantLabel = row.string("variantLabel"),
                stockUnit = row.string("stockUnit"),
                quantity = row.int("quantity"),
                unitCostMinor = row.money("unitCostPaise"),
                batchNumber = row.stringOrNull("batchNumber"),
                expiryEpochDay = row.epochDayOrNull("expiryDate"),
                batchId = row.refOrNull("batchId")?.let { ids.of("batch", it) },
            )
        }
    }

    private fun planStockMovements(rows: List<SharedRow>): List<PlannedRow<StockMovementEntity>> = rows.map { row ->
        planRow(row.id(), "stockMovement") { ids ->
            StockMovementEntity(
                id = 0,
                variantId = ids.of("variant", row.ref("variantId")),
                quantityChange = row.int("quantityChange"),
                reason = row.string("reason"),
                note = row.stringOrNull("note"),
                createdAt = row.long("createdAt"),
                invoiceId = row.refOrNull("invoiceId")?.let { ids.of("invoice", it) },
                batchId = row.refOrNull("batchId")?.let { ids.of("batch", it) },
                receiptId = null,
            )
        }
    }

    private fun planDayClosings(rows: List<SharedRow>): List<PlannedRow<DayClosingEntity>> = rows.map { row ->
        planRow(row.id(), "dayClosing") {
            DayClosingEntity(
                id = 0,
                businessDate = row.string("businessDate"),
                bills = row.int("bills"),
                returns = row.int("returns"),
                netSalesMinor = row.money("netSalesPaise"),
                cashReceivedMinor = row.money("cashReceivedPaise"),
                cashRefundedMinor = row.money("cashRefundedPaise"),
                cashFromAccountsMinor = row.money("cashFromAccountsPaise"),
                upiNetMinor = row.money("upiNetPaise"),
                cardNetMinor = row.money("cardNetPaise"),
                onAccountNetMinor = row.money("onAccountNetPaise"),
                countedCashMinor = row.money("countedCashPaise"),
                note = row.stringOrNull("note"),
                closedAt = row.long("closedAt"),
                closedBy = row.string("closedBy"),
            )
        }
    }

    private fun planHeldBills(rows: List<SharedRow>, lines: List<SharedRow>, parents: Set<String>): List<PlannedRow<HeldBillEntity>> {
        val byBill = groupLines(lines, "heldBillId", parents, "held bill")
        return rows.map { row ->
            val discount = row.record("discount")
            val billLines = byBill[row.id()].orEmpty()
            planRow(row.id(), "heldBill") { ids ->
                val customer = CustomerDetails(
                    name = row.stringOrNull("buyerName") ?: "",
                    phone = row.stringOrNull("buyerPhone") ?: "",
                    gstin = row.stringOrNull("buyerGstin") ?: "",
                    customerId = row.refOrNull("customerId")?.let { ids.of("customer", it) },
                )
                val cart = cartOf(
                    billLines, customer, discount?.let { discountOf(it, zeroMeansNone = true) }, discount?.stringOrNull("reason") ?: "", ids,
                    placeOfSupply = row.stringOrNull("placeOfSupplyStateCode")?.takeIf { it.isNotEmpty() },
                )
                HeldBillEntity(
                    id = 0,
                    label = row.string("label"),
                    createdAt = row.long("createdAt"),
                    updatedAt = row.long("updatedAt"),
                    itemCount = row.int("itemCount"),
                    totalMinor = row.money("totalPaise"),
                    payload = HeldCartCodec.encode(cart),
                    tableNumber = row.intOrNull("tableNumber"),
                    tokenNumber = row.intOrNull("tokenNumber"),
                )
            }
        }
    }

    private fun planQuotations(rows: List<SharedRow>, lines: List<SharedRow>, parents: Set<String>): List<PlannedRow<QuotationEntity>> {
        val byQuotation = groupLines(lines, "quotationId", parents, "quotation")
        return rows.map { row ->
            val billLines = byQuotation[row.id()].orEmpty()
            planRow(row.id(), "quotation") { ids ->
                val customer = CustomerDetails(
                    name = row.string("customerName"),
                    phone = row.string("customerPhone"),
                    customerId = row.refOrNull("customerId")?.let { ids.of("customer", it) },
                )
                val cart = cartOf(billLines, customer, null, "", ids)
                QuotationEntity(
                    id = 0,
                    number = row.string("number"),
                    createdAt = row.long("createdAt"),
                    validUntilDay = row.epochDay("validUntil"),
                    customerName = row.string("customerName"),
                    customerPhone = row.string("customerPhone"),
                    itemCount = row.int("itemCount"),
                    totalMinor = row.money("totalPaise"),
                    payload = HeldCartCodec.encode(cart),
                    status = row.string("status"),
                    convertedInvoiceId = row.refOrNull("convertedInvoiceId")?.let { ids.of("invoice", it) },
                )
            }
        }
    }

    private fun planStockCounts(rows: List<SharedRow>, lines: List<SharedRow>, parents: Set<String>): List<PlannedRow<StockCountEntity>> {
        val byCount = groupLines(lines, "stockCountId", parents, "stock count")
        return rows.map { row ->
            val countLines = byCount[row.id()].orEmpty()
            planRow(row.id(), "stockCount") { ids ->
                val counted = countLines.map { line ->
                    CountLine(
                        variantId = ids.of("variant", line.ref("variantId")),
                        expected = line.int("expectedQuantity"),
                        counted = line.int("countedQuantity"),
                    )
                }
                StockCountEntity(
                    id = 0,
                    startedAt = row.long("startedAt"),
                    status = row.string("status"),
                    payload = StockCounts.encode(counted),
                    finishedAt = row.longOrNull("finishedAt"),
                    reason = row.stringOrNull("reason"),
                    note = row.stringOrNull("note"),
                    adjustedCount = row.int("adjustedCount"),
                )
            }
        }
    }

    private fun planAuditLog(rows: List<SharedRow>): List<PlannedRow<AuditLogEntity>> = rows.map { row ->
        planRow(row.id(), "audit") {
            AuditLogEntity(
                id = 0,
                createdAt = row.long("createdAt"),
                actorId = row.string("actorId").toLongOrNull() ?: 0L,
                actorName = row.string("actorName"),
                actorRole = row.string("actorRole"),
                action = row.string("action"),
                entityType = row.string("entityType"),
                entityId = row.stringOrNull("entityId")?.toLongOrNull(),
                summary = row.string("summary"),
            )
        }
    }

    /** A cart from its lines, in the order the file gives them. */
    private fun cartOf(
        lines: List<SharedRow>,
        customer: CustomerDetails,
        billDiscount: Discount?,
        billReason: String,
        ids: LocalIds,
        placeOfSupply: String? = null,
    ): Cart =
        Cart(
            lines = lines.sortedBy { it.long("sortOrder") }.mapIndexed { index, line ->
                cartLine(line.requireRecord("snapshot"), index, ids)
            },
            billDiscount = billDiscount,
            billDiscountReason = billReason,
            customer = customer,
            placeOfSupplyStateCode = placeOfSupply,
        )

    private fun cartLine(snapshot: SharedRow, index: Int, ids: LocalIds): CartLine {
        val custom = snapshot.bool("customItem")
        val apparel = snapshot.bool("apparelGst")
        val fixedRate = snapshot.intOrNull("gstRateBasisPoints")
        return CartLine(
            // A one-off item has no product; it gets a key of its own within the cart.
            variantId = if (custom) CartLine.CUSTOM_KEY_BASE - index else ids.of("variant", snapshot.ref("variantId")),
            productId = if (custom) 0L else ids.of("product", snapshot.ref("productId")),
            productName = snapshot.string("name"),
            variantLabel = snapshot.string("variantLabel"),
            barcode = snapshot.string("barcode"),
            hsnCode = snapshot.stringOrNull("hsnCode"),
            unitPrice = Money(snapshot.money("unitPricePaise")),
            mrp = snapshot.moneyOrNull("mrpPaise")?.let { Money(it) },
            gstRate = when {
                apparel -> GstRate.Apparel
                fixedRate != null -> GstRate.Fixed(fixedRate)
                else -> null
            },
            quantity = snapshot.int("quantity"),
            discount = snapshot.record("discount")?.let { discountOf(it, zeroMeansNone = false) },
            discountReason = snapshot.stringOrNull("discountReason") ?: "",
            availableStock = if (custom) CartLine.UNLIMITED else null,
            isCustom = custom,
            stockUnit = stockUnitOf(snapshot.string("stockUnit")),
            isRestaurantItem = snapshot.bool("restaurantItem"),
        )
    }

    /** A discount as the file writes it. A bill with no discount is written as a zero amount. */
    private fun discountOf(record: SharedRow, zeroMeansNone: Boolean): Discount? {
        val value = record.money("value")
        return when (record.string("kind")) {
            "AMOUNT" -> if (value == 0L && zeroMeansNone) null else Discount.Amount(Money(value))
            "PERCENT" -> Discount.Percent(value.toInt())
            else -> throw RestoreProblem("The backup has a discount kind that this app does not know.")
        }
    }

    private fun stockUnitOf(name: String): StockUnit =
        StockUnit.entries.firstOrNull { it.name == name } ?: throw RestoreProblem("The backup has a stock unit that this app does not know ($name).")

    /** Photo lookups used while building rows. */
    private class Ctx(private val photoNames: Map<String, String>) {
        fun photoFile(row: SharedRow, key: String): String? = row.refOrNull(key)?.let { photoId ->
            photoNames[photoId] ?: throw RestoreProblem("A product or variant refers to a photo that is not in the file.")
        }
    }
}
