package com.invision.pos.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.ReturnResult
import com.invision.pos.data.repository.RoomBillingRepository
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.RoomSettingsRepository
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.GstRate
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockUnit
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
    private var database: InvisionDatabase? = null

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
        val schema = JSONObject(File("schemas/com.invision.pos.data.local.InvisionDatabase/$version.json").readText()).getJSONObject("database")
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
                "VALUES (1, 'Cotton T-Shirt', 'Roy Wear', 'GARMENT', NULL, 'Cotton', NULL, 100, 100)",
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
        val db = InvisionDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val inventory = RoomInventoryRepository(db, TestData.clock)

        // Opening runs the migrations and Room validates every table against the current version.
        val product = inventory.getProduct(1)!!
        assertEquals(InvisionDatabase.VERSION, db.openHelper.readableDatabase.version)
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

        val db = InvisionDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db
        val inventory = RoomInventoryRepository(db, TestData.clock)
        val biscuits = inventory.getProduct(2)!!.variants.single()
        assertEquals(InvisionDatabase.VERSION, db.openHelper.readableDatabase.version)

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
                    "(1, 'Roy Mart', '12 Park Street, Kolkata', '9830012345', '19AABCU9603R1ZK', '19', 1, 1, 1, 'INV', 'CN', " +
                    "'roymart@okaxis', 'Roy Mart', 'THERMAL_58', 'Thank you', 1, 1, 1, 1, 0)",
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
                    "(1, 'INV/26-27/00001', 'SALE', $soldAt, 'Roy Mart', '12 Park Street, Kolkata', '9830012345', '19AABCU9603R1ZK', '19', " +
                    "NULL, NULL, NULL, '19', 1, 1, 0, 99800, 0, 95048, 2376, 2376, 0, 0, 99800, 0, NULL, NULL, NULL), " +
                    "(2, 'CN/26-27/00001', 'RETURN', $soldAt, 'Roy Mart', '12 Park Street, Kolkata', '9830012345', '19AABCU9603R1ZK', '19', " +
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

        val db = InvisionDatabase.builder(context, name).allowMainThreadQueries().build()
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
        assertEquals("Roy Mart", RoomSettingsRepository(db).getSettings().storeName)
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
        val db = InvisionDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db

        assertEquals(InvisionDatabase.VERSION, db.openHelper.readableDatabase.version)
        assertEquals("Signed in", db.auditDao().observeRecent(10).first().single().summary)
        val dayEnd = com.invision.pos.data.repository.RoomDayEndRepository(db, TestData.clock)
        assertTrue(dayEnd.observeClosings().first().isEmpty())
        val result = dayEnd.closeDay(java.time.LocalDate.now(TestData.clock), Money(1_000), null, "Test Admin")
        assertTrue(result is com.invision.pos.data.repository.CloseDayResult.Closed)
    }

    /**
     * A shop with a customer who owes money, opened from version 4. It goes 4 -> 5 (automatic), then 5 -> 6 and
     * 6 -> 7 (by hand): every balance stays as it was, nobody holds points, loyalty is off until the Super Admin
     * switches it on, and bills keep printing in English. (Version 5's own schema file is not needed: the chain
     * runs through it.)
     */
    @Test
    fun migrate4To11_keepsBalances_startsWithNoPoints_loyaltyOff_billsInEnglish_upiQrOn_noEmployeeDiscounts_heldBillsAndOpenOrdersWork() = runTest {
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
        val db = InvisionDatabase.builder(context, name).allowMainThreadQueries().build()
        database = db

        assertEquals(InvisionDatabase.VERSION, db.openHelper.readableDatabase.version)
        val customers = com.invision.pos.data.repository.RoomCustomerRepository(db, TestData.clock)
        val account = customers.observeAccount(1).first()!!
        assertEquals(Money(30_000), account.balance)
        assertEquals(0, account.points)
        assertEquals(listOf(0, 0), customers.observeLedger(1).first().map { it.points })

        val loyalty = com.invision.pos.data.repository.RoomLoyaltyRepository(db)
        assertEquals(com.invision.pos.domain.loyalty.LoyaltyRules.OFF, loyalty.getRules())

        // The new column and table accept data.
        db.customerDao().insertEntry(
            com.invision.pos.data.local.entity.LedgerEntryEntity(
                customerId = 1, type = "POINTS_EARNED", amountMinor = 0, invoiceId = null, method = null, reference = null, note = null,
                createdAt = 300, points = 12,
            ),
        )
        assertEquals(12, customers.observeAccount(1).first()!!.points)
        assertEquals(Money(30_000), customers.observeAccount(1).first()!!.balance)
        loyalty.saveRules(com.invision.pos.domain.loyalty.LoyaltyRules(true, Money(5_000), Money(50)))
        assertEquals(com.invision.pos.domain.loyalty.LoyaltyRules(true, Money(5_000), Money(50)), loyalty.getRules())

        // The bill language: English until the shop chooses, then saved.
        val settings = RoomSettingsRepository(db)
        assertEquals(com.invision.pos.domain.model.Language.ENGLISH, settings.getSettings().receipt.language)
        settings.saveSettings(TestData.settings.let { it.copy(receipt = it.receipt.copy(language = com.invision.pos.domain.model.Language.HINDI)) })
        assertEquals(com.invision.pos.domain.model.Language.HINDI, settings.getSettings().receipt.language)

        // The receipt UPI QR: on for a shop that existed before, and the choice is saved.
        assertTrue(settings.getSettings().receipt.showUpiQr)
        settings.saveSettings(TestData.settings.let { it.copy(receipt = it.receipt.copy(showUpiQr = false)) })
        assertFalse(settings.getSettings().receipt.showUpiQr)
        assertEquals(TestData.settings.upiId, settings.getSettings().receipt.upiId)

        // Employees cannot discount until the Super Admin allows it, and the limit is saved.
        assertEquals(0, settings.getSettings().employeeDiscountLimitBp)
        settings.saveSettings(TestData.settings.copy(employeeDiscountLimitBp = 1_000))
        assertEquals(1_000, settings.getSettings().employeeDiscountLimitBp)

        // The reason columns exist on bills and their lines.
        for (table in listOf("invoices", "invoice_lines")) {
            val columns = db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { c ->
                generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
            }
            assertTrue("$table has discount_reason", "discount_reason" in columns)
        }

        // Parked bills: the new table starts empty and holds a bill without touching anything else.
        val held = com.invision.pos.data.repository.RoomHeldBillRepository(db, TestData.clock)
        assertTrue(held.observeHeld().first().isEmpty())
        val cart = com.invision.pos.domain.billing.Cart(
            lines = listOf(
                com.invision.pos.domain.billing.CartLine.custom(
                    com.invision.pos.domain.billing.CartLine.CUSTOM_KEY_BASE,
                    com.invision.pos.domain.billing.CustomItemDraft("Alteration", Money(100_00), 1, 0),
                ),
            ),
        )
        val id = held.hold(cart, Money(100_00), "Asha")
        assertEquals("Asha", held.observeHeld().first().single().label)
        assertEquals("Alteration", held.get(id)!!.cart.lines.single().productName)
        assertEquals(Money(30_000), customers.observeAccount(1).first()!!.balance)

        // Open restaurant orders: a parked bill becomes an open table, and bills record the table.
        val order = held.placeOrder(
            cart.copy(serviceType = com.invision.pos.domain.billing.ServiceType.DINE_IN, tableNumber = "t2"),
            Money(100_00),
            "",
        ) as com.invision.pos.domain.billing.PlaceOrderResult.Placed
        assertEquals("KOT-001", order.kotNumber)
        assertEquals("T2", held.observeHeld().first().first { it.id == order.orderId }.tableNumber)
        // The bill parked before is still just a parked bill.
        assertNull(held.observeHeld().first().first { it.id == id }.serviceType)
        for (table in listOf("held_bills", "invoices")) {
            val columns = db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { c ->
                generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
            }
            assertTrue("$table has table_number and service_type", "table_number" in columns && "service_type" in columns)
        }
    }
}
