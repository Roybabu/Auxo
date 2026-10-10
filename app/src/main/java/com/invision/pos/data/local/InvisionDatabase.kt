package com.invision.pos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.invision.pos.data.local.dao.InventoryDao
import com.invision.pos.data.local.entity.ProductEntity
import com.invision.pos.data.local.entity.ProductVariantEntity
import com.invision.pos.data.local.entity.StockMovementEntity

@Database(
    entities = [ProductEntity::class, ProductVariantEntity::class, StockMovementEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class InvisionDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    companion object {
        private const val NAME = "invision.db"

        fun create(context: Context): InvisionDatabase =
            Room.databaseBuilder(context.applicationContext, InvisionDatabase::class.java, NAME).build()
    }
}
