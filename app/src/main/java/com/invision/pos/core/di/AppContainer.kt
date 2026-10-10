package com.invision.pos.core.di

import android.content.Context
import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.InMemoryStoreRepository
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.StoreRepository
import com.invision.pos.domain.barcode.InternalBarcodeGenerator
import com.invision.pos.domain.usecase.AdjustStockUseCase
import com.invision.pos.domain.usecase.FindByBarcodeUseCase
import com.invision.pos.domain.usecase.ObserveInventorySummaryUseCase
import com.invision.pos.domain.usecase.ObserveStoreOverviewUseCase
import com.invision.pos.domain.usecase.SaveProductUseCase
import java.time.Clock

/**
 * Manual dependency container, created once per process by InvisionApplication.
 * Kept deliberately small; it can be replaced by a DI framework later.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = Clock.systemDefaultZone()

    private val database: InvisionDatabase by lazy { InvisionDatabase.create(appContext) }

    val storeRepository: StoreRepository by lazy { InMemoryStoreRepository() }

    val inventoryRepository: InventoryRepository by lazy {
        RoomInventoryRepository(database.inventoryDao(), clock)
    }

    val barcodeGenerator: InternalBarcodeGenerator by lazy { InternalBarcodeGenerator() }

    val moneyFormatter: MoneyFormatter by lazy { MoneyFormatter() }

    val observeStoreOverview: ObserveStoreOverviewUseCase by lazy {
        ObserveStoreOverviewUseCase(storeRepository)
    }

    val observeInventorySummary: ObserveInventorySummaryUseCase by lazy {
        ObserveInventorySummaryUseCase(inventoryRepository, clock)
    }

    val saveProduct: SaveProductUseCase by lazy { SaveProductUseCase(inventoryRepository, barcodeGenerator) }

    val adjustStock: AdjustStockUseCase by lazy { AdjustStockUseCase(inventoryRepository, clock) }

    val findByBarcode: FindByBarcodeUseCase by lazy { FindByBarcodeUseCase(inventoryRepository) }
}
