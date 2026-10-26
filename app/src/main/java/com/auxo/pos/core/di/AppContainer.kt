// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.di

import com.auxo.pos.domain.billing.BusinessTime
import android.content.Context
import com.auxo.pos.core.appearance.AppearanceRepository
import com.auxo.pos.core.audit.AuditTrail
import com.auxo.pos.core.audit.RoomAuditTrail
import com.auxo.pos.core.audit.toAuditDraft
import com.auxo.pos.core.auth.AccountEventListener
import com.auxo.pos.core.auth.AuthRepository
import com.auxo.pos.core.auth.Authorizer
import com.auxo.pos.core.auth.SessionAuthorizer
import com.auxo.pos.data.security.AuthorizedBackupService
import com.auxo.pos.data.security.AuthorizedBillingRepository
import com.auxo.pos.data.security.AuthorizedCustomerRepository
import com.auxo.pos.data.security.AuthorizedInventoryRepository
import com.auxo.pos.data.security.AuthorizedPurchasingRepository
import com.auxo.pos.data.security.AuthorizedSettingsRepository
import com.auxo.pos.data.security.AuthorizedShopPreferences
import com.auxo.pos.data.security.AuthorizedStoreLogoRepository
import com.auxo.pos.core.auth.FileAccountStore
import com.auxo.pos.core.auth.KeystoreSessionVault
import com.auxo.pos.core.auth.LocalAuthRepository
import com.auxo.pos.core.auth.SavedSessionVault
import com.auxo.pos.core.auth.SharedPrefsLockSettings
import com.auxo.pos.core.auth.SharedPrefsLockoutStore
import com.auxo.pos.core.shop.FileStoreLogoRepository
import com.auxo.pos.core.shop.ShopPreferences
import com.auxo.pos.core.shop.StoreLogoRepository
import com.auxo.pos.core.shop.SharedPrefsShopPreferences
import com.auxo.pos.core.appearance.SharedPrefsAppearanceRepository
import android.net.Uri
import com.auxo.pos.core.auth.Role
import com.auxo.pos.core.auth.SessionUser
import com.auxo.pos.core.backup.auto.AutoBackupRunner
import com.auxo.pos.core.backup.auto.AutoBackupScheduler
import com.auxo.pos.core.backup.auto.AutoBackupSettingsStore
import com.auxo.pos.core.backup.auto.DocumentsAutoFolder
import com.auxo.pos.core.backup.auto.SharedPrefsAutoBackupStore
import com.auxo.pos.core.backup.auto.WorkManagerAutoBackupScheduler
import com.auxo.pos.data.security.AuthorizedAutoBackupService
import com.auxo.pos.data.security.AutoBackupService
import com.auxo.pos.core.backup.BackupManager
import com.auxo.pos.core.backup.BackupService
import com.auxo.pos.core.backup.DocumentsBackupStorage
import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.BillingRepository
import com.auxo.pos.data.repository.CustomerRepository
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.PurchasingRepository
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomCustomerRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.RoomPurchasingRepository
import com.auxo.pos.data.repository.RoomSettingsRepository
import com.auxo.pos.data.repository.SettingsRepository
import com.auxo.pos.domain.barcode.InternalBarcodeGenerator
import com.auxo.pos.domain.usecase.AdjustStockUseCase
import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.domain.usecase.CreateReorderDraftsUseCase
import com.auxo.pos.domain.usecase.FindByBarcodeUseCase
import com.auxo.pos.domain.usecase.ObserveInventorySummaryUseCase
import com.auxo.pos.domain.usecase.ObserveStoreOverviewUseCase
import com.auxo.pos.domain.usecase.ProcessReturnUseCase
import com.auxo.pos.domain.usecase.ReceiveAccountPaymentUseCase
import com.auxo.pos.domain.usecase.ReceiveGoodsUseCase
import com.auxo.pos.domain.usecase.RecordWastageUseCase
import com.auxo.pos.domain.usecase.SaveProductUseCase
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * Manual dependency container, created once per process by AuxoApplication.
 * Kept deliberately small; it can be replaced by a DI framework later.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = BusinessTime.clock()

    /** Holds the key that locks the database file on this phone. */
    private val databaseKeys: com.auxo.pos.data.local.DatabaseKeyStore by lazy { com.auxo.pos.data.local.KeystoreDatabaseKeyStore(appContext) }

    private val database: AuxoDatabase by lazy { AuxoDatabase.create(appContext, databaseKeys) }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Looks for newer releases of the app on GitHub and installs the one the user chooses. */
    val appUpdater: com.auxo.pos.core.update.AppUpdater by lazy { com.auxo.pos.core.update.AppUpdater(appContext, background) }

    /** The saved sign-in that only the phone's fingerprint can open (see [KeystoreSessionVault]). */
    val sessionVault: SavedSessionVault by lazy { KeystoreSessionVault(appContext) }

    val authRepository: AuthRepository by lazy {
        LocalAuthRepository(
            FileAccountStore(appContext.filesDir),
            sessions = sessionVault,
            clock = clock,
            lockSettings = SharedPrefsLockSettings(appContext),
            lockouts = SharedPrefsLockoutStore(appContext),
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

    val loyaltyRepository: com.auxo.pos.data.repository.LoyaltyRepository by lazy {
        com.auxo.pos.data.security.AuthorizedLoyaltyRepository(com.auxo.pos.data.repository.RoomLoyaltyRepository(database), authorizer, auditTrail)
    }
    val heldBillRepository: com.auxo.pos.data.repository.HeldBillRepository by lazy {
        com.auxo.pos.data.security.AuthorizedHeldBillRepository(com.auxo.pos.data.repository.RoomHeldBillRepository(database, clock), authorizer)
    }
    val stockCountRepository: com.auxo.pos.data.repository.StockCountRepository by lazy {
        com.auxo.pos.data.security.AuthorizedStockCountRepository(com.auxo.pos.data.repository.RoomStockCountRepository(database, clock), authorizer, auditTrail)
    }
    val postStockCount: com.auxo.pos.domain.usecase.PostStockCountUseCase by lazy {
        com.auxo.pos.domain.usecase.PostStockCountUseCase(inventoryRepository, stockCountRepository, clock)
    }
    val quotationRepository: com.auxo.pos.data.repository.QuotationRepository by lazy {
        com.auxo.pos.data.security.AuthorizedQuotationRepository(com.auxo.pos.data.repository.RoomQuotationRepository(database, clock), authorizer)
    }

    /**
     * Saves the business name typed on the setup form as the store name. It runs on the app's own scope, so it still finishes
     * when the setup screen closes as soon as the account exists. A failure leaves the default name, which can be edited in settings.
     */
    fun rememberBusinessName(name: String) {
        background.launch {
            runCatching { settingsRepository.saveSettings(settingsRepository.getSettings().copy(storeName = name)) }
        }
    }

    /**
     * Saves everything the setup form collected about the store: the name, the optional details and the logo. Like
     * [rememberBusinessName] it runs on the app's own scope, and a failure leaves the defaults, which can be edited
     * later in Store details. The logo is saved after the settings, and only if one was picked.
     */
    fun rememberStore(name: String, details: com.auxo.pos.domain.model.StoreSettings, logo: android.net.Uri?) {
        background.launch {
            runCatching { settingsRepository.saveSettings(details.copy(storeName = name)) }
            if (logo != null) runCatching { storeLogoRepository.save(logo) }
        }
    }

    /** Whether the owner closed the "add your FSSAI number" reminder on Home. */
    val fssaiHintStore: com.auxo.pos.viewmodel.FssaiHintStore by lazy { com.auxo.pos.viewmodel.SharedPrefsFssaiHintStore(appContext) }

    val settingsRepository: SettingsRepository by lazy { AuthorizedSettingsRepository(RoomSettingsRepository(database), authorizer, auditTrail) }

    val categoryRepository: com.auxo.pos.data.repository.CategoryRepository by lazy {
        com.auxo.pos.data.security.AuthorizedCategoryRepository(com.auxo.pos.data.repository.RoomCategoryRepository(database), authorizer, auditTrail)
    }

    val purchasingRepository: PurchasingRepository by lazy { AuthorizedPurchasingRepository(RoomPurchasingRepository(database, clock), authorizer, auditTrail) }

    val customerRepository: CustomerRepository by lazy { AuthorizedCustomerRepository(RoomCustomerRepository(database, clock, onTimeBonus = { rawShopPreferences.onTimeBonusPoints.value }), authorizer, auditTrail) }

    val gstReportService: com.auxo.pos.data.security.GstReportService by lazy {
        com.auxo.pos.data.security.GstReportService(billingRepository, authorizer)
    }

    val dayEndRepository: com.auxo.pos.data.repository.DayEndRepository by lazy {
        com.auxo.pos.data.security.AuthorizedDayEndRepository(com.auxo.pos.data.repository.RoomDayEndRepository(database, clock), authorizer, auditTrail)
    }


    val barcodeGenerator: InternalBarcodeGenerator by lazy { InternalBarcodeGenerator() }

    val moneyFormatter: MoneyFormatter by lazy { MoneyFormatter() }

    val appearanceRepository: AppearanceRepository by lazy { SharedPrefsAppearanceRepository(appContext) }
    val languageRepository: com.auxo.pos.core.locale.LanguageRepository by lazy { com.auxo.pos.core.locale.SharedPrefsLanguageRepository(appContext) }

    private val rawShopPreferences: ShopPreferences by lazy { SharedPrefsShopPreferences(appContext) }

    val shopPreferences: ShopPreferences by lazy { AuthorizedShopPreferences(rawShopPreferences, authorizer) }

    /** Product photo files in the app's private storage. */
    val productPhotos: com.auxo.pos.core.photos.ProductPhotoStore by lazy { com.auxo.pos.core.photos.FileProductPhotoStore(appContext) }

    /** The shop's Bluetooth receipt printer: which one, how wide, and printing to it. */
    val printerPreferences: com.auxo.pos.core.printer.PrinterPreferences by lazy { com.auxo.pos.core.printer.SharedPrefsPrinterPreferences(appContext) }
    val bluetoothDevices: com.auxo.pos.core.printer.BluetoothDevices by lazy { com.auxo.pos.core.printer.AndroidBluetoothDevices(appContext) }
    val receiptPrinting: com.auxo.pos.core.printer.ReceiptPrinting by lazy {
        com.auxo.pos.core.printer.ThermalReceiptPrinter(appContext, printerPreferences, com.auxo.pos.core.printer.AndroidPrinterTransport(appContext))
    }

    val storeLogoRepository: StoreLogoRepository by lazy { AuthorizedStoreLogoRepository(FileStoreLogoRepository(appContext), authorizer) }

    /** Holds the passphrase that locks backups, when the owner has set one. */
    private val backupPassphrase: com.auxo.pos.core.backup.BackupPassphraseStore by lazy {
        com.auxo.pos.core.backup.KeystoreBackupPassphraseStore(appContext)
    }

    /**
     * Restoring before anyone has signed in: the first screen on a new phone offers the backups it finds. It is
     * the same manager without the permission check, which would refuse a person who has no account yet, and the
     * screen that uses it is only reachable while no business exists on the phone.
     */
    val firstRunRestore: BackupService by lazy {
        BackupManager(appContext, database, appearanceRepository, DocumentsBackupStorage(appContext), clock, rawShopPreferences, productPhotos, backupPassphrase, databaseKeys)
    }

    val restoredSignIn: com.auxo.pos.core.backup.RestoredSignInFlag by lazy { com.auxo.pos.core.backup.RestoredSignInFlag(appContext) }

    /**
     * Called after a backup was restored from the first screen. The restored data already holds the shop's category
     * choices, and the person has been past the introduction, so neither is asked again; only a sign-in is.
     */
    fun afterFirstRunRestore() {
        restoredSignIn.set()
        authRepository.forgetAccountsAfterRestore()
        rawShopPreferences.setCategorySetupDone(true)
        rawShopPreferences.setOnboardingSeen(true)
    }

    val backupScanner: com.auxo.pos.core.backup.BackupScanner by lazy { com.auxo.pos.core.backup.BackupScanner(appContext) }

    val backupService: BackupService by lazy {
        AuthorizedBackupService(
            BackupManager(appContext, database, appearanceRepository, DocumentsBackupStorage(appContext), clock, rawShopPreferences, productPhotos, backupPassphrase, databaseKeys),
            authorizer,
            auditTrail,
        )
    }

    val autoBackupSettings: AutoBackupSettingsStore by lazy { SharedPrefsAutoBackupStore(appContext) }

    val autoBackupScheduler: AutoBackupScheduler by lazy { WorkManagerAutoBackupScheduler(appContext) { autoBackupSettings.settings.value.schedule } }

    /** Makes the scheduled backups. It acts for the app itself, so it needs nobody signed in. */
    val autoBackupRunner: AutoBackupRunner by lazy {
        val system = SessionUser(0L, "Automatic backup", Role.SUPER_ADMIN, emptySet())
        AutoBackupRunner(
            store = autoBackupSettings,
            folder = { DocumentsAutoFolder(appContext) },
            backUpTo = { storage -> BackupManager(appContext, database, appearanceRepository, storage, clock, rawShopPreferences, productPhotos, backupPassphrase, databaseKeys).backUp() },
            clock = clock,
            log = { draft -> auditTrail.record(draft, system) },
            needsPassphrase = { !backupPassphrase.isSet() },
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

    /**
     * Restaurant service (dine-in, tables, kitchen tickets, the quick-add card) is on while a Restaurant category is
     * switched on. This keeps that flag in step with the category list, whoever changes it and whether or not anyone
     * is signed in.
     */
    @Volatile private var restaurantSyncStarted = false

    fun startRestaurantModeSync() {
        if (restaurantSyncStarted) return
        restaurantSyncStarted = true
        background.launch {
            runCatching {
                com.auxo.pos.data.repository.RoomCategoryRepository(database).observeCategories().collect { categories ->
                    val on = com.auxo.pos.domain.category.CategoryRules.restaurantModeOn(categories)
                    if (rawShopPreferences.restaurantMenu.value != on) rawShopPreferences.setRestaurantMenu(on)
                }
            }
        }
    }

    val observeStoreOverview: ObserveStoreOverviewUseCase by lazy {
        ObserveStoreOverviewUseCase(settingsRepository, billingRepository, clock)
    }

    val observeInventorySummary: ObserveInventorySummaryUseCase by lazy {
        ObserveInventorySummaryUseCase(inventoryRepository, clock, settingsRepository)
    }

    val saveProduct: SaveProductUseCase by lazy { SaveProductUseCase(inventoryRepository, barcodeGenerator) }
    val importProducts: com.auxo.pos.domain.usecase.ImportProductsUseCase by lazy { com.auxo.pos.domain.usecase.ImportProductsUseCase(saveProduct) }
    val importFileReader: com.auxo.pos.domain.imports.ImportFileReader by lazy { com.auxo.pos.core.imports.ContentResolverImportReader(appContext) }

    val adjustStock: AdjustStockUseCase by lazy { AdjustStockUseCase(inventoryRepository, clock) }

    val findByBarcode: FindByBarcodeUseCase by lazy { FindByBarcodeUseCase(inventoryRepository) }

    val checkout: CheckoutUseCase by lazy { CheckoutUseCase(billingRepository, settingsRepository, clock, customerRepository) }

    val processReturn: ProcessReturnUseCase by lazy { ProcessReturnUseCase(billingRepository, settingsRepository, clock) }

    val receiveGoods: ReceiveGoodsUseCase by lazy { ReceiveGoodsUseCase(purchasingRepository, clock) }

    val recordWastage: RecordWastageUseCase by lazy { RecordWastageUseCase(inventoryRepository) }

    val receiveAccountPayment: ReceiveAccountPaymentUseCase by lazy { ReceiveAccountPaymentUseCase(customerRepository) }

    val createReorderDrafts: CreateReorderDraftsUseCase by lazy { CreateReorderDraftsUseCase(purchasingRepository) }
}
