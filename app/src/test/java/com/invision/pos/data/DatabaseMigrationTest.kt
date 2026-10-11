package com.invision.pos.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.RoomBillingRepository
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.GstRate
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockUnit
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Builds a version 1 database from the committed schema (app/schemas/.../1.json),
 * fills it with Step 2 data, then opens it with the current app so Room runs the
 * 1 -> 2 and 2 -> 3 migrations and checks the result against the current schema.
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
        val db = Room.databaseBuilder(context, InvisionDatabase::class.java, name).allowMainThreadQueries().build()
        database = db
        val inventory = RoomInventoryRepository(db, TestData.clock)

        // Opening runs the migrations and Room validates every table against the current version.
        val product = inventory.getProduct(1)!!
        assertEquals(3, db.openHelper.readableDatabase.version)
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

        val db = Room.databaseBuilder(context, InvisionDatabase::class.java, name).allowMainThreadQueries().build()
        database = db
        val inventory = RoomInventoryRepository(db, TestData.clock)
        val biscuits = inventory.getProduct(2)!!.variants.single()
        assertEquals(3, db.openHelper.readableDatabase.version)

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
}
