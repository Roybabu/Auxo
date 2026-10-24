// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
 * The Auxo database. Version 1 is the first released version, so it is the baseline: nothing older is supported.
 * From the next version on, every change needs a migration, a test and its exported schema in app/schemas.
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

    companion object {
        const val NAME = "auxo.db"

        /** The database version above, for code that checks a file against it. */
        const val VERSION = 1

        /** A builder for the database. All code that opens the database file goes through this. */
        fun builder(context: Context, name: String = NAME): RoomDatabase.Builder<AuxoDatabase> =
            Room.databaseBuilder(context.applicationContext, AuxoDatabase::class.java, name)
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
