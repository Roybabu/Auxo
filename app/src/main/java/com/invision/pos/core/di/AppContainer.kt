package com.invision.pos.core.di

import com.invision.pos.domain.billing.BusinessTime
import android.content.Context
import com.invision.pos.core.appearance.AppearanceRepository
import com.invision.pos.core.audit.AuditTrail
import com.invision.pos.core.audit.RoomAuditTrail
import com.invision.pos.core.audit.toAuditDraft
import com.invision.pos.core.auth.AccountEventListener
import com.invision.pos.core.auth.AuthRepository
import com.invision.pos.core.auth.Authorizer
import com.invision.pos.core.auth.SessionAuthorizer
import com.invision.pos.data.security.AuthorizedBackupService
import com.invision.pos.data.security.AuthorizedBillingRepository
import com.invision.pos.data.security.AuthorizedCustomerRepository
import com.invision.pos.data.security.AuthorizedInventoryRepository
import com.invision.pos.data.security.AuthorizedMenuRepository
import com.invision.pos.data.security.AuthorizedPurchasingRepository
import com.invision.pos.data.security.AuthorizedSettingsRepository
import com.invision.pos.data.security.AuthorizedShopPreferences
import com.invision.pos.data.security.AuthorizedStoreLogoRepository
import com.invision.pos.core.auth.FileAccountStore
import com.invision.pos.core.auth.LocalAuthRepository
import com.invision.pos.core.auth.SharedPrefsLockSettings
import com.invision.pos.core.shop.FileStoreLogoRepository
import com.invision.pos.core.shop.ShopPreferences
import com.invision.pos.core.shop.StoreLogoRepository
import com.invision.pos.core.shop.SharedPrefsShopPreferences
import com.invision.pos.core.appearance.SharedPrefsAppearanceRepository
import android.net.Uri
import com.invision.pos.core.auth.Role
import com.invision.pos.core.auth.SessionUser
import com.invision.pos.core.backup.auto.AutoBackupRunner
import com.invision.pos.core.backup.auto.AutoBackupScheduler
import com.invision.pos.core.backup.auto.AutoBackupSettingsStore
import com.invision.pos.core.backup.auto.DocumentTreeFolder
import com.invision.pos.core.backup.auto.SharedPrefsAutoBackupStore
import com.invision.pos.core.backup.auto.WorkManagerAutoBackupScheduler
import com.invision.pos.data.security.AuthorizedAutoBackupService
import com.invision.pos.data.security.AutoBackupService
import com.invision.pos.core.backup.BackupManager
import com.invision.pos.core.backup.BackupService
import com.invision.pos.core.backup.DocumentsBackupStorage
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container, created once per process by InvisionApplication.
 * Kept deliberately small; it can be replaced by a DI framework later.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = BusinessTime.clock()

    private val database: InvisionDatabase by lazy { InvisionDatabase.create(appContext) }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val authRepository: AuthRepository by lazy {
        LocalAuthRepository(
            FileAccountStore(appContext.filesDir),
            clock = clock,
            lockSettings = SharedPrefsLockSettings(appContext),
            listener = AccountEventListener { event ->
                // Best effort and off the caller's thread: the account change has already been saved.
                background.launch { runCatching { auditTrail.record(event.toAuditDraft(), event.actor) } }
            },
        )
    }

    /** Answers "may the signed-in person do this?" for the data layer below. */
    val authorizer: Authorizer by lazy { SessionAuthorizer(authRepository) }

    val auditTrail: AuditTrail by lazy { RoomAuditTrail(database, clock, authorizer) }

    // Every repository the screens use is wrapped, so the checks sit below the screens and view models.
    val inventoryRepository: InventoryRepository by lazy { AuthorizedInventoryRepository(RoomInventoryRepository(database, clock), authorizer, auditTrail) }

    val billingRepository: BillingRepository by lazy { AuthorizedBillingRepository(RoomBillingRepository(database), authorizer, auditTrail, onSaleRecorded = { onSaleRecorded() }, employeeDiscountLimit = { RoomSettingsRepository(database).getSettings().employeeDiscountLimitBp }) }

    /** A finished sale counts towards "back up every N invoices", and may make that backup due. */
    private fun onSaleRecorded() {
        autoBackupRunner.onInvoiceRecorded()
        if (autoBackupRunner.isDueNow()) background.launch { runCatching { autoBackupRunner.runIfDue() } }
    }

    val loyaltyRepository: com.invision.pos.data.repository.LoyaltyRepository by lazy {
        com.invision.pos.data.security.AuthorizedLoyaltyRepository(com.invision.pos.data.repository.RoomLoyaltyRepository(database), authorizer, auditTrail)
    }
    val heldBillRepository: com.invision.pos.data.repository.HeldBillRepository by lazy {
        com.invision.pos.data.security.AuthorizedHeldBillRepository(com.invision.pos.data.repository.RoomHeldBillRepository(database, clock), authorizer)
    }
    val quotationRepository: com.invision.pos.data.repository.QuotationRepository by lazy {
        com.invision.pos.data.security.AuthorizedQuotationRepository(com.invision.pos.data.repository.RoomQuotationRepository(database, clock), authorizer)
    }

    val settingsRepository: SettingsRepository by lazy { AuthorizedSettingsRepository(RoomSettingsRepository(database), authorizer, auditTrail) }

    val purchasingRepository: PurchasingRepository by lazy { AuthorizedPurchasingRepository(RoomPurchasingRepository(database, clock), authorizer, auditTrail) }

    val customerRepository: CustomerRepository by lazy { AuthorizedCustomerRepository(RoomCustomerRepository(database, clock), authorizer, auditTrail) }

    val gstReportService: com.invision.pos.data.security.GstReportService by lazy {
        com.invision.pos.data.security.GstReportService(billingRepository, authorizer)
    }

    val dayEndRepository: com.invision.pos.data.repository.DayEndRepository by lazy {
        com.invision.pos.data.security.AuthorizedDayEndRepository(com.invision.pos.data.repository.RoomDayEndRepository(database, clock), authorizer, auditTrail)
    }

    val menuRepository: MenuRepository by lazy { AuthorizedMenuRepository(RoomMenuRepository(database, clock), authorizer, auditTrail) }

    val barcodeGenerator: InternalBarcodeGenerator by lazy { InternalBarcodeGenerator() }

    val moneyFormatter: MoneyFormatter by lazy { MoneyFormatter() }

    val appearanceRepository: AppearanceRepository by lazy { SharedPrefsAppearanceRepository(appContext) }
    val languageRepository: com.invision.pos.core.locale.LanguageRepository by lazy { com.invision.pos.core.locale.SharedPrefsLanguageRepository(appContext) }

    private val rawShopPreferences: ShopPreferences by lazy { SharedPrefsShopPreferences(appContext) }

    val shopPreferences: ShopPreferences by lazy { AuthorizedShopPreferences(rawShopPreferences, authorizer) }

    val storeLogoRepository: StoreLogoRepository by lazy { AuthorizedStoreLogoRepository(FileStoreLogoRepository(appContext), authorizer) }

    val backupService: BackupService by lazy {
        AuthorizedBackupService(
            BackupManager(appContext, database, appearanceRepository, DocumentsBackupStorage(appContext), clock, rawShopPreferences),
            authorizer,
            auditTrail,
        )
    }

    val autoBackupSettings: AutoBackupSettingsStore by lazy { SharedPrefsAutoBackupStore(appContext) }

    val autoBackupScheduler: AutoBackupScheduler by lazy { WorkManagerAutoBackupScheduler(appContext) }

    /** Makes the scheduled backups. It acts for the app itself, so it needs nobody signed in. */
    val autoBackupRunner: AutoBackupRunner by lazy {
        val system = SessionUser(0L, "Automatic backup", Role.SUPER_ADMIN, emptySet())
        AutoBackupRunner(
            store = autoBackupSettings,
            folderFor = { uri -> DocumentTreeFolder(appContext, Uri.parse(uri)) },
            backUpTo = { storage -> BackupManager(appContext, database, appearanceRepository, storage, clock, rawShopPreferences).backUp() },
            clock = clock,
            log = { draft -> auditTrail.record(draft, system) },
        )
    }

    val autoBackupService: AutoBackupService by lazy {
        AuthorizedAutoBackupService(autoBackupSettings, autoBackupRunner, autoBackupScheduler, authorizer, auditTrail, background)
    }

    /** Called when the app opens: catches up on a backup that came due while the phone was off or the app closed. */
    fun checkAutoBackup() {
        // Only touch the background scheduler when backup is on; turning it off is done when the choice is made.
        if (autoBackupSettings.settings.value.isActive) autoBackupScheduler.sync(true)
        background.launch { runCatching { autoBackupRunner.runIfDue() } }
    }

    val observeStoreOverview: ObserveStoreOverviewUseCase by lazy {
        ObserveStoreOverviewUseCase(settingsRepository, billingRepository, clock)
    }

    val observeInventorySummary: ObserveInventorySummaryUseCase by lazy {
        ObserveInventorySummaryUseCase(inventoryRepository, clock, settingsRepository)
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
