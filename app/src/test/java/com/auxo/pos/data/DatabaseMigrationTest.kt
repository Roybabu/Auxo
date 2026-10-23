// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.DishMigration
import com.auxo.pos.data.repository.ReturnResult
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.RoomSettingsRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.ReceiptPaper
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Builds a version 1 database from the committed schema (app/schemas/.../1.json),
 * fills it with Step 2 data, then opens it with the current app so Room runs the
 * 1 -> 2, 2 -> 3, 3 -> 4 and 4 -> 5 migrations and checks the result against the current schema.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"
    private var database: AuxoDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(name)
    }

    /** Creates the tables of schema [version] from its committed JSON and returns the open database. */
    private fun createFromSchema(version: Int): SQLiteDatabase {
        val schema = JSONObject(File("schemas/com.auxo.pos.data.local.AuxoDatabase/$version.json").readText()).getJSONObject("database")
        assertEquals(version, schema.getInt("version"))
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    private fun createVersion1() {
        val db = createFromSchema(1)

        // Step 2 data: a product, a variant with stock and its opening stock movement.
        db.execSQL(
            "INSERT INTO products (id, name, brand, category, description, material, diet_type, created_at, updated_at) " +
                "VALUES (1, 'Cotton T-Shirt', 'Sample Wear', 'GARMENT', NULL, 'Cotton', NULL, 100, 100)",
        )
        db.execSQL(
            "INSERT INTO product_variants (id, product_id, sku, barcode, size, colour, net_quantity_milli, net_quantity_unit, " +
                "price_minor, mrp_minor, cost_minor, stock_quantity, low_stock_threshold, batch_number, expiry_epoch_day) " +
                "VALUES (11, 1, 'TS-M', '${TestData.EAN[0]}', 'M', 'Navy', NULL, NULL, 49900, NULL, 25000, 8, 2, NULL, NULL)",
        )
        db.execSQL(
            "INSERT INTO stock_movements (id, variant_id, quantity_change, reason, note, created_at) " +
                "VALUES (1, 11, 8, 'INITIAL', NULL, 100)",
        )
        db.close()
    }

    @Test
    fun migrate1To2_keepsInventory_andBillingWorks() = runTest {
        createVersion1()
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val inventory = RoomInventoryRepository(db, TestData.clock)

        // Opening runs the migrations and Room validates every table against the current version.
        val product = inventory.getProduct(1)!!
        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)
        assertEquals("Cotton T-Shirt", product.name)
        assertEquals(8, product.variants.single().stockQuantity)
        assertEquals(Money(49_900), product.variants.single().sellingPrice)
        assertNull(product.gstRate) // new column, not set yet
        assertEquals(StockChangeReason.INITIAL, inventory.observeStockMovements(1).first().single().reason)

        // The new tables work with the migrated data.
        inventory.saveProduct(product.copy(gstRate = GstRate.Apparel, hsnCode = "6109"))
        val withGst = inventory.getProduct(1)!!
        assertEquals(GstRate.Apparel, withGst.gstRate)
        val cart = Cart().add(CartLine.from(withGst, withGst.variants.single(), quantity = 2))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val invoice = SaleBuilder.build(
            cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis(),
        )
        val result = RoomBillingRepository(db).recordSale(invoice, "INV")
        assertTrue(result is SaleResult.Recorded)
        assertEquals(6, inventory.getProduct(1)!!.variants.single().stockQuantity)
    }

    @Test
    fun migrate2To3_putsFoodStockIntoBatches() = runTest {
        val expiryDay = TestData.today.plusDays(20).toEpochDay()
        createFromSchema(2).apply {
            execSQL(
                "INSERT INTO products (id, name, brand, category, description, material, diet_type, hsn_code, gst_rule, gst_rate_bp, created_at, updated_at) VALUES " +
                    "(1, 'Cotton T-Shirt', NULL, 'GARMENT', NULL, NULL, NULL, '6109', 'APPAREL', NULL, 100, 100), " +
                    "(2, 'Butter Biscuits', NULL, 'PACKAGED_FOOD', NULL, NULL, 'VEG', '1905', 'FIXED', 500, 100, 100), " +
                    "(3, 'Mango Juice', NULL, 'BEVERAGE', NULL, NULL, NULL, '2202', 'FIXED', 500, 100, 100)",
            )
            execSQL(
                "INSERT INTO product_variants (id, product_id, sku, barcode, size, colour, net_quantity_milli, net_quantity_unit, " +
                    "price_minor, mrp_minor, cost_minor, stock_quantity, low_stock_threshold, batch_number, expiry_epoch_day) VALUES " +
                    "(11, 1, NULL, '${TestData.EAN[0]}', 'M', 'Navy', NULL, NULL, 49900, NULL, 25000, 8, 2, NULL, NULL), " +
                    "(21, 2, NULL, '${TestData.EAN[2]}', NULL, NULL, 200000, 'GRAM', 3000, 3500, 2200, 30, 10, 'B9', $expiryDay), " +
                    "(31, 3, NULL, '${TestData.EAN[3]}', NULL, NULL, 1000000, 'LITRE', 9900, NULL, NULL, 0, 5, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO stock_movements (id, variant_id, quantity_change, reason, note, created_at, invoice_id) VALUES " +
                    "(1, 11, 8, 'INITIAL', NULL, 100, NULL), (2, 21, 30, 'INITIAL', NULL, 100, NULL)",
            )
            close()
        }

        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val inventory = RoomInventoryRepository(db, TestData.clock)
        val biscuits = inventory.getProduct(2)!!.variants.single()
        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)

        // Existing food stock became one batch with the variant's batch number and expiry.
        val batch = biscuits.batches.single()
        assertEquals(30, batch.quantity)
        assertEquals("B9", batch.batchNumber)
        assertEquals(TestData.today.plusDays(20), batch.expiryDate)
        assertEquals(Money(2_200), batch.unitCost)
        assertTrue(inventory.getProduct(1)!!.variants.single().batches.isEmpty()) // garments aren't batched
        assertTrue(inventory.getProduct(3)!!.variants.single().batches.isEmpty()) // nothing in stock
        assertEquals(0, biscuits.reorderQuantity)
        assertEquals(StockUnit.UNIT, inventory.getProduct(2)!!.stockUnit)

        // Selling from the migrated batch keeps stock and batches in step.
        val product = inventory.getProduct(2)!!
        val cart = Cart(lines = listOf(CartLine.from(product, biscuits, quantity = 5)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val sale = SaleBuilder.build(cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis())
        assertTrue(RoomBillingRepository(db).recordSale(sale, "INV") is SaleResult.Recorded)
        val after = inventory.getProduct(2)!!.variants.single()
        assertEquals(25, after.stockQuantity)
        assertEquals(25, after.batches.single().quantity)
        assertEquals(25, db.inventoryDao().movementTotal(21))
    }

    /**
     * A shop that has been trading on version 2: settings, a sale with one unit already
     * returned, its credit note, number counters and the stock movements behind them.
     */
    @Test
    fun migrate2To3_keepsBillingHistory_andNumbersContinue() = runTest {
        val soldAt = TestData.clock.millis() - 86_400_000
        createFromSchema(2).apply {
            execSQL(
                "INSERT INTO store_settings (id, store_name, address, phone, gstin, state_code, gst_registered, prices_include_tax, round_off, " +
                    "invoice_prefix, credit_note_prefix, upi_id, upi_payee_name, receipt_paper, receipt_footer, receipt_show_hsn, " +
                    "receipt_show_tax_breakup, receipt_show_savings, receipt_show_customer, receipt_show_barcode) VALUES " +
                    "(1, 'Sample Mart', '12 Park Street, Kolkata', '9830012345', '19AABCU9603R1ZK', '19', 1, 1, 1, 'INV', 'CN', " +
                    "'samplemart@okaxis', 'Sample Mart', 'THERMAL_58', 'Thank you', 1, 1, 1, 1, 0)",
            )
            execSQL(
                "INSERT INTO products (id, name, brand, category, description, material, diet_type, hsn_code, gst_rule, gst_rate_bp, created_at, updated_at) " +
                    "VALUES (1, 'Cotton T-Shirt', NULL, 'GARMENT', NULL, NULL, NULL, '6109', 'APPAREL', NULL, 100, 100)",
            )
            execSQL(
                "INSERT INTO product_variants (id, product_id, sku, barcode, size, colour, net_quantity_milli, net_quantity_unit, " +
                    "price_minor, mrp_minor, cost_minor, stock_quantity, low_stock_threshold, batch_number, expiry_epoch_day) VALUES " +
                    "(11, 1, NULL, '${TestData.EAN[0]}', 'M', 'Navy', NULL, NULL, 49900, NULL, 25000, 7, 2, NULL, NULL)",
            )
            // Sale INV/26-27/00001 of two shirts, one of them returned on credit note CN/26-27/00001.
            execSQL(
                "INSERT INTO invoices (id, number, type, created_at, seller_name, seller_address, seller_phone, seller_gstin, seller_state_code, " +
                    "buyer_name, buyer_phone, buyer_gstin, place_of_supply, gst_registered, prices_include_tax, inter_state, subtotal_minor, " +
                    "discount_minor, taxable_minor, cgst_minor, sgst_minor, igst_minor, round_off_minor, grand_total_minor, savings_minor, " +
                    "original_invoice_id, original_invoice_number, note) VALUES " +
                    "(1, 'INV/26-27/00001', 'SALE', $soldAt, 'Sample Mart', '12 Park Street, Kolkata', '9830012345', '19AABCU9603R1ZK', '19', " +
                    "NULL, NULL, NULL, '19', 1, 1, 0, 99800, 0, 95048, 2376, 2376, 0, 0, 99800, 0, NULL, NULL, NULL), " +
                    "(2, 'CN/26-27/00001', 'RETURN', $soldAt, 'Sample Mart', '12 Park Street, Kolkata', '9830012345', '19AABCU9603R1ZK', '19', " +
                    "NULL, NULL, NULL, '19', 1, 1, 0, 49900, 0, 47524, 1188, 1188, 0, 0, 49900, 0, 1, 'INV/26-27/00001', 'Wrong size')",
            )
            execSQL(
                "INSERT INTO invoice_lines (id, invoice_id, variant_id, product_name, variant_label, barcode, hsn_code, quantity, unit_price_minor, " +
                    "mrp_minor, discount_minor, taxable_minor, gst_rate_bp, cgst_minor, sgst_minor, igst_minor, total_minor, returned_quantity, original_line_id) VALUES " +
                    "(1, 1, 11, 'Cotton T-Shirt', 'M · Navy', '${TestData.EAN[0]}', '6109', 2, 49900, NULL, 0, 95048, 500, 2376, 2376, 0, 99800, 1, NULL), " +
                    "(2, 2, 11, 'Cotton T-Shirt', 'M · Navy', '${TestData.EAN[0]}', '6109', 1, 49900, NULL, 0, 47524, 500, 1188, 1188, 0, 49900, 0, 1)",
            )
            execSQL(
                "INSERT INTO payments (id, invoice_id, method, kind, amount_minor, reference, tendered_minor, change_minor, created_at) VALUES " +
                    "(1, 1, 'UPI', 'PAYMENT', 99800, 'UTR123', NULL, NULL, $soldAt), (2, 2, 'CASH', 'REFUND', 49900, NULL, NULL, NULL, $soldAt)",
            )
            execSQL(
                "INSERT INTO stock_movements (id, variant_id, quantity_change, reason, note, created_at, invoice_id) VALUES " +
                    "(1, 11, 8, 'INITIAL', NULL, 100, NULL), (2, 11, -2, 'SOLD', 'INV/26-27/00001', $soldAt, 1), (3, 11, 1, 'RETURNED', 'CN/26-27/00001', $soldAt, 2)",
            )
            execSQL("INSERT INTO number_sequences (name, next_value) VALUES ('INV|2026', 2), ('CN|2026', 2)")
            close()
        }

        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val billing = RoomBillingRepository(db)
        val inventory = RoomInventoryRepository(db, TestData.clock)

        // Everything written before the upgrade reads back as it was.
        val sale = billing.getInvoice(1)!!
        assertEquals("INV/26-27/00001", sale.number)
        assertEquals(Money(99_800), sale.grandTotal)
        assertEquals(1, sale.lines.single().returnedQuantity)
        assertEquals(PaymentMethod.UPI, sale.payments.single().method)
        assertEquals("UTR123", sale.payments.single().reference)
        assertNull(sale.customerId) // new column, nobody linked yet
        val creditNote = billing.getInvoice(2)!!
        assertEquals(1L, creditNote.originalInvoiceId)
        assertEquals(PaymentKind.REFUND, creditNote.payments.single().kind)
        assertEquals("Sample Mart", RoomSettingsRepository(db).getSettings().storeName)
        assertEquals(ReceiptPaper.THERMAL_58, RoomSettingsRepository(db).getSettings().receipt.paper)
        assertEquals("Thank you", RoomSettingsRepository(db).getSettings().receipt.footer)
        assertEquals(7, inventory.getProduct(1)!!.variants.single().stockQuantity)
        assertEquals(7, db.inventoryDao().movementTotal(11))

        // Nothing is left pointing at a missing row.
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }

        // The counters carry on from where they were, and stock stays in step with the movements.
        val product = inventory.getProduct(1)!!
        val cart = Cart(lines = listOf(CartLine.from(product, product.variants.single(), quantity = 1)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val next = SaleBuilder.build(cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis())
        assertEquals("INV/26-27/00002", (billing.recordSale(next, "INV") as SaleResult.Recorded).number)
        assertEquals(6, db.inventoryDao().movementTotal(11))
        assertEquals(6, inventory.getProduct(1)!!.variants.single().stockQuantity)

        // The one unit already returned still counts: only one of the two sold units can come back.
        assertTrue(ReturnBuilder.build(billing.getInvoice(1)!!, mapOf(1L to 2), PaymentMethod.CASH, "Test", TestData.clock.millis()) is ReturnBuild.Rejected)
        val one = ReturnBuilder.build(billing.getInvoice(1)!!, mapOf(1L to 1), PaymentMethod.CASH, "Test", TestData.clock.millis()) as ReturnBuild.Ready
        // Even a hand-made credit note for both units is refused by the database layer.
        val tooMany = one.creditNote.copy(lines = one.creditNote.lines.map { it.copy(quantity = 2) })
        assertEquals(ReturnResult.QuantityExceedsSold, billing.recordReturn(tooMany, "CN"))
        assertEquals(6, inventory.getProduct(1)!!.variants.single().stockQuantity)
        assertEquals("CN/26-27/00002", (billing.recordReturn(one.creditNote, "CN") as ReturnResult.Recorded).number)
        assertEquals(7, inventory.getProduct(1)!!.variants.single().stockQuantity)
        assertEquals(7, db.inventoryDao().movementTotal(11))
    }

    @Test
    fun migrate4To5_addsDayClosings_andKeepsTheRest() = runTest {
        createFromSchema(4).also {
            it.execSQL(
                "INSERT INTO audit_log (id, created_at, actor_id, actor_name, actor_role, action, entity_type, entity_id, summary) " +
                    "VALUES (1, 100, 1, 'Admin', 'SUPER_ADMIN', 'SIGNED_IN', 'user', 1, 'Signed in')",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db

        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)
        assertEquals("Signed in", db.auditDao().observeRecent(10).first().single().summary)
        val dayEnd = com.auxo.pos.data.repository.RoomDayEndRepository(db, TestData.clock)
        assertTrue(dayEnd.observeClosings().first().isEmpty())
        val result = dayEnd.closeDay(java.time.LocalDate.now(TestData.clock), Money(1_000), null, "Test Admin")
        assertTrue(result is com.auxo.pos.data.repository.CloseDayResult.Closed)
    }

    /**
     * A shop with a customer who owes money, opened from version 4. It goes 4 -> 5 (automatic), then 5 -> 6 and
     * 6 -> 7 (by hand): every balance stays as it was, nobody holds points, loyalty is off until the Super Admin
     * switches it on, and bills keep printing in English. (Version 5's own schema file is not needed: the chain
     * runs through it.)
     */
    @Test
    fun migrate4To15_keepsBalances_startsWithNoPoints_loyaltyOff_billsInEnglish_upiQrOn_noEmployeeDiscounts_heldBillsOpenOrdersQuotationsThirtyDayExpiryStockCountsAndProductPhotosWork() = runTest {
        createFromSchema(4).also {
            it.execSQL(
                "INSERT INTO customers (id, name, phone, email, gstin, state_code, address, credit_limit_minor, notes, created_at, updated_at) " +
                    "VALUES (1, 'Asha', '9876543210', NULL, NULL, NULL, NULL, NULL, NULL, 100, 100)",
            )
            it.execSQL(
                "INSERT INTO customer_ledger (id, customer_id, type, amount_minor, invoice_id, method, reference, note, created_at) VALUES " +
                    "(1, 1, 'OPENING_BALANCE', 50000, NULL, NULL, NULL, NULL, 100), (2, 1, 'PAYMENT', -20000, NULL, 'CASH', NULL, NULL, 200)",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db

        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)
        val customers = com.auxo.pos.data.repository.RoomCustomerRepository(db, TestData.clock)
        val account = customers.observeAccount(1).first()!!
        assertEquals(Money(30_000), account.balance)
        assertEquals(0, account.points)
        assertEquals(listOf(0, 0), customers.observeLedger(1).first().map { it.points })

        val loyalty = com.auxo.pos.data.repository.RoomLoyaltyRepository(db)
        assertEquals(com.auxo.pos.domain.loyalty.LoyaltyRules.OFF, loyalty.getRules())

        // The new column and table accept data.
        db.customerDao().insertEntry(
            com.auxo.pos.data.local.entity.LedgerEntryEntity(
                customerId = 1, type = "POINTS_EARNED", amountMinor = 0, invoiceId = null, method = null, reference = null, note = null,
                createdAt = 300, points = 12,
            ),
        )
        assertEquals(12, customers.observeAccount(1).first()!!.points)
        assertEquals(Money(30_000), customers.observeAccount(1).first()!!.balance)
        loyalty.saveRules(com.auxo.pos.domain.loyalty.LoyaltyRules(true, Money(5_000), Money(50)))
        assertEquals(com.auxo.pos.domain.loyalty.LoyaltyRules(true, Money(5_000), Money(50)), loyalty.getRules())

        // The bill language: English until the shop chooses, then saved.
        val settings = RoomSettingsRepository(db)
        assertEquals(com.auxo.pos.domain.model.Language.ENGLISH, settings.getSettings().receipt.language)
        settings.saveSettings(TestData.settings.let { it.copy(receipt = it.receipt.copy(language = com.auxo.pos.domain.model.Language.HINDI)) })
        assertEquals(com.auxo.pos.domain.model.Language.HINDI, settings.getSettings().receipt.language)

        // The receipt UPI QR: on for a shop that existed before, and the choice is saved.
        assertTrue(settings.getSettings().receipt.showUpiQr)
        settings.saveSettings(TestData.settings.let { it.copy(receipt = it.receipt.copy(showUpiQr = false)) })
        assertFalse(settings.getSettings().receipt.showUpiQr)
        assertEquals(TestData.settings.upiId, settings.getSettings().receipt.upiId)

        // Employees cannot discount until the Super Admin allows it, and the limit is saved.
        assertEquals(0, settings.getSettings().employeeDiscountLimitBp)
        settings.saveSettings(TestData.settings.copy(employeeDiscountLimitBp = 1_000))
        assertEquals(1_000, settings.getSettings().employeeDiscountLimitBp)

        // Expiry warning: shops that existed before keep the 30 days they always had; the choice is saved.
        assertEquals(30, settings.getSettings().expiryWarningDays)
        settings.saveSettings(TestData.settings.copy(expiryWarningDays = 7))
        assertEquals(7, settings.getSettings().expiryWarningDays)
        settings.saveSettings(TestData.settings.copy(employeeDiscountLimitBp = 1_000))

        // The reason columns exist on bills and their lines.
        for (table in listOf("invoices", "invoice_lines")) {
            val columns = db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { c ->
                generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
            }
            assertTrue("$table has discount_reason", "discount_reason" in columns)
        }

        // Parked bills: the new table starts empty and holds a bill without touching anything else.
        val held = com.auxo.pos.data.repository.RoomHeldBillRepository(db, TestData.clock)
        assertTrue(held.observeHeld().first().isEmpty())
        val cart = com.auxo.pos.domain.billing.Cart(
            lines = listOf(
                com.auxo.pos.domain.billing.CartLine.custom(
                    com.auxo.pos.domain.billing.CartLine.CUSTOM_KEY_BASE,
                    com.auxo.pos.domain.billing.CustomItemDraft("Alteration", Money(100_00), 1, 0),
                ),
            ),
        )
        val id = held.hold(cart, Money(100_00), "Asha")
        assertEquals("Asha", held.observeHeld().first().single().label)
        assertEquals("Alteration", held.get(id)!!.cart.lines.single().productName)
        assertEquals(Money(30_000), customers.observeAccount(1).first()!!.balance)

        // Quotations: the table exists and starts empty; the first quotation is QT/..../00001 and moves no stock.
        assertEquals(0, com.auxo.pos.testutil.DataIntegrity.count(db, "quotations"))
        val quotes = com.auxo.pos.data.repository.RoomQuotationRepository(db, TestData.clock)
        val quote = quotes.save(cart, Money(100_00), java.time.LocalDate.of(2026, 10, 25))
        assertEquals("QT/26-27/00001", quote.number)
        assertEquals(quote.number, quotes.get(quote.id)!!.summary.number)
        assertEquals(0, com.auxo.pos.testutil.DataIntegrity.count(db, "invoices"))
        for (table in listOf("held_bills", "invoices")) {
            val columns = db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { c ->
                generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
            }
            // Version 23 gives parked bills a table_number again, with a new meaning (the restaurant table they belong to).
            assertTrue("$table no longer has service_type", "service_type" !in columns)
            if (table == "invoices") assertTrue("invoices no longer has table_number", "table_number" !in columns)
        }

        // Stock counts: the table exists and starts empty; a count can be started, filled and cancelled with no
        // effect on stock.
        assertEquals(0, com.auxo.pos.testutil.DataIntegrity.count(db, "stock_counts"))
        val stockCounts = com.auxo.pos.data.repository.RoomStockCountRepository(db, TestData.clock)
        val openCount = stockCounts.start()
        assertTrue(stockCounts.setLine(openCount.id, com.auxo.pos.domain.inventory.CountLine(1, 5, 3)))
        assertEquals(listOf(com.auxo.pos.domain.inventory.CountLine(1, 5, 3)), stockCounts.observeOpen().first()!!.lines)
        stockCounts.cancel(openCount.id)
        assertNull(stockCounts.observeOpen().first())

        // Product photos: the column exists, no existing product has a photo, and a photo name is saved and read back.
        val productColumns = db.openHelper.readableDatabase.query("PRAGMA table_info(products)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
        }
        assertTrue("products has photo_file", "photo_file" in productColumns)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM products WHERE photo_file IS NOT NULL").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        val photoName = "0123456789abcdef0123456789abcdef.jpg"
        val inventory = com.auxo.pos.data.repository.RoomInventoryRepository(db, TestData.clock)
        val saved = inventory.saveProduct(
            com.auxo.pos.domain.model.Product(
                name = "Photo migration test",
                category = com.auxo.pos.domain.model.ProductCategory.GARMENT,
                variants = listOf(TestData.variant(barcode = "PHOTO-MIGRATION-1", size = "M", colour = "Red")),
                photoFile = photoName,
            ),
        ) as com.auxo.pos.data.repository.SaveProductResult.Saved
        assertEquals(photoName, inventory.getProduct(saved.productId)!!.photoFile)
    }

    @Test
    fun migrate15To16_movesDishesIntoProducts_relinksHistoryAndParkedBills_andDropsTheMenuTables() = runTest {
        createFromSchema(15).also {
            it.execSQL(
                "INSERT INTO menu_items (id, name, section, description, price_minor, gst_rate_bp, sac_code, diet_type, available, deduct_ingredients, sort_order, created_at, updated_at) VALUES " +
                    "(3, 'Masala Dosa', 'Breakfast', 'Crisp', 12000, 500, '996331', 'VEG', 1, 1, 0, 100, 200), " +
                    "(4, 'Idli', ' ', NULL, 6000, 500, NULL, NULL, 0, 0, 1, 100, 200)",
            )
            it.execSQL("INSERT INTO recipe_lines (id, menu_item_id, variant_id, quantity) VALUES (1, 3, 1, 50)")
            it.execSQL(
                "INSERT INTO invoices (id, number, type, created_at, seller_name, seller_address, seller_phone, seller_gstin, seller_state_code, place_of_supply, " +
                    "gst_registered, prices_include_tax, inter_state, subtotal_minor, discount_minor, taxable_minor, cgst_minor, sgst_minor, igst_minor, round_off_minor, grand_total_minor, savings_minor) " +
                    "VALUES (1, 'INV-1', 'SALE', 300, 'Shop', 'Addr', '9', '', '19', '19', 0, 1, 0, 12000, 0, 11429, 286, 285, 0, 0, 12000, 0)",
            )
            it.execSQL(
                "INSERT INTO invoice_lines (id, invoice_id, variant_id, product_name, variant_label, barcode, hsn_code, quantity, unit_price_minor, mrp_minor, discount_minor, taxable_minor, " +
                    "gst_rate_bp, cgst_minor, sgst_minor, igst_minor, total_minor, returned_quantity, original_line_id, menu_item_id, discount_reason) " +
                    "VALUES (1, 1, NULL, 'Masala Dosa', '', '', '996331', 1, 12000, NULL, 0, 11429, 500, 286, 285, 0, 12000, 0, NULL, 3, NULL)",
            )
            val cart = """{"v":1,"lines":[
                {"key":-3,"productId":0,"name":"Masala Dosa","variant":"","barcode":"","price":12000,"gst":"F500","qty":2,"discountReason":"","custom":false,"menuItemId":3},
                {"key":-99,"productId":0,"name":"Gone","variant":"","barcode":"","price":1000,"gst":"F500","qty":1,"discountReason":"","custom":false,"menuItemId":99}
                ],"billDiscountReason":"","customer":{"name":"","phone":"","gstin":""},"table":"T4"}""".replace("\n", "").replace("  ", "")
            val kot = """[{"key":-3,"name":"Masala Dosa","variant":"","qty":2}]"""
            it.execSQL(
                "INSERT INTO held_bills (id, label, created_at, updated_at, item_count, total_minor, payload, service_type, table_number, kot_sent, kot_count) " +
                    "VALUES (1, 'T4', 100, 100, 3, 25000, ?, 'DINE_IN', 'T4', ?, 1)",
                arrayOf<Any?>(cart, kot),
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)

        // The tables are gone and the dishes are products.
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
        }
        assertFalse("menu_items" in tables)
        assertFalse("recipe_lines" in tables)
        val inventory = com.auxo.pos.data.repository.RoomInventoryRepository(db, TestData.clock)
        val dishes = inventory.observeProducts().first().filter { it.category == com.auxo.pos.domain.model.ProductCategory.RESTAURANT }
        assertEquals(setOf("Masala Dosa", "Idli"), dishes.map { it.name }.toSet())
        val dosa = dishes.first { it.name == "Masala Dosa" }
        assertEquals("Breakfast", dosa.subcategory)
        assertEquals(com.auxo.pos.domain.model.GstRate.Fixed(500), dosa.gstRate)
        assertEquals("996331", dosa.hsnCode)
        assertFalse(dosa.tracksStock)
        assertTrue(dosa.available)
        assertEquals(com.auxo.pos.domain.model.Money(12_000), dosa.variants.single().sellingPrice)
        assertEquals("DISH-3", dosa.variants.single().barcode)
        val idli = dishes.first { it.name == "Idli" }
        assertNull(idli.subcategory)
        assertFalse(idli.available)

        // History keeps its own copy of the line but now points at the product.
        db.openHelper.readableDatabase.query("SELECT variant_id, product_name, unit_price_minor FROM invoice_lines WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(dosa.variants.single().id, c.getLong(0))
            assertEquals("Masala Dosa", c.getString(1))
            assertEquals(12_000L, c.getLong(2))
        }

        // The parked bill opens: the dish is now an ordinary line, and a dish that no longer existed stays as a one-off item.
        val held = db.openHelper.readableDatabase.query("SELECT payload FROM held_bills WHERE id = 1").use { c ->
            c.moveToFirst()
            c.getString(0)
        }
        val cart = com.auxo.pos.data.repository.HeldCartCodec.decode(held)!!
        assertEquals(dosa.variants.single().id, cart.lines[0].variantId)
        assertEquals(dosa.id, cart.lines[0].productId)
        assertEquals("DISH-3", cart.lines[0].barcode)
        assertTrue(cart.lines[1].isCustom)
    }

    @Test
    fun dishMigration_repointsOnlyDishLines_andLeavesOtherTextAlone() {
        val moved = mapOf(3L to DishMigration.Placement(productId = 70, variantId = 71))
        val tickets = """[{"key":-3,"name":"Dosa","variant":"","qty":1},{"key":5,"name":"Shirt","variant":"M","qty":1},{"key":${-(1L shl 40) - 2},"name":"Gift wrap","variant":"","qty":1}]"""
        val rewritten = JSONArray(DishMigration.repointTickets(tickets, moved))
        assertEquals(71L, rewritten.getJSONObject(0).getLong("key"))
        assertEquals(5L, rewritten.getJSONObject(1).getLong("key"))
        assertEquals(-(1L shl 40) - 2, rewritten.getJSONObject(2).getLong("key"))
        assertNull(DishMigration.repointTickets("""[{"key":5,"name":"Shirt","variant":"","qty":1}]""", moved))
        assertNull(DishMigration.repointTickets("not json", moved))
        assertNull(DishMigration.repointCart("""{"lines":[{"key":5}]}""", moved))
        assertNull(DishMigration.repointCart("not json", moved))
    }

    @Test
    fun migrate16To17_makesCategoryRows_linksProducts_andTurnsRestaurantOnOnlyWhenThereAreDishes() = runTest {
        fun product(db: SQLiteDatabase, id: Int, category: String, subcategory: String?) = db.execSQL(
            "INSERT INTO products (id, name, brand, category, description, material, diet_type, hsn_code, gst_rule, gst_rate_bp, created_at, updated_at, stock_unit, " +
                "preferred_supplier_id, photo_file, subcategory, available, tracks_stock) " +
                "VALUES ($id, 'P$id', NULL, '$category', NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, NULL, NULL, ${subcategory?.let { "'$it'" } ?: "NULL"}, 1, 1)",
        )
        createFromSchema(16).also {
            product(it, 1, "GARMENT", null)
            product(it, 2, "RESTAURANT", "Starters")
            product(it, 3, "RESTAURANT", "Starters")
            product(it, 4, "RESTAURANT", " ")
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)

        val categories = com.auxo.pos.data.repository.RoomCategoryRepository(db).getCategories()
        // The Ingredient category that version 17 added is removed again by version 19; version 20 adds Footwear and
        // Accessories (on), version 22 adds General item, Electronics, Pharmacy and Cosmetics (off) and version 23
        // adds Loose goods and Jewellery (off).
        assertEquals(12, categories.size)
        assertTrue(categories.all { it.builtIn })
        val newer = setOf(
            com.auxo.pos.domain.model.ProductCategory.GENERAL, com.auxo.pos.domain.model.ProductCategory.ELECTRONICS,
            com.auxo.pos.domain.model.ProductCategory.PHARMACY, com.auxo.pos.domain.model.ProductCategory.COSMETICS,
            com.auxo.pos.domain.model.ProductCategory.LOOSE, com.auxo.pos.domain.model.ProductCategory.JEWELLERY,
        )
        assertTrue(categories.filter { it.profile !in newer }.all { it.enabled })
        assertTrue(categories.filter { it.profile in newer }.none { it.enabled })

        val inventory = RoomInventoryRepository(db, TestData.clock)
        val garment = categories.first { it.profile == com.auxo.pos.domain.model.ProductCategory.GARMENT }
        val restaurant = categories.first { it.profile == com.auxo.pos.domain.model.ProductCategory.RESTAURANT }
        assertEquals(garment.id, inventory.getProduct(1)!!.categoryId)
        assertEquals(restaurant.id, inventory.getProduct(2)!!.categoryId)
        assertEquals(listOf("Starters"), restaurant.subcategories.map { it.name })
    }

    @Test
    fun migrate16To17_leavesRestaurantOff_whenTheShopHasNoDishes() = runTest {
        createFromSchema(16).close()
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val categories = com.auxo.pos.data.repository.RoomCategoryRepository(db).getCategories()
        assertEquals(
            listOf(true, true, true, true, true, false, false, false, false, false, false, false),
            categories.map { it.enabled },
        )
    }

    @Test
    fun migrate17To18_addsEmptyFssaiNumbers_andKeepsTheShopsSettings() = runTest {
        createFromSchema(17).also {
            it.execSQL(
                "INSERT INTO store_settings (id, store_name, address, phone, gstin, state_code, gst_registered, prices_include_tax, round_off, " +
                    "invoice_prefix, credit_note_prefix, upi_id, upi_payee_name, receipt_paper, receipt_footer, receipt_show_hsn, " +
                    "receipt_show_tax_breakup, receipt_show_savings, receipt_show_customer, receipt_show_barcode, receipt_language, " +
                    "receipt_show_upi_qr, employee_discount_limit_bp, expiry_warning_days) " +
                    "VALUES (1, 'Old Shop', 'Addr', '9831000000', '', '19', 1, 1, 1, 'INV', 'CN', '', '', 'THERMAL_80', '', 1, 1, 1, 1, 0, NULL, 1, 0, 30)",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)
        val settings = RoomSettingsRepository(db).getSettings()
        assertEquals("Old Shop", settings.storeName)
        assertEquals("", settings.fssai)
        val saved = settings.copy(fssai = "12345678901234")
        RoomSettingsRepository(db).saveSettings(saved)
        assertEquals("12345678901234", RoomSettingsRepository(db).getSettings().fssai)
    }

    @Test
    fun migrate18To19_dropsKitchenColumns_keepsParkedBills_andRemovesIngredients() = runTest {
        createFromSchema(18).also {
            it.execSQL("INSERT INTO products (id, name, category, created_at, updated_at) VALUES (1, 'Wheat Flour', 'INGREDIENT', 1, 1), (2, 'Cotton Shirt', 'GARMENT', 1, 1)")
            it.execSQL(
                "INSERT INTO product_variants (id, product_id, barcode, price_minor, stock_quantity, low_stock_threshold) VALUES " +
                    "(11, 1, 'FLOUR-1', 0, 5000, 0), (12, 2, 'SHIRT-1', 49900, 3, 0)",
            )
            it.execSQL("INSERT INTO stock_movements (variant_id, quantity_change, reason, created_at) VALUES (11, 5000, 'RECEIVED', 1), (12, 3, 'INITIAL', 1)")
            it.execSQL("INSERT INTO stock_batches (variant_id, quantity, received_at) VALUES (11, 5000, 1)")
            it.execSQL("INSERT INTO shop_categories (id, profile, enabled, sort_order, built_in) VALUES (1, 'GARMENT', 1, 0, 1), (2, 'INGREDIENT', 1, 4, 1)")
            it.execSQL("INSERT INTO number_sequences (name, next_value) VALUES ('KOT|2026-10-01', 3), ('INV/26-27', 8)")
            it.execSQL(
                "INSERT INTO invoices (id, number, type, created_at, seller_name, seller_address, seller_phone, seller_gstin, seller_state_code, seller_fssai, place_of_supply, " +
                    "gst_registered, prices_include_tax, inter_state, subtotal_minor, discount_minor, taxable_minor, cgst_minor, sgst_minor, igst_minor, round_off_minor, " +
                    "grand_total_minor, savings_minor, service_type, table_number) " +
                    "VALUES (1, 'INV-1', 'SALE', 300, 'Shop', 'Addr', '9', '', '19', '', '19', 0, 1, 0, 100, 0, 100, 0, 0, 0, 0, 100, 0, 'DINE_IN', 'T4')",
            )
            it.execSQL(
                "INSERT INTO held_bills (id, label, created_at, updated_at, item_count, total_minor, payload, service_type, table_number, kot_sent, kot_count) " +
                    "VALUES (1, 'T4', 100, 100, 2, 25000, 'payload', 'DINE_IN', 'T4', '[]', 1)",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)

        fun columns(table: String) = db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
        }
        fun count(sql: String) = db.openHelper.readableDatabase.query(sql).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        for (table in listOf("held_bills", "invoices")) {
            val names = columns(table)
            assertTrue("$table has no service_type", "service_type" !in names)
            if (table == "invoices") assertTrue("invoices has no table_number", "table_number" !in names)
        }
        assertTrue("kot_sent" !in columns("held_bills") && "kot_count" !in columns("held_bills"))

        // The parked bill and the invoice are kept, and the kitchen ticket numbers are gone.
        assertEquals("T4", db.openHelper.readableDatabase.query("SELECT label FROM held_bills WHERE id = 1").use { c -> c.moveToFirst(); c.getString(0) })
        assertEquals(1, count("SELECT COUNT(*) FROM invoices"))
        assertEquals(0, count("SELECT COUNT(*) FROM number_sequences WHERE name LIKE 'KOT|%'"))
        assertEquals(1, count("SELECT COUNT(*) FROM number_sequences"))

        // The ingredient, its variant, batches and movements are gone; the shirt is untouched.
        assertEquals(0, count("SELECT COUNT(*) FROM products WHERE category = 'INGREDIENT'"))
        assertEquals(0, count("SELECT COUNT(*) FROM product_variants WHERE id = 11"))
        assertEquals(0, count("SELECT COUNT(*) FROM stock_batches"))
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movements"))
        assertEquals(1, count("SELECT COUNT(*) FROM product_variants WHERE id = 12"))
        // Garment and the two that version 20 adds, the four that version 22 adds and the two that version 23 adds.
        assertEquals(9, count("SELECT COUNT(*) FROM shop_categories"))
        assertEquals(listOf("Cotton Shirt"), RoomInventoryRepository(db, TestData.clock).observeProducts().first().map { it.name })
    }

    @Test
    fun migrate19To20_addsFootwearAndAccessories_rightAfterGarment_keepingTheShopsChoices() = runTest {
        createFromSchema(19).also {
            it.execSQL(
                "INSERT INTO shop_categories (id, profile, enabled, sort_order, built_in) VALUES " +
                    "(1, 'GARMENT', 1, 0, 1), (2, 'PACKAGED_FOOD', 0, 1, 1), (3, 'BEVERAGE', 1, 2, 1), (4, 'RESTAURANT', 1, 3, 1)",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        assertEquals(AuxoDatabase.VERSION, db.openHelper.readableDatabase.version)

        val categories = com.auxo.pos.data.repository.RoomCategoryRepository(db).getCategories().sortedBy { it.sortOrder }
        assertEquals(
            listOf(
                ProductCategory.GARMENT, ProductCategory.FOOTWEAR, ProductCategory.ACCESSORIES, ProductCategory.PACKAGED_FOOD, ProductCategory.BEVERAGE, ProductCategory.RESTAURANT,
                ProductCategory.GENERAL, ProductCategory.ELECTRONICS, ProductCategory.PHARMACY, ProductCategory.COSMETICS,
                ProductCategory.LOOSE, ProductCategory.JEWELLERY,
            ),
            categories.map { it.profile },
        )
        assertEquals(listOf(true, true, true, false, true, true, false, false, false, false, false, false), categories.map { it.enabled })
        assertTrue(categories.all { it.builtIn })
    }

    @Test
    fun migrate19To20_withoutTheBuiltInGarment_putsFootwearAndAccessoriesAtTheEnd() = runTest {
        createFromSchema(19).also {
            it.execSQL(
                "INSERT INTO shop_categories (id, profile, enabled, sort_order, built_in) VALUES " +
                    "(2, 'PACKAGED_FOOD', 1, 0, 1), (3, 'BEVERAGE', 1, 1, 1)",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val categories = com.auxo.pos.data.repository.RoomCategoryRepository(db).getCategories().sortedBy { it.sortOrder }
        assertEquals(
            listOf(
                ProductCategory.PACKAGED_FOOD, ProductCategory.BEVERAGE, ProductCategory.FOOTWEAR, ProductCategory.ACCESSORIES,
                ProductCategory.GENERAL, ProductCategory.ELECTRONICS, ProductCategory.PHARMACY, ProductCategory.COSMETICS,
                ProductCategory.LOOSE, ProductCategory.JEWELLERY,
            ),
            categories.map { it.profile },
        )
    }

    @Test
    fun migrate20To21_marksOldBillsRoundedOnlyWhenTheirTotalIsWholeRupees() = runTest {
        createFromSchema(20).also {
            fun bill(id: Long, totalMinor: Long) = it.execSQL(
                "INSERT INTO invoices (id, number, type, created_at, seller_name, seller_address, seller_phone, seller_gstin, " +
                    "seller_state_code, seller_fssai, place_of_supply, gst_registered, prices_include_tax, inter_state, subtotal_minor, " +
                    "discount_minor, taxable_minor, cgst_minor, sgst_minor, igst_minor, round_off_minor, grand_total_minor, savings_minor) " +
                    "VALUES ($id, 'INV/$id', 'SALE', 0, 'S', '', '', '', '19', '', '19', 1, 1, 0, $totalMinor, 0, $totalMinor, 0, 0, 0, 0, $totalMinor, 0)",
            )
            bill(1, 100_00)
            bill(2, 99_99)
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val rows = db.openHelper.readableDatabase.query("SELECT id, rounded_to_rupee FROM invoices ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getInt(1)) }
        }
        assertEquals(listOf(1L to 1, 2L to 0), rows)
    }

    @Test
    fun migrate21To22_addsFourCategoriesOff_andTheTwoProductColumns() = runTest {
        createFromSchema(21).also {
            it.execSQL("INSERT INTO shop_categories (id, name, profile, enabled, sort_order, built_in) VALUES (1, NULL, 'GARMENT', 1, 0, 1)")
            it.execSQL("INSERT INTO shop_categories (id, name, profile, enabled, sort_order, built_in) VALUES (2, NULL, 'RESTAURANT', 0, 1, 1)")
            it.execSQL(
                "INSERT INTO products (id, name, brand, category, description, material, diet_type, hsn_code, gst_rule, gst_rate_bp, created_at, updated_at, stock_unit, " +
                    "preferred_supplier_id, photo_file, subcategory, available, tracks_stock, category_id) " +
                    "VALUES (1, 'Shirt', NULL, 'GARMENT', NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, 1, 1, 1)",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val sql = db.openHelper.readableDatabase
        val rows = sql.query("SELECT profile, enabled, sort_order FROM shop_categories ORDER BY sort_order, id").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getInt(1), c.getInt(2))) }
        }
        assertEquals(
            listOf(
                Triple("GARMENT", 1, 0), Triple("RESTAURANT", 0, 1),
                Triple("GENERAL", 0, 2), Triple("ELECTRONICS", 0, 3), Triple("PHARMACY", 0, 4), Triple("COSMETICS", 0, 5),
                Triple("LOOSE", 0, 6), Triple("JEWELLERY", 0, 7),
            ),
            rows,
        )
        val product = sql.query("SELECT warranty_months, prescription_required FROM products WHERE id = 1").use { c ->
            c.moveToFirst()
            c.isNull(0) to c.getInt(1)
        }
        assertEquals(true to 0, product)
    }

    @Test
    fun migrate21To22_leavesAShopWithNoCategoryRowsToBeSeededLater() = runTest {
        createFromSchema(21).close()
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM shop_categories").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        assertEquals(0, count)
    }

    @Test
    fun migrate22To23_addsTwoCategoriesOff_andTheThreeColumns() = runTest {
        createFromSchema(22).also {
            it.execSQL("INSERT INTO shop_categories (id, name, profile, enabled, sort_order, built_in) VALUES (1, NULL, 'GARMENT', 1, 0, 1)")
            it.execSQL(
                "INSERT INTO products (id, name, brand, category, description, material, diet_type, hsn_code, gst_rule, gst_rate_bp, created_at, updated_at, stock_unit, " +
                    "preferred_supplier_id, photo_file, subcategory, available, tracks_stock, category_id) " +
                    "VALUES (1, 'Shirt', NULL, 'GARMENT', NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, 1, 1, 1)",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val sql = db.openHelper.readableDatabase
        val rows = sql.query("SELECT profile, enabled, sort_order FROM shop_categories ORDER BY sort_order, id").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getInt(1), c.getInt(2))) }
        }
        assertEquals(listOf(Triple("GARMENT", 1, 0), Triple("LOOSE", 0, 1), Triple("JEWELLERY", 0, 2)), rows)
        val product = sql.query("SELECT metal, making_charge_bp FROM products WHERE id = 1").use { c ->
            c.moveToFirst()
            c.isNull(0) to c.isNull(1)
        }
        assertEquals(true to true, product)
        // Parked bills get the two columns that tie them to a table or a number.
        val heldColumns = sql.query("PRAGMA table_info(held_bills)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
        }
        assertTrue("table_number" in heldColumns && "token_number" in heldColumns)
        // Bill lines already saved count as pieces: they have no unit.
        sql.query("SELECT stock_unit FROM invoice_lines").use { assertEquals(0, it.count) }
    }

    @Test
    fun migrate22To23_leavesAShopWithNoCategoryRowsToBeSeededLater() = runTest {
        createFromSchema(22).close()
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM shop_categories").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        assertEquals(0, count)
    }

    @Test
    fun migrate23To24_addsAColourPhotoToVariants_andLeavesExistingOnesWithNone() = runTest {
        createFromSchema(23).also {
            it.execSQL(
                "INSERT INTO products (id, name, brand, category, description, material, diet_type, hsn_code, gst_rule, gst_rate_bp, created_at, updated_at, stock_unit, " +
                    "preferred_supplier_id, photo_file, subcategory, available, tracks_stock, category_id) " +
                    "VALUES (1, 'Shirt', NULL, 'GARMENT', NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, NULL, 'shirt.jpg', NULL, 1, 1, NULL)",
            )
            it.execSQL(
                "INSERT INTO product_variants (id, product_id, sku, barcode, size, colour, net_quantity_milli, net_quantity_unit, price_minor, mrp_minor, cost_minor, " +
                    "stock_quantity, low_stock_threshold, batch_number, expiry_epoch_day, reorder_quantity) " +
                    "VALUES (1, 1, NULL, '${TestData.EAN[0]}', 'M', 'Navy', NULL, NULL, 49900, NULL, NULL, 5, 1, NULL, NULL, 0)",
            )
            it.close()
        }
        val db = AuxoDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val sql = db.openHelper.readableDatabase
        assertEquals(AuxoDatabase.VERSION, sql.version)
        sql.query("SELECT photo_file FROM product_variants WHERE id = 1").use { c ->
            c.moveToFirst()
            assertTrue(c.isNull(0))
        }
        // The product keeps its own photo.
        sql.query("SELECT photo_file FROM products WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("shirt.jpg", c.getString(0))
        }
    }
}
