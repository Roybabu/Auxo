package com.invision.pos.core.di

import com.invision.pos.domain.billing.BusinessTime
import android.content.Context
import com.invision.pos.core.appearance.AppearanceRepository
import com.invision.pos.core.appearance.SharedPrefsAppearanceRepository
import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.BillingRepository
import com.invision.pos.data.repository.CustomerRepository
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.MenuRepository
import com.invision.pos.data.repository.PurchasingRepository
import com.invision.pos.data.repository.RoomBillingRepository
import com.invision.pos.data.repository.RoomCustomerRepository
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.RoomMenuRepository
import com.invision.pos.data.repository.RoomPurchasingRepository
import com.invision.pos.data.repository.RoomSettingsRepository
import com.invision.pos.data.repository.SettingsRepository
import com.invision.pos.domain.barcode.InternalBarcodeGenerator
import com.invision.pos.domain.usecase.AdjustStockUseCase
import com.invision.pos.domain.usecase.CheckoutUseCase
import com.invision.pos.domain.usecase.CreateReorderDraftsUseCase
import com.invision.pos.domain.usecase.FindByBarcodeUseCase
import com.invision.pos.domain.usecase.ObserveInventorySummaryUseCase
import com.invision.pos.domain.usecase.ObserveStoreOverviewUseCase
import com.invision.pos.domain.usecase.ProcessReturnUseCase
import com.invision.pos.domain.usecase.ReceiveAccountPaymentUseCase
import com.invision.pos.domain.usecase.ReceiveGoodsUseCase
import com.invision.pos.domain.usecase.RecordWastageUseCase
import com.invision.pos.domain.usecase.SaveProductUseCase
import java.time.Clock

/**
 * Manual dependency container, created once per process by InvisionApplication.
 * Kept deliberately small; it can be replaced by a DI framework later.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = BusinessTime.clock()

    private val database: InvisionDatabase by lazy { InvisionDatabase.create(appContext) }

    val inventoryRepository: InventoryRepository by lazy { RoomInventoryRepository(database, clock) }

    val billingRepository: BillingRepository by lazy { RoomBillingRepository(database) }

    val settingsRepository: SettingsRepository by lazy { RoomSettingsRepository(database) }

    val purchasingRepository: PurchasingRepository by lazy { RoomPurchasingRepository(database, clock) }

    val customerRepository: CustomerRepository by lazy { RoomCustomerRepository(database, clock) }

    val menuRepository: MenuRepository by lazy { RoomMenuRepository(database, clock) }

    val barcodeGenerator: InternalBarcodeGenerator by lazy { InternalBarcodeGenerator() }

    val moneyFormatter: MoneyFormatter by lazy { MoneyFormatter() }

    val appearanceRepository: AppearanceRepository by lazy { SharedPrefsAppearanceRepository(appContext) }

    val observeStoreOverview: ObserveStoreOverviewUseCase by lazy {
        ObserveStoreOverviewUseCase(settingsRepository, billingRepository, clock)
    }

    val observeInventorySummary: ObserveInventorySummaryUseCase by lazy {
        ObserveInventorySummaryUseCase(inventoryRepository, clock)
    }

    val saveProduct: SaveProductUseCase by lazy { SaveProductUseCase(inventoryRepository, barcodeGenerator) }

    val adjustStock: AdjustStockUseCase by lazy { AdjustStockUseCase(inventoryRepository, clock) }

    val findByBarcode: FindByBarcodeUseCase by lazy { FindByBarcodeUseCase(inventoryRepository) }

    val checkout: CheckoutUseCase by lazy { CheckoutUseCase(billingRepository, settingsRepository, clock, customerRepository) }

    val processReturn: ProcessReturnUseCase by lazy { ProcessReturnUseCase(billingRepository, settingsRepository, clock) }

    val receiveGoods: ReceiveGoodsUseCase by lazy { ReceiveGoodsUseCase(purchasingRepository, clock) }

    val recordWastage: RecordWastageUseCase by lazy { RecordWastageUseCase(inventoryRepository) }

    val receiveAccountPayment: ReceiveAccountPaymentUseCase by lazy { ReceiveAccountPaymentUseCase(customerRepository) }

    val createReorderDrafts: CreateReorderDraftsUseCase by lazy { CreateReorderDraftsUseCase(purchasingRepository) }
}
