// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.auxo.pos.data.local.dao.AuditDao
import com.auxo.pos.data.local.dao.CategoryDao
import com.auxo.pos.data.local.dao.DayEndDao
import com.auxo.pos.data.local.dao.HeldBillDao
import com.auxo.pos.data.local.dao.QuotationDao
import com.auxo.pos.data.local.dao.BillingDao
import com.auxo.pos.data.local.dao.CustomerDao
import com.auxo.pos.data.local.dao.InventoryDao
import com.auxo.pos.data.local.dao.OperationDao
import com.auxo.pos.data.local.dao.PurchasingDao
import com.auxo.pos.data.local.dao.StockCountDao
import com.auxo.pos.data.local.entity.AppliedRequestEntity
import com.auxo.pos.data.local.entity.AuditLogEntity
import com.auxo.pos.data.local.entity.DayClosingEntity
import com.auxo.pos.data.local.entity.HeldBillEntity
import com.auxo.pos.data.local.entity.QuotationEntity
import com.auxo.pos.data.local.entity.CustomerEntity
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
import com.auxo.pos.data.local.entity.StockBatchEntity
import com.auxo.pos.data.local.entity.StockCountEntity
import com.auxo.pos.data.local.entity.StockMovementEntity
import com.auxo.pos.data.local.entity.StoreSettingsEntity
import com.auxo.pos.data.local.entity.ShopCategoryEntity
import com.auxo.pos.data.local.entity.ShopSubcategoryEntity
import com.auxo.pos.data.local.entity.SupplierEntity

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
 * 10. Held (parked) bills: the held_bills table. Written by hand ([MIGRATION_9_10]).
 * 11. Open restaurant orders: service type, table and kitchen-ticket columns on held_bills, and service type
 *     and table on invoices. Written by hand ([MIGRATION_10_11]).
 * 12. Quotations: the quotations table. Written by hand ([MIGRATION_11_12]).
 * 13. The expiring-soon warning window (store_settings.expiry_warning_days, 30 for existing shops). Written by hand
 *     ([MIGRATION_12_13]).
 * 14. Stock counts: the stock_counts table. Written by hand ([MIGRATION_13_14]).
 * 15. Product photos: products.photo_file, the name of the photo file (null for existing products). Written by
 *     hand ([MIGRATION_14_15]).
 * 16. The restaurant menu moves into products: products.subcategory, products.available and products.tracks_stock,
 *     each menu dish becomes a Restaurant product, and the menu_items and recipe_lines tables are dropped. Written
 *     by hand ([MIGRATION_15_16], see [DishMigration]).
 * 17. Shop categories the admin can edit: the shop_categories and shop_subcategories tables and products.category_id.
 *     Existing categories become rows, all on (Restaurant only if the shop already has dishes). Written by hand
 *     ([MIGRATION_16_17]).
 * 18. FSSAI number: store_settings.fssai and invoices.seller_fssai (empty for existing shops and bills). Written by
 *     hand ([MIGRATION_17_18]).
 * 19. Order type and kitchen tickets are removed: held_bills.service_type, table_number, kot_sent and kot_count and
 *     invoices.service_type and table_number are dropped. The Ingredient category is removed too, with its products,
 *     batches and stock movements. An automatic migration from 18 ([RemoveKitchenAndIngredients]).
 * 20. Footwear and Accessories: two built-in rows are added to shop_categories, on, right after Garment. No table
 *     changes. Written by hand ([MIGRATION_19_20]).
 * 21. Whether each bill was rounded to the rupee (invoices.rounded_to_rupee), so partial refunds follow the bill's own
 *     rounding. Existing bills count as rounded when their total is whole rupees, as before. Written by hand
 *     ([MIGRATION_20_21]).
 * 22. General item, Electronics, Pharmacy and Cosmetics: four built-in rows are added to shop_categories, off, at the
 *     end of the list; products.warranty_months (electronics) and products.prescription_required (pharmacy). Written
 *     by hand ([MIGRATION_21_22]).
 * 23. Loose goods and Jewellery: two built-in rows are added to shop_categories, off, at the end of the list;
 *     invoice_lines.stock_unit (lines sold by weight or volume); products.metal and products.making_charge_bp
 *     (jewellery); held_bills.table_number and held_bills.token_number (parked bills belong to a restaurant table or
 *     get a number). Written by hand ([MIGRATION_22_23]).
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
        AuditLogEntity::class,
        DayClosingEntity::class,
        LoyaltySettingsEntity::class,
        HeldBillEntity::class,
        QuotationEntity::class,
        StockCountEntity::class,
        ShopCategoryEntity::class,
        ShopSubcategoryEntity::class,
    ],
    version = AuxoDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = AuxoDatabase.BatchBackfill::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 18, to = 19, spec = AuxoDatabase.RemoveKitchenAndIngredients::class),
    ],
)
abstract class AuxoDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    abstract fun billingDao(): BillingDao

    abstract fun purchasingDao(): PurchasingDao

    abstract fun customerDao(): CustomerDao

    abstract fun operationDao(): OperationDao

    abstract fun auditDao(): AuditDao

    abstract fun dayEndDao(): DayEndDao

    abstract fun heldBillDao(): HeldBillDao

    abstract fun quotationDao(): QuotationDao

    abstract fun stockCountDao(): StockCountDao

    abstract fun categoryDao(): CategoryDao

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

    /**
     * Version 19 drops the order-type and kitchen-ticket columns (Room rebuilds the two tables) and then removes
     * the Ingredient category: its products, variants, batches and stock movements and its shop category rows.
     * Old bills, purchase lines and receipt lines that point at an ingredient keep their text and lose only the
     * link. Foreign keys are not enforced while a migration runs, so the children are cleared here by hand.
     */
    @DeleteColumn.Entries(
        DeleteColumn(tableName = "held_bills", columnName = "service_type"),
        DeleteColumn(tableName = "held_bills", columnName = "table_number"),
        DeleteColumn(tableName = "held_bills", columnName = "kot_sent"),
        DeleteColumn(tableName = "held_bills", columnName = "kot_count"),
        DeleteColumn(tableName = "invoices", columnName = "service_type"),
        DeleteColumn(tableName = "invoices", columnName = "table_number"),
    )
    class RemoveKitchenAndIngredients : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            // Kitchen ticket numbers ("KOT|<day>") are no longer used.
            db.execSQL("DELETE FROM number_sequences WHERE name LIKE 'KOT|%'")
            val ingredientVariants = "SELECT id FROM product_variants WHERE product_id IN (SELECT id FROM products WHERE category = 'INGREDIENT')"
            db.execSQL("DELETE FROM stock_movements WHERE variant_id IN ($ingredientVariants)")
            db.execSQL("DELETE FROM stock_batches WHERE variant_id IN ($ingredientVariants)")
            db.execSQL("UPDATE invoice_lines SET variant_id = NULL WHERE variant_id IN ($ingredientVariants)")
            db.execSQL("UPDATE purchase_order_lines SET variant_id = NULL WHERE variant_id IN ($ingredientVariants)")
            db.execSQL("UPDATE purchase_receipt_lines SET variant_id = NULL WHERE variant_id IN ($ingredientVariants)")
            db.execSQL("DELETE FROM product_variants WHERE product_id IN (SELECT id FROM products WHERE category = 'INGREDIENT')")
            db.execSQL("DELETE FROM products WHERE category = 'INGREDIENT'")
            db.execSQL("DELETE FROM shop_subcategories WHERE category_id IN (SELECT id FROM shop_categories WHERE profile = 'INGREDIENT')")
            db.execSQL("DELETE FROM shop_categories WHERE profile = 'INGREDIENT'")
        }
    }

    companion object {
        const val NAME = "auxo.db"

        /** The database version above, for code that checks a file against it. */
        const val VERSION = 23

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

        /** Version 10 adds parked bills. Nothing existing changes. */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS held_bills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, " +
                        "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, item_count INTEGER NOT NULL, " +
                        "total_minor INTEGER NOT NULL, payload TEXT NOT NULL)",
                )
            }
        }

        /** Version 11: open restaurant orders. Existing parked bills are plain bills (no service type, no tickets). */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE held_bills ADD COLUMN service_type TEXT")
                db.execSQL("ALTER TABLE held_bills ADD COLUMN table_number TEXT")
                db.execSQL("ALTER TABLE held_bills ADD COLUMN kot_sent TEXT")
                db.execSQL("ALTER TABLE held_bills ADD COLUMN kot_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN service_type TEXT")
                db.execSQL("ALTER TABLE invoices ADD COLUMN table_number TEXT")
            }
        }

        /** Version 12: quotations. Nothing existing changes; the table starts empty. */
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS quotations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, number TEXT NOT NULL, " +
                        "created_at INTEGER NOT NULL, valid_until_day INTEGER NOT NULL, customer_name TEXT NOT NULL, " +
                        "customer_phone TEXT NOT NULL, item_count INTEGER NOT NULL, total_minor INTEGER NOT NULL, " +
                        "payload TEXT NOT NULL, status TEXT NOT NULL, converted_invoice_id INTEGER)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_quotations_number ON quotations (number)")
            }
        }

        /** Version 13: the expiring-soon window. Existing shops keep the 30 days they always had. */
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE store_settings ADD COLUMN expiry_warning_days INTEGER NOT NULL DEFAULT 30")
            }
        }

        /** Version 14: stock counts. Nothing existing changes; the table starts empty. */
        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS stock_counts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, started_at INTEGER NOT NULL, " +
                        "status TEXT NOT NULL, payload TEXT NOT NULL, finished_at INTEGER, reason TEXT, note TEXT, " +
                        "adjusted_count INTEGER NOT NULL DEFAULT 0)",
                )
            }
        }

        /** Version 15: product photos. Existing products have none; the files live outside the database. */
        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN photo_file TEXT")
            }
        }

        /**
         * Version 16: dishes become Restaurant products (see [DishMigration]). Existing products keep stock tracking
         * and stay available, because the new columns default to on.
         */
        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) = DishMigration.run(db)
        }

        /**
         * Version 17: the shop's categories become rows. The five built-in categories are added, all on except
         * Restaurant, which is on only when the shop already has dishes. Every product is linked to the row for its
         * kind, and the sections already used by restaurant items become subcategories.
         */
        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `shop_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `profile` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `built_in` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `shop_subcategories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, FOREIGN KEY(`category_id`) REFERENCES `shop_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shop_subcategories_category_id_name` ON `shop_subcategories` (`category_id`, `name`)")
                db.execSQL("ALTER TABLE products ADD COLUMN category_id INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_category_id` ON `products` (`category_id`)")
                val order = listOf("GARMENT", "PACKAGED_FOOD", "BEVERAGE", "RESTAURANT", "INGREDIENT")
                order.forEachIndexed { index, profile ->
                    val enabled = if (profile == "RESTAURANT") "(SELECT COUNT(*) > 0 FROM products WHERE category = 'RESTAURANT')" else "1"
                    db.execSQL("INSERT INTO shop_categories (name, profile, enabled, sort_order, built_in) VALUES (NULL, '$profile', $enabled, $index, 1)")
                }
                db.execSQL("UPDATE products SET category_id = (SELECT c.id FROM shop_categories c WHERE c.profile = products.category AND c.built_in = 1)")
                db.execSQL(
                    "INSERT OR IGNORE INTO shop_subcategories (category_id, name, sort_order) " +
                        "SELECT category_id, TRIM(subcategory), 0 FROM products " +
                        "WHERE category_id IS NOT NULL AND subcategory IS NOT NULL AND TRIM(subcategory) <> '' GROUP BY category_id, TRIM(subcategory)",
                )
            }
        }

        /** Version 18: the shop's FSSAI number, and the copy printed on each bill. Both start empty. */
        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE store_settings ADD COLUMN fssai TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN seller_fssai TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Version 20: the built-in Footwear and Accessories categories are added to a shop that already has its
         * categories, on, listed straight after Garment. A shop with no category rows yet is seeded with them later.
         */
        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Straight after Garment; a shop without the built-in Garment row gets them at the end of its list.
                // A shop with no category rows yet is seeded with them later, so it gets nothing here.
                val garment = "(SELECT MIN(sort_order) FROM shop_categories WHERE profile = 'GARMENT' AND built_in = 1)"
                val after = "COALESCE($garment, (SELECT MAX(sort_order) FROM shop_categories))"
                db.execSQL("UPDATE shop_categories SET sort_order = sort_order + 2 WHERE sort_order > $after")
                listOf("FOOTWEAR", "ACCESSORIES").forEachIndexed { index, profile ->
                    db.execSQL(
                        "INSERT INTO shop_categories (name, profile, enabled, sort_order, built_in) " +
                            "SELECT NULL, '$profile', 1, $after + ${index + 1}, 1 WHERE $after IS NOT NULL " +
                            "AND NOT EXISTS (SELECT 1 FROM shop_categories WHERE profile = '$profile' AND built_in = 1)",
                    )
                }
            }
        }

        /**
         * Version 21: invoices.rounded_to_rupee. Older bills are marked rounded when their total is whole rupees,
         * which is how partial refunds treated them before, so nothing already issued changes.
         */
        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN rounded_to_rupee INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE invoices SET rounded_to_rupee = CASE WHEN grand_total_minor % 100 = 0 THEN 1 ELSE 0 END")
            }
        }

        /**
         * Version 22: four more built-in categories (General item, Electronics, Pharmacy, Cosmetics), added off at the
         * end of a shop's list so nothing it sells changes until the owner switches them on, and the two product
         * columns they use. A shop with no category rows yet is seeded with them later.
         */
        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN warranty_months INTEGER")
                db.execSQL("ALTER TABLE products ADD COLUMN prescription_required INTEGER NOT NULL DEFAULT 0")
                listOf("GENERAL", "ELECTRONICS", "PHARMACY", "COSMETICS").forEachIndexed { index, profile ->
                    db.execSQL(
                        "INSERT INTO shop_categories (name, profile, enabled, sort_order, built_in) " +
                            "SELECT NULL, '$profile', 0, (SELECT MAX(sort_order) FROM shop_categories) + 1, 1 " +
                            "WHERE EXISTS (SELECT 1 FROM shop_categories) " +
                            "AND NOT EXISTS (SELECT 1 FROM shop_categories WHERE profile = '$profile' AND built_in = 1)",
                    )
                }
            }
        }

        /**
         * Loose goods and Jewellery join the category list, off and at the end like the four before them, with the
         * columns they need: the unit a bill line was weighed in, and a piece of jewellery's metal and making charge.
         */
        val MIGRATION_22_23: Migration = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN stock_unit TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN metal TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN making_charge_bp INTEGER")
                db.execSQL("ALTER TABLE held_bills ADD COLUMN table_number INTEGER")
                db.execSQL("ALTER TABLE held_bills ADD COLUMN token_number INTEGER")
                listOf("LOOSE", "JEWELLERY").forEach { profile ->
                    db.execSQL(
                        "INSERT INTO shop_categories (name, profile, enabled, sort_order, built_in) " +
                            "SELECT NULL, '$profile', 0, (SELECT MAX(sort_order) FROM shop_categories) + 1, 1 " +
                            "WHERE EXISTS (SELECT 1 FROM shop_categories) " +
                            "AND NOT EXISTS (SELECT 1 FROM shop_categories WHERE profile = '$profile' AND built_in = 1)",
                    )
                }
            }
        }

        // Upgrades are guaranteed only from version 15 (nothing was released before it). Versions 6 to 14 have no
        // exported schema and are tested only through the 4 to 15 chain; see "Database upgrades" in the README.

        /** A builder with every migration added. All code that opens the database file goes through this. */
        fun builder(context: Context, name: String = NAME): RoomDatabase.Builder<AuxoDatabase> =
            Room.databaseBuilder(context.applicationContext, AuxoDatabase::class.java, name)
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)
                .addCallback(object : RoomDatabase.Callback() {
                    // Debug builds only record this (any upgrade has already run by now).
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        com.auxo.pos.core.debuglog.DebugLog.event("Database", "Opened, schema version ${db.version}")
                    }
                })

        /**
         * The database the app runs on. The file is locked with the key from [keys] (see [DatabaseEncryption]); a plain
         * file left by an earlier version is converted first. [builder] stays unlocked, for tests.
         */
        fun create(context: Context, keys: DatabaseKeyStore = KeystoreDatabaseKeyStore(context)): AuxoDatabase {
            val key = DatabaseEncryption.prepare(context, keys)
            val room = builder(context)
            if (key != null) room.openHelperFactory(DatabaseEncryption.openHelperFactory(key))
            return room.build()
        }
    }
}
