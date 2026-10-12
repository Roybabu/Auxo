package com.invision.pos.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.invision.pos.data.local.dao.AuditDao
import com.invision.pos.data.local.dao.DayEndDao
import com.invision.pos.data.local.dao.BillingDao
import com.invision.pos.data.local.dao.CustomerDao
import com.invision.pos.data.local.dao.InventoryDao
import com.invision.pos.data.local.dao.MenuDao
import com.invision.pos.data.local.dao.OperationDao
import com.invision.pos.data.local.dao.PurchasingDao
import com.invision.pos.data.local.entity.AppliedRequestEntity
import com.invision.pos.data.local.entity.AuditLogEntity
import com.invision.pos.data.local.entity.DayClosingEntity
import com.invision.pos.data.local.entity.CustomerEntity
import com.invision.pos.data.local.entity.InvoiceEntity
import com.invision.pos.data.local.entity.InvoiceLineEntity
import com.invision.pos.data.local.entity.LedgerEntryEntity
import com.invision.pos.data.local.entity.LoyaltySettingsEntity
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
 * 4. Activity log of who changed, returned or deleted what (roles and User Management).
 * 5. Day-end closings: the cash counted and the day's figures, one row per closed day.
 * 6. Loyalty points: a points column on the customer ledger and the loyalty rules table. Written by hand
 *    (see [MIGRATION_5_6]) so building does not depend on the exported 5.json.
 * 7. The language bills are printed in (store_settings.receipt_language). Also written by hand ([MIGRATION_6_7]).
 * 8. Whether receipts print the UPI payment QR (store_settings.receipt_show_upi_qr, on for existing shops).
 *    Written by hand ([MIGRATION_7_8]).
 * 9. Discount reasons (invoices.discount_reason, invoice_lines.discount_reason) and the employee discount
 *    limit (store_settings.employee_discount_limit_bp, 0 = none). Written by hand ([MIGRATION_8_9]).
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
        AuditLogEntity::class,
        DayClosingEntity::class,
        LoyaltySettingsEntity::class,
    ],
    version = InvisionDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = InvisionDatabase.BatchBackfill::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class InvisionDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    abstract fun billingDao(): BillingDao

    abstract fun purchasingDao(): PurchasingDao

    abstract fun customerDao(): CustomerDao

    abstract fun menuDao(): MenuDao

    abstract fun operationDao(): OperationDao

    abstract fun auditDao(): AuditDao

    abstract fun dayEndDao(): DayEndDao

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

        /** The database version above, for code that checks a file against it. */
        const val VERSION = 9

        /**
         * Version 6 adds loyalty points. Existing ledger rows get 0 points, so no balance changes, and loyalty
         * starts switched off because the settings table starts empty. The SQL must match the entities exactly;
         * Room checks the result against the version 6 schema when the database opens.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer_ledger ADD COLUMN points INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS loyalty_settings (id INTEGER NOT NULL, enabled INTEGER NOT NULL, " +
                        "spend_per_point_minor INTEGER NOT NULL, point_value_minor INTEGER NOT NULL, PRIMARY KEY(id))",
                )
            }
        }

        /** Version 7 adds the bill language. Existing shops keep printing in English (a missing value means English). */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE store_settings ADD COLUMN receipt_language TEXT")
            }
        }

        /** Version 8: receipts may print a UPI QR. Existing shops keep getting what they asked for, so it starts on. */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE store_settings ADD COLUMN receipt_show_upi_qr INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Version 9: discount reasons and the employee discount limit. Old bills have no reason, and the limit
         * starts at 0, so Employees cannot discount until the Super Admin allows it.
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN discount_reason TEXT")
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN discount_reason TEXT")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN employee_discount_limit_bp INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** A builder with every migration added. All code that opens the database file goes through this. */
        fun builder(context: Context, name: String = NAME): RoomDatabase.Builder<InvisionDatabase> =
            Room.databaseBuilder(context.applicationContext, InvisionDatabase::class.java, name)
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

        fun create(context: Context): InvisionDatabase = builder(context).build()
    }
}
