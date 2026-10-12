package com.invision.pos.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.core.audit.RoomAuditTrail
import com.invision.pos.core.auth.Authorizer
import com.invision.pos.core.auth.FixedAuthorizer
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.PermissionDeniedException
import com.invision.pos.core.auth.Role
import com.invision.pos.core.auth.RolePermissions
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
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.data.repository.SaveCustomerResult
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.data.repository.SettingsRepository
import com.invision.pos.data.security.AuthorizedBillingRepository
import com.invision.pos.data.security.AuthorizedCustomerRepository
import com.invision.pos.data.security.AuthorizedInventoryRepository
import com.invision.pos.data.security.AuthorizedMenuRepository
import com.invision.pos.data.security.AuthorizedPurchasingRepository
import com.invision.pos.data.security.AuthorizedSettingsRepository
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.PurchaseOrder
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.model.Supplier
import com.invision.pos.testutil.DataIntegrity.count
import com.invision.pos.testutil.TestData
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The data layer refuses restricted work for the wrong role, on the real Room database, whatever screen
 * or view model asked. Denied calls must leave every table exactly as it was.
 */
@RunWith(AndroidJUnit4::class)
class PermissionEnforcementTest {

    private lateinit var database: InvisionDatabase
    private val now = TestData.clock.millis()
    private val store = TestData.settings

    private class Repos(
        val billing: BillingRepository,
        val settings: SettingsRepository,
        val inventory: InventoryRepository,
        val customers: CustomerRepository,
        val purchasing: PurchasingRepository,
        val menu: MenuRepository,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun trailFor(auth: Authorizer) = RoomAuditTrail(database, TestData.clock, auth)

    private fun reposFor(auth: Authorizer): Repos {
        val trail = trailFor(auth)
        return Repos(
            AuthorizedBillingRepository(RoomBillingRepository(database) { ZoneOffset.UTC }, auth, trail),
            AuthorizedSettingsRepository(RoomSettingsRepository(database), auth, trail),
            AuthorizedInventoryRepository(RoomInventoryRepository(database, TestData.clock), auth, trail),
            AuthorizedCustomerRepository(RoomCustomerRepository(database, TestData.clock), auth, trail),
            AuthorizedPurchasingRepository(RoomPurchasingRepository(database, TestData.clock) { ZoneOffset.UTC }, auth, trail),
            AuthorizedMenuRepository(RoomMenuRepository(database, TestData.clock), auth, trail),
        )
    }

    private val admin get() = reposFor(FixedAuthorizer(Role.SUPER_ADMIN))
    private val employee get() = reposFor(FixedAuthorizer(Role.EMPLOYEE))
    private val signedOut get() = reposFor(FixedAuthorizer(null))

    private val tables = listOf(
        "audit_log", "products", "product_variants", "stock_movements", "stock_batches", "invoices", "invoice_lines", "payments",
        "store_settings", "number_sequences", "suppliers", "purchase_orders", "purchase_receipts", "customers",
        "customer_ledger", "menu_items", "applied_requests",
    )

    private fun snapshot() = tables.associateWith { count(database, it) }

    private suspend fun savedShirt(): Product {
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val id = (admin.inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        return admin.inventory.getProduct(id)!!
    }

    private fun saleOf(product: Product, quantity: Int = 1): Invoice {
        val cart = Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity)))
        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        return SaleBuilder.build(cart, totals, store, listOf(PaymentRecord(method = PaymentMethod.UPI, amount = totals.grandTotal)), now)
    }

    /** Runs [block] and requires it to be refused, naming the permission it was refused for. */
    private suspend fun denied(permission: Permission, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $permission to be required")
        } catch (e: PermissionDeniedException) {
            assertEquals(permission, e.permission)
        }
    }

    // The policy itself

    @Test
    fun anEmployeeMayOnlySellAndReadInvoices() {
        assertEquals(setOf(Permission.SELL, Permission.VIEW_INVOICES), RolePermissions.of(Role.EMPLOYEE))
    }

    @Test
    fun theSuperAdminHasEveryPermission_andOnlyTheySeeUserManagement() {
        assertEquals(Permission.entries.toSet(), RolePermissions.of(Role.SUPER_ADMIN))
        assertTrue(RolePermissions.allows(Role.SUPER_ADMIN, Permission.MANAGE_USERS))
        assertFalse(RolePermissions.allows(Role.EMPLOYEE, Permission.MANAGE_USERS))
    }

    // Employees can do the billing job

    @Test
    fun anEmployee_canSell_andOpenTheInvoiceItMade() = runTest {
        val shirt = savedShirt()
        val sold = employee.billing.recordSale(saleOf(shirt), "INV") as SaleResult.Recorded
        val invoice = employee.billing.getInvoice(sold.invoiceId)
        assertNotNull(invoice)
        assertEquals(PaymentMethod.UPI, invoice!!.payments.single().method)
        assertEquals(invoice.grandTotal, invoice.payments.single().amount)
        assertEquals(1, employee.billing.observeInvoices(0, Long.MAX_VALUE).first().size)
        assertTrue(employee.billing.observeCreditNotesFor(sold.invoiceId).first().isEmpty())
    }

    @Test
    fun anEmployee_canSearchProductsAndMenu_andReadSettingsForBilling() = runTest {
        val shirt = savedShirt()
        admin.menu.saveItem(MenuItem(name = "Masala Dosa", section = "Mains", price = Money(9_000)))
        assertEquals(1, employee.inventory.observeProducts().first().size)
        assertNotNull(employee.inventory.getProduct(shirt.id))
        assertNotNull(employee.inventory.findByBarcode(shirt.variants[0].barcode))
        assertEquals(1, employee.menu.observeMenu().first().size)
        employee.settings.getSettings()
        employee.settings.observeSettings().first()
    }

    @Test
    fun anEmployee_canAddANewCustomerFromTheBill_butNotEditOne() = runTest {
        val id = (employee.customers.saveCustomer(Customer(name = "Anita Das", phone = "9830012345")) as SaveCustomerResult.Saved).customerId
        val saved = employee.customers.getCustomer(id)!!
        assertEquals(1, employee.customers.observeAccounts().first().size)
        denied(Permission.MANAGE_CUSTOMERS) { employee.customers.saveCustomer(saved.copy(name = "Renamed")) }
        denied(Permission.MANAGE_CUSTOMERS) { employee.customers.saveCustomer(Customer(name = "Owes", phone = "9830012346"), Money(50_000)) }
        assertEquals("Anita Das", admin.customers.getCustomer(id)!!.name)
    }

    // Employees are kept out of everything else, and nothing changes

    @Test
    fun anEmployee_cannotReadBusinessWideFigures() = runTest {
        val shirt = savedShirt()
        employee.billing.recordSale(saleOf(shirt), "INV")
        denied(Permission.VIEW_FINANCIALS) { employee.billing.observeSalesTotals(0, Long.MAX_VALUE).first() }
        denied(Permission.VIEW_FINANCIALS) { employee.billing.observePayments(0, Long.MAX_VALUE).first() }
        denied(Permission.VIEW_FINANCIALS) { employee.customers.observeAccountPayments(0, Long.MAX_VALUE).first() }
        denied(Permission.VIEW_REPORTS) { employee.billing.invoicesBetween(0, Long.MAX_VALUE) }
        // The Super Admin reads the same data fine.
        assertEquals(1, admin.billing.observeSalesTotals(0, Long.MAX_VALUE).first().billCount)
    }

    @Test
    fun anEmployee_cannotReturnOrRefundASale() = runTest {
        val shirt = savedShirt()
        val sold = admin.billing.recordSale(saleOf(shirt, 2), "INV") as SaleResult.Recorded
        val original = admin.billing.getInvoice(sold.invoiceId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines.single().id to 1), PaymentMethod.CASH, "x", now) as ReturnBuild.Ready).creditNote
        val before = snapshot()
        denied(Permission.PROCESS_RETURN) { employee.billing.recordReturn(note, "CN") }
        assertEquals(before, snapshot())
        assertEquals(0, admin.billing.getInvoice(sold.invoiceId)!!.lines.single().returnedQuantity)
    }

    @Test
    fun anEmployee_cannotChangeProductsStockPricesOrBarcodes() = runTest {
        val shirt = savedShirt()
        val before = snapshot()
        val repriced = shirt.copy(variants = shirt.variants.map { it.copy(sellingPrice = it.sellingPrice + Money(100)) })
        denied(Permission.MANAGE_PRODUCTS) { employee.inventory.saveProduct(repriced) }
        denied(Permission.MANAGE_PRODUCTS) { employee.inventory.deleteProduct(shirt.id) }
        denied(Permission.MANAGE_PRODUCTS) { employee.inventory.observeProduct(shirt.id).first() }
        denied(Permission.MANAGE_PRODUCTS) { employee.inventory.isBarcodeInUse("x") }
        val movement = StockMovement(variantId = shirt.variants[0].id, quantityChange = 5, reason = StockChangeReason.RECEIVED, createdAtMillis = now)
        denied(Permission.ADJUST_STOCK) { employee.inventory.recordStockChange(movement) }
        denied(Permission.ADJUST_STOCK) { employee.inventory.writeOff(1, 1, StockChangeReason.DAMAGED, null, "r1") }
        denied(Permission.VIEW_STOCK_RECORDS) { employee.inventory.observeMovementHistory(0, Long.MAX_VALUE).first() }
        denied(Permission.VIEW_STOCK_RECORDS) { employee.inventory.observeStockMovements(shirt.id).first() }
        denied(Permission.VIEW_STOCK_RECORDS) { employee.inventory.observeBatches().first() }
        assertEquals(before, snapshot())
        assertEquals(shirt.variants[0].sellingPrice, admin.inventory.getProduct(shirt.id)!!.variants[0].sellingPrice)
    }

    @Test
    fun anEmployee_cannotManageSuppliersPurchasesOrReceiveStock() = runTest {
        val before = snapshot()
        denied(Permission.MANAGE_SUPPLIERS) { employee.purchasing.saveSupplier(Supplier(name = "Acme")) }
        denied(Permission.MANAGE_SUPPLIERS) { employee.purchasing.observeSuppliers().first() }
        denied(Permission.MANAGE_PURCHASES) { employee.purchasing.observeOrders().first() }
        denied(Permission.MANAGE_PURCHASES) { employee.purchasing.createDraftOrders(emptyList()) }
        denied(Permission.MANAGE_PURCHASES) { employee.purchasing.setOrderStatus(1, com.invision.pos.domain.model.PurchaseOrderStatus.CANCELLED) }
        denied(Permission.MANAGE_PURCHASES) { employee.purchasing.saveDraftOrder(PurchaseOrder(supplierId = 1)) }
        assertEquals(before, snapshot())
    }

    @Test
    fun anEmployee_cannotChangeTheMenuSettingsOrAccountBalances() = runTest {
        val dish = admin.menu.saveItem(MenuItem(name = "Masala Dosa", section = "Mains", price = Money(9_000)))
        val customerId = (admin.customers.saveCustomer(Customer(name = "Anita Das", phone = "9830012345")) as SaveCustomerResult.Saved).customerId
        val before = snapshot()
        denied(Permission.MANAGE_MENU) { employee.menu.saveItem(MenuItem(name = "New", section = "Mains", price = Money(100))) }
        denied(Permission.MANAGE_MENU) { employee.menu.setAvailable(dish, false) }
        denied(Permission.MANAGE_MENU) { employee.menu.deleteItem(dish) }
        denied(Permission.MANAGE_SETTINGS) { employee.settings.saveSettings(store.copy(storeName = "Hijacked")) }
        denied(Permission.RECEIVE_ACCOUNT_PAYMENT) { employee.customers.receivePayment(customerId, Money(100), PaymentMethod.CASH, null, null, "p1") }
        denied(Permission.MANAGE_CUSTOMERS) { employee.customers.observeLedger(customerId).first() }
        assertEquals(before, snapshot())
        assertEquals(1, admin.menu.observeMenu().first().size)
    }

    // The Super Admin can do all of it

    @Test
    fun theSuperAdmin_canUseEveryModule() = runTest {
        val shirt = savedShirt()
        val sold = admin.billing.recordSale(saleOf(shirt, 2), "INV") as SaleResult.Recorded
        val original = admin.billing.getInvoice(sold.invoiceId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines.single().id to 1), PaymentMethod.CASH, "x", now) as ReturnBuild.Ready).creditNote
        admin.billing.recordReturn(note, "CN")
        admin.billing.observePayments(0, Long.MAX_VALUE).first()
        admin.billing.invoicesBetween(0, Long.MAX_VALUE)
        admin.settings.saveSettings(store.copy(storeName = "Renamed"))
        assertEquals("Renamed", admin.settings.getSettings().storeName)
        val movement = StockMovement(variantId = shirt.variants[0].id, quantityChange = 5, reason = StockChangeReason.RECEIVED, createdAtMillis = now)
        admin.inventory.recordStockChange(movement)
        admin.inventory.observeMovementHistory(0, Long.MAX_VALUE).first()
        admin.purchasing.saveSupplier(Supplier(name = "Acme"))
        assertEquals(1, admin.purchasing.observeSuppliers().first().size)
        val dish = admin.menu.saveItem(MenuItem(name = "Masala Dosa", section = "Mains", price = Money(9_000)))
        admin.menu.setAvailable(dish, false)
        admin.menu.deleteItem(dish)
    }

    // Nobody signed in

    @Test
    fun withNobodySignedIn_everythingIsRefused() = runTest {
        val shirt = savedShirt()
        denied(Permission.SELL) { signedOut.billing.recordSale(saleOf(shirt), "INV") }
        denied(Permission.VIEW_INVOICES) { signedOut.billing.getInvoice(1) }
        denied(Permission.SELL) { signedOut.inventory.observeProducts().first() }
        denied(Permission.SELL) { signedOut.settings.getSettings() }
        assertEquals(0, count(database, "invoices"))
    }

    // Employees with extras

    private fun employeeWith(vararg extras: Permission) = reposFor(FixedAuthorizer(Role.EMPLOYEE, extras.toSet()))

    @Test
    fun anEmployee_givenProducts_canEditThem_butStillNothingElse() = runTest {
        val shirt = savedShirt()
        val staff = employeeWith(Permission.MANAGE_PRODUCTS)
        staff.inventory.saveProduct(shirt.copy(name = "Renamed shirt"))
        assertEquals("Renamed shirt", admin.inventory.getProduct(shirt.id)!!.name)
        val movement = StockMovement(variantId = shirt.variants[0].id, quantityChange = 5, reason = StockChangeReason.RECEIVED, createdAtMillis = now)
        denied(Permission.ADJUST_STOCK) { staff.inventory.recordStockChange(movement) }
        denied(Permission.MANAGE_SUPPLIERS) { staff.purchasing.saveSupplier(Supplier(name = "Acme")) }
        denied(Permission.MANAGE_MENU) { staff.menu.saveItem(MenuItem(name = "Dosa", section = "Mains", price = Money(9_000))) }
        denied(Permission.VIEW_FINANCIALS) { staff.billing.observeSalesTotals(0, Long.MAX_VALUE).first() }
    }

    @Test
    fun anEmployee_givenTheMenuAndSuppliers_canUseJustThose() = runTest {
        val staff = employeeWith(Permission.MANAGE_MENU, Permission.MANAGE_SUPPLIERS)
        staff.menu.saveItem(MenuItem(name = "Masala Dosa", section = "Mains", price = Money(9_000)))
        staff.purchasing.saveSupplier(Supplier(name = "Acme"))
        assertEquals(1, admin.menu.observeMenu().first().size)
        assertEquals(1, staff.purchasing.observeSuppliers().first().size)
        denied(Permission.MANAGE_PURCHASES) { staff.purchasing.observeOrders().first() }
        denied(Permission.MANAGE_PRODUCTS) { staff.inventory.deleteProduct(savedShirt().id) }
    }

    @Test
    fun anEmployee_givenFigures_canSeeSalesTotals_butNotChangeAnyRecord() = runTest {
        val shirt = savedShirt()
        val sold = admin.billing.recordSale(saleOf(shirt), "INV") as SaleResult.Recorded
        val staff = employeeWith(Permission.VIEW_FINANCIALS)
        assertEquals(1, staff.billing.observePayments(0, Long.MAX_VALUE).first().size)
        staff.billing.observeSalesTotals(0, Long.MAX_VALUE).first()
        val original = admin.billing.getInvoice(sold.invoiceId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines.single().id to 1), PaymentMethod.CASH, "x", now) as ReturnBuild.Ready).creditNote
        denied(Permission.PROCESS_RETURN) { staff.billing.recordReturn(note, "CN") }
    }

    @Test
    fun noExtraEverGivesAnEmployeeUserManagementOrBackup() {
        val everything = FixedAuthorizer(Role.EMPLOYEE, Permission.entries.toSet())
        assertFalse(everything.can(Permission.MANAGE_USERS))
        assertFalse(everything.can(Permission.BACKUP_AND_RESTORE))
        assertTrue(everything.can(Permission.RECEIVE_GOODS))
        assertFalse(everything.can(Permission.GST_REPORT))
        assertFalse(everything.can(Permission.MANAGE_LOYALTY))
        assertFalse(everything.can(Permission.UNLIMITED_DISCOUNT))
        assertEquals(setOf(Permission.MANAGE_USERS, Permission.BACKUP_AND_RESTORE, Permission.GST_REPORT, Permission.MANAGE_LOYALTY, Permission.UNLIMITED_DISCOUNT), Permission.entries.filterNot { everything.can(it) }.toSet())
    }

    // Held bills belong to billing

    @Test
    fun heldBills_needTheSellPermission() = runTest {
        val shirt = savedShirt()
        val cart = Cart(lines = listOf(CartLine.from(shirt, shirt.variants[0])))
        val room = com.invision.pos.data.repository.RoomHeldBillRepository(database, TestData.clock)
        val staff = com.invision.pos.data.security.AuthorizedHeldBillRepository(room, FixedAuthorizer(Role.EMPLOYEE))
        val id = staff.hold(cart, Money(499_00), "Asha")
        assertEquals("Asha", staff.observeHeld().first().single().label)
        assertNotNull(staff.get(id))
        staff.discard(id)

        val nobody = com.invision.pos.data.security.AuthorizedHeldBillRepository(room, FixedAuthorizer(null))
        denied(Permission.SELL) { nobody.hold(cart, Money(499_00), "x") }
        denied(Permission.SELL) { nobody.get(1) }
        denied(Permission.SELL) { nobody.discard(1) }
        denied(Permission.SELL) { nobody.observeHeld().first() }
    }

    // Counting sales for "back up every N invoices"

    @Test
    fun aRecordedSale_isCountedOnce_andARefusedOneNotAtAll() = runTest {
        val shirt = savedShirt()
        var counted = 0
        val staff = FixedAuthorizer(Role.EMPLOYEE)
        val billing = AuthorizedBillingRepository(RoomBillingRepository(database) { ZoneOffset.UTC }, staff, trailFor(staff), onSaleRecorded = { counted++ })
        billing.recordSale(saleOf(shirt), "INV")
        assertEquals(1, counted)

        val nobody = FixedAuthorizer(null)
        val locked = AuthorizedBillingRepository(RoomBillingRepository(database) { ZoneOffset.UTC }, nobody, trailFor(nobody), onSaleRecorded = { counted++ })
        denied(Permission.SELL) { locked.recordSale(saleOf(shirt), "INV") }
        assertEquals(1, counted)
    }

    @Test
    fun aCounterThatFails_neverStopsASaleBeingRecorded() = runTest {
        val shirt = savedShirt()
        val staff = FixedAuthorizer(Role.EMPLOYEE)
        val billing = AuthorizedBillingRepository(RoomBillingRepository(database) { ZoneOffset.UTC }, staff, trailFor(staff), onSaleRecorded = { error("counter broke") })
        assertTrue(billing.recordSale(saleOf(shirt), "INV") is SaleResult.Recorded)
    }
}
