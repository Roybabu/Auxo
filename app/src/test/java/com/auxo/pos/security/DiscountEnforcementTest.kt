// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.core.audit.AuditAction
import com.auxo.pos.core.audit.RoomAuditTrail
import com.auxo.pos.core.auth.Authorizer
import com.auxo.pos.core.auth.FixedAuthorizer
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.PermissionDeniedException
import com.auxo.pos.core.auth.Role
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.RoomSettingsRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.data.security.AuthorizedBillingRepository
import com.auxo.pos.data.security.AuthorizedInventoryRepository
import com.auxo.pos.data.security.AuthorizedSettingsRepository
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Money
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Who may discount how much is decided by the data layer, whatever the screen allowed. */
@RunWith(AndroidJUnit4::class)
class DiscountEnforcementTest {

    private lateinit var database: AuxoDatabase
    private val store = TestData.settings
    private val now = TestData.clock.millis()
    private val admin = FixedAuthorizer(Role.SUPER_ADMIN)
    private val employee = FixedAuthorizer(Role.EMPLOYEE)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun trail(auth: Authorizer) = RoomAuditTrail(database, TestData.clock, auth)

    private fun billing(auth: Authorizer, limitBp: Int? = null) = AuthorizedBillingRepository(
        RoomBillingRepository(database) { ZoneOffset.UTC }, auth, trail(auth),
        employeeDiscountLimit = { limitBp ?: RoomSettingsRepository(database).getSettings().employeeDiscountLimitBp },
    )

    private suspend fun savedShirt(): Product {
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val inventory = AuthorizedInventoryRepository(RoomInventoryRepository(database, TestData.clock), admin, trail(admin))
        val id = (inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        return inventory.getProduct(id)!!
    }

    /** A sale of one shirt with [percentBp] off the line and a reason. */
    private fun discountedSale(product: Product, percentBp: Int, billReason: String? = null) = Cart(
        lines = listOf(CartLine.from(product, product.variants[0], 1).copy(discount = Discount.Percent(percentBp), discountReason = "Festival offer")),
        billDiscount = if (billReason != null) Discount.Amount(Money(1_00)) else null,
        billDiscountReason = billReason.orEmpty(),
    ).let { cart ->
        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        SaleBuilder.build(cart, totals, store, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), now)
    }

    private suspend fun denied(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected the discount to be refused")
        } catch (e: PermissionDeniedException) {
            assertEquals(Permission.UNLIMITED_DISCOUNT, e.permission)
        }
    }

    @Test
    fun anEmployee_cannotDiscount_untilTheSuperAdminSetsALimit() = runTest {
        val shirt = savedShirt()
        val before = count(database, "invoices")
        denied { billing(employee).recordSale(discountedSale(shirt, 100), "INV") }
        assertEquals(before, count(database, "invoices"))
        assertEquals(0, RoomSettingsRepository(database).getSettings().employeeDiscountLimitBp)
    }

    @Test
    fun anEmployee_canDiscountUpToTheLimit_notAboveIt() = runTest {
        val shirt = savedShirt()
        assertTrue(billing(employee, limitBp = 1_000).recordSale(discountedSale(shirt, 1_000), "INV") is SaleResult.Recorded)
        denied { billing(employee, limitBp = 1_000).recordSale(discountedSale(shirt, 1_001), "INV") }
        assertEquals(1, count(database, "invoices"))
    }

    @Test
    fun theSuperAdmin_isNotLimited() = runTest {
        val shirt = savedShirt()
        assertTrue(billing(admin, limitBp = 0).recordSale(discountedSale(shirt, 5_000), "INV") is SaleResult.Recorded)
    }

    @Test
    fun aSaleWithoutDiscount_isNeverBlockedByTheLimit() = runTest {
        val shirt = savedShirt()
        val cart = Cart(lines = listOf(CartLine.from(shirt, shirt.variants[0], 1)))
        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        val sale = SaleBuilder.build(cart, totals, store, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), now)
        assertTrue(billing(employee, limitBp = 0).recordSale(sale, "INV") is SaleResult.Recorded)
    }

    @Test
    fun reasonsAreSaved_andTheActivityLogShowsTheDiscount() = runTest {
        val shirt = savedShirt()
        val repo = billing(admin)
        val sold = repo.recordSale(discountedSale(shirt, 1_000, billReason = "Regular customer"), "INV") as SaleResult.Recorded

        val invoice = repo.getInvoice(sold.invoiceId)!!
        assertEquals("Festival offer", invoice.lines.single().discountReason)
        assertEquals("Regular customer", invoice.discountReason)

        val entry = trail(admin).observeRecent().first().first { it.action == AuditAction.SALE_RECORDED.name }
        assertTrue(entry.summary, entry.summary.contains("discount"))
        assertTrue(entry.summary, entry.summary.contains("Festival offer"))
        assertTrue(entry.summary, entry.summary.contains("Regular customer"))
    }

    @Test
    fun aSaleWithoutDiscount_hasNoReasons() = runTest {
        val shirt = savedShirt()
        val cart = Cart(lines = listOf(CartLine.from(shirt, shirt.variants[0], 1)))
        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        val sale = SaleBuilder.build(cart, totals, store, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), now)
        val sold = billing(admin).recordSale(sale, "INV") as SaleResult.Recorded
        val invoice = billing(admin).getInvoice(sold.invoiceId)!!
        assertNull(invoice.discountReason)
        assertNull(invoice.lines.single().discountReason)
        val entry = trail(admin).observeRecent().first().first { it.action == AuditAction.SALE_RECORDED.name }
        assertTrue(entry.summary, !entry.summary.contains("discount"))
    }

    @Test
    fun onlyTheSuperAdmin_canChangeTheLimit_evenWithSettingsAccess() = runTest {
        val manager = FixedAuthorizer(Role.EMPLOYEE, setOf(Permission.MANAGE_SETTINGS))
        val managerSettings = AuthorizedSettingsRepository(RoomSettingsRepository(database), manager, trail(manager))
        val adminSettings = AuthorizedSettingsRepository(RoomSettingsRepository(database), admin, trail(admin))

        adminSettings.saveSettings(store.copy(employeeDiscountLimitBp = 500))
        assertEquals(500, adminSettings.getSettings().employeeDiscountLimitBp)

        // Saving other changes keeps working for the manager...
        managerSettings.saveSettings(store.copy(storeName = "Sample Mart 2", employeeDiscountLimitBp = 500))
        assertEquals("Sample Mart 2", adminSettings.getSettings().storeName)
        // ...but not raising the limit.
        try {
            managerSettings.saveSettings(store.copy(employeeDiscountLimitBp = 10_000))
            fail("Expected the limit change to be refused")
        } catch (e: PermissionDeniedException) {
            assertEquals(Permission.UNLIMITED_DISCOUNT, e.permission)
        }
        assertEquals(500, adminSettings.getSettings().employeeDiscountLimitBp)

        val entry = trail(admin).observeRecent().first().first { it.action == AuditAction.SETTINGS_SAVED.name && it.summary.contains("discount limit") }
        assertTrue(entry.summary, entry.summary.contains("5.0%"))
    }
}
