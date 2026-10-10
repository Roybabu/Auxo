package com.invision.pos.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.invision.pos.data.local.dao.BillingDao
import com.invision.pos.data.local.dao.InventoryDao
import com.invision.pos.data.local.entity.InvoiceEntity
import com.invision.pos.data.local.entity.InvoiceLineEntity
import com.invision.pos.data.local.entity.NumberSequenceEntity
import com.invision.pos.data.local.entity.PaymentEntity
import com.invision.pos.data.local.entity.ProductEntity
import com.invision.pos.data.local.entity.ProductVariantEntity
import com.invision.pos.data.local.entity.StockMovementEntity
import com.invision.pos.data.local.entity.StoreSettingsEntity

/**
 * Version history:
 * 1. Products, variants and stock movements (Step 2).
 * 2. GST and HSN on products, invoices, invoice lines, payments, settings and
 *    number sequences; stock movements link to invoices (Step 3).
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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class InvisionDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    abstract fun billingDao(): BillingDao

    companion object {
        const val NAME = "invision.db"

        fun create(context: Context): InvisionDatabase =
            Room.databaseBuilder(context.applicationContext, InvisionDatabase::class.java, NAME).build()
    }
}
