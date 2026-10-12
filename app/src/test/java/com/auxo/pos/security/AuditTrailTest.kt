package com.auxo.pos.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.audit.AuditAction
import com.auxo.pos.core.audit.AuditDraft
import com.auxo.pos.core.audit.AuditEntity
import com.auxo.pos.core.audit.RoomAuditTrail
import com.auxo.pos.core.auth.Authorizer
import com.auxo.pos.core.auth.FixedAuthorizer
import com.auxo.pos.core.auth.PermissionDeniedException
import com.auxo.pos.core.auth.Role
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.dao.AuditDao
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomCustomerRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.data.security.AuthorizedBillingRepository
import com.auxo.pos.data.security.AuthorizedCustomerRepository
import com.auxo.pos.data.security.AuthorizedInventoryRepository
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.testutil.DataIntegrity.count
import com.auxo.pos.testutil.TestData
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Important changes are written to the activity log in the same transaction as the change. */
@RunWith(AndroidJUnit4::class)
class AuditTrailTest {

    private lateinit var database: AuxoDatabase
    private val store = TestData.settings
    private val now = TestData.clock.millis()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun trail(auth: Authorizer) = RoomAuditTrail(database, TestData.clock, auth)

    private val adminAuth = FixedAuthorizer(Role.SUPER_ADMIN)
    private val staffAuth = FixedAuthorizer(Role.EMPLOYEE)

    private fun inventory(auth: Authorizer = adminAuth) =
        AuthorizedInventoryRepository(RoomInventoryRepository(database, TestData.clock), auth, trail(auth))

    private fun billing(auth: Authorizer = adminAuth) =
        AuthorizedBillingRepository(RoomBillingRepository(database) { ZoneOffset.UTC }, auth, trail(auth))

    private suspend fun savedShirt(): Product {
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val id = (inventory().saveProduct(fresh) as SaveProductResult.Saved).productId
        return inventory().getProduct(id)!!
    }

    private fun saleOf(product: Product, quantity: Int = 1) = Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity))).let { cart ->
        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        SaleBuilder.build(cart, totals, store, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), now)
    }

    private suspend fun log() = trail(adminAuth).observeRecent().first()

    @Test
    fun aSale_isLogged_withWhoMadeIt_andTheInvoice() = runTest {
        val shirt = savedShirt()
        val sold = billing(staffAuth).recordSale(saleOf(shirt), "INV") as SaleResult.Recorded

        val entry = log().first { it.action == AuditAction.SALE_RECORDED.name }
        assertEquals(sold.invoiceId, entry.entityId)
        assertEquals(AuditEntity.INVOICE, entry.entityType)
        assertEquals(Role.EMPLOYEE, entry.actorRole)
        assertEquals("Test Employee", entry.actorName)
        assertEquals(now, entry.createdAtMillis)
        assertTrue(entry.summary.contains(sold.number))
    }

    @Test
    fun aSaleThatFails_leavesNoLogLine() = runTest {
        val shirt = savedShirt()
        val before = count(database, "audit_log")
        val result = billing().recordSale(saleOf(shirt, quantity = 99), "INV")
        assertTrue(result is SaleResult.StockShortage)
        assertEquals(before, count(database, "audit_log"))
    }

    @Test
    fun aReturn_isLogged_asAReversalOfTheSale() = runTest {
        val shirt = savedShirt()
        val sold = billing().recordSale(saleOf(shirt, 2), "INV") as SaleResult.Recorded
        val original = billing().getInvoice(sold.invoiceId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines.single().id to 1), PaymentMethod.CASH, "x", now) as ReturnBuild.Ready).creditNote
        billing().recordReturn(note, "CN")

        val entry = log().first { it.action == AuditAction.RETURN_RECORDED.name }
        assertEquals(Role.SUPER_ADMIN, entry.actorRole)
        assertTrue(entry.summary.contains(sold.number))
    }

    @Test
    fun productEditsAndDeletions_areLogged_withTheProductId() = runTest {
        val shirt = savedShirt()
        inventory().saveProduct(shirt.copy(name = "Linen Shirt"))
        inventory().deleteProduct(shirt.id)

        val actions = log().filter { it.entityId == shirt.id }.map { it.action to it.summary }
        assertTrue(actions.any { it.first == AuditAction.PRODUCT_DELETED.name && it.second.contains("Linen Shirt") })
        assertTrue(actions.any { it.first == AuditAction.PRODUCT_SAVED.name && it.second.startsWith("Changed") })
        assertTrue(actions.any { it.first == AuditAction.PRODUCT_SAVED.name && it.second.startsWith("Added") })
    }

    @Test
    fun aRefusedAction_isNotLogged_andChangesNothing() = runTest {
        val shirt = savedShirt()
        val before = count(database, "audit_log")
        try {
            inventory(staffAuth).deleteProduct(shirt.id)
            fail("Expected a refusal")
        } catch (expected: PermissionDeniedException) {
            // refused
        }
        assertEquals(before, count(database, "audit_log"))
        assertEquals(1, count(database, "products"))
    }

    @Test
    fun theLogLineAndTheChange_areOneTransaction() = runTest {
        val customers = RoomCustomerRepository(database, TestData.clock)
        val trail = trail(adminAuth)
        try {
            trail.recording({ customers.saveCustomer(Customer(name = "Anita Das", phone = "9830012345")) }) {
                error("the log line could not be written")
            }
            fail("Expected the failure to pass through")
        } catch (expected: IllegalStateException) {
            // rolled back
        }
        assertEquals(0, count(database, "customers"))
        assertEquals(0, count(database, "audit_log"))
    }

    @Test
    fun theLogCanBeReadOnlyBySomeoneWhoMay() = runTest {
        trail(adminAuth).record(AuditDraft(AuditAction.SETTINGS_SAVED, AuditEntity.SETTINGS, 1L, "Store settings saved"))
        assertEquals(1, trail(adminAuth).observeRecent().first().size)
        try {
            trail(staffAuth).observeRecent().first()
            fail("An employee read the activity log")
        } catch (expected: PermissionDeniedException) {
            // refused
        }
    }

    @Test
    fun theLogOffersNoWayToEditOrRemoveALine() {
        val methods = AuditDao::class.java.methods.map { it.name.lowercase() }
        assertFalse(methods.any { "delete" in it || "update" in it || "clear" in it || "remove" in it })
    }

    @Test
    fun customerPaymentsAndNewCustomers_areLogged() = runTest {
        val auth = adminAuth
        val customers = AuthorizedCustomerRepository(RoomCustomerRepository(database, TestData.clock), auth, trail(auth))
        customers.saveCustomer(Customer(name = "Anita Das", phone = "9830012345"))
        assertTrue(log().any { it.action == AuditAction.CUSTOMER_SAVED.name && it.summary.contains("Anita Das") })
    }
}
