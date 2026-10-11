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

    private fun createVersion1() {
        val schema = JSONObject(File("schemas/com.invision.pos.data.local.InvisionDatabase/1.json").readText()).getJSONObject("database")
        assertEquals(1, schema.getInt("version"))
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
        db.version = 1

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
}
