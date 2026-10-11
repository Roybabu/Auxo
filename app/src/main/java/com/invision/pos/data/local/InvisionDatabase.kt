package com.invision.pos.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.invision.pos.data.local.dao.BillingDao
import com.invision.pos.data.local.dao.CustomerDao
import com.invision.pos.data.local.dao.InventoryDao
import com.invision.pos.data.local.dao.MenuDao
import com.invision.pos.data.local.dao.OperationDao
import com.invision.pos.data.local.dao.PurchasingDao
import com.invision.pos.data.local.entity.AppliedRequestEntity
import com.invision.pos.data.local.entity.CustomerEntity
import com.invision.pos.data.local.entity.InvoiceEntity
import com.invision.pos.data.local.entity.InvoiceLineEntity
import com.invision.pos.data.local.entity.LedgerEntryEntity
import com.invision.pos.data.local.entity.MenuItemEntity
import com.invision.pos.data.local.entity.NumberSequenceEntity
import com.invision.pos.data.local.entity.PaymentEntity
import com.invision.pos.data.local.entity.ProductEntity
import com.invision.pos.data.local.entity.ProductVariantEntity
import com.invision.pos.data.local.entity.PurchaseOrderEntity
import com.invision.pos.data.local.entity.PurchaseOrderLineEntity
import com.invision.pos.data.local.entity.PurchaseReceiptEntity
import com.invision.pos.data.local.entity.PurchaseReceiptLineEntity
import com.invision.pos.data.local.entity.RecipeLineEntity
import com.invision.pos.data.local.entity.StockBatchEntity
import com.invision.pos.data.local.entity.StockMovementEntity
import com.invision.pos.data.local.entity.StoreSettingsEntity
import com.invision.pos.data.local.entity.SupplierEntity

/**
 * Version history:
 * 1. Products, variants and stock movements (Step 2).
 * 2. GST and HSN on products, invoices, invoice lines, payments, settings and
 *    number sequences; stock movements link to invoices (Step 3).
 * 3. Stock batches, suppliers, purchase orders and goods receipts, customers and
 *    their ledger, restaurant menu and recipes, one-time request ids (Step 4).
 */
@Database(
    entities = [
        ProductEntity::class,
        ProductVariantEntity::class,
        StockMovementEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        PaymentEntity::class,
        StoreSettingsEntity::class,
        NumberSequenceEntity::class,
        StockBatchEntity::class,
        AppliedRequestEntity::class,
        SupplierEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderLineEntity::class,
        PurchaseReceiptEntity::class,
        PurchaseReceiptLineEntity::class,
        CustomerEntity::class,
        LedgerEntryEntity::class,
        MenuItemEntity::class,
        RecipeLineEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = InvisionDatabase.BatchBackfill::class),
    ],
)
abstract class InvisionDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    abstract fun billingDao(): BillingDao

    abstract fun purchasingDao(): PurchasingDao

    abstract fun customerDao(): CustomerDao

    abstract fun menuDao(): MenuDao

    abstract fun operationDao(): OperationDao

    /**
     * Version 3 keeps food and beverage stock in batches. Existing stock becomes
     * one batch per variant, with the batch number and expiry saved on the variant.
     */
    class BatchBackfill : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO stock_batches (variant_id, batch_number, expiry_epoch_day, quantity, received_at, unit_cost_minor) " +
                    "SELECT v.id, v.batch_number, v.expiry_epoch_day, v.stock_quantity, " +
                    "CAST(strftime('%s', 'now') AS INTEGER) * 1000, v.cost_minor " +
                    "FROM product_variants v JOIN products p ON p.id = v.product_id " +
                    "WHERE p.category IN ('PACKAGED_FOOD', 'BEVERAGE') AND v.stock_quantity > 0",
            )
        }
    }

    companion object {
        const val NAME = "invision.db"

        fun create(context: Context): InvisionDatabase =
            Room.databaseBuilder(context.applicationContext, InvisionDatabase::class.java, NAME).build()
    }
}
