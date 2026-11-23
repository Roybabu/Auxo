// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.AccountPaymentResult
import com.auxo.pos.data.repository.ReturnResult
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomCustomerRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SaveCustomerResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.customers.Ledger
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** Customer accounts: balances, sales on account, payments and returns, against the real Room database. */
@RunWith(AndroidJUnit4::class)
class RoomCustomerLedgerTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private lateinit var customers: RoomCustomerRepository
    private lateinit var shirt: Product

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        customers = RoomCustomerRepository(database, TestData.clock)
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        shirt = inventory.getProduct((inventory.saveProduct(fresh) as SaveProductResult.Saved).productId)!!
    }

    @After
    fun tearDown() = database.close()

    private suspend fun newCustomer(limit: Money? = null, opening: Money = Money.ZERO, phone: String? = "9830012345"): Customer {
        val id = (customers.saveCustomer(Customer(name = "Anita Das", phone = phone, creditLimit = limit), opening) as SaveCustomerResult.Saved).customerId
        return customers.getCustomer(id)!!
    }

    /** Two shirts (₹998) for [customer]: [cash] paid now, the rest on account. */
    private fun saleOnAccount(customer: Customer, cash: Money, quantity: Int = 2): Invoice {
        val cart = Cart(
            lines = listOf(CartLine.from(shirt, shirt.variants[0], quantity = quantity)),
            customer = CustomerDetails(name = customer.name, phone = customer.phone.orEmpty(), customerId = customer.id),
        )
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val payments = listOfNotNull(
            PaymentRecord(method = PaymentMethod.CASH, amount = cash).takeIf { !cash.isZero },
            PaymentRecord(method = PaymentMethod.CREDIT, amount = totals.grandTotal - cash),
        )
        return SaleBuilder.build(cart, totals, TestData.settings, payments, TestData.clock.millis())
    }

    private suspend fun balance(customer: Customer): Money = customers.observeAccount(customer.id).first()!!.balance

    private suspend fun stock(): Int = inventory.getProduct(shirt.id)!!.variants[0].stockQuantity

    @Test
    fun newCustomer_withOpeningBalance_owesIt() = runTest {
        val anita = newCustomer(opening = Money(15_000))
        assertEquals(Money(15_000), balance(anita))
        assertEquals(LedgerEntryType.OPENING_BALANCE, customers.observeLedger(anita.id).first().single().type)
        // Editing doesn't add another opening entry.
        customers.saveCustomer(anita.copy(name = "Anita D."), Money(99_999))
        assertEquals(Money(15_000), balance(anita))
        assertEquals("Anita D.", customers.observeAccounts().first().single().customer.name)
    }

    @Test
    fun aMobileNumber_belongsToOneCustomer() = runTest {
        newCustomer()
        assertEquals(SaveCustomerResult.PhoneInUse, customers.saveCustomer(Customer(name = "Someone else", phone = "9830012345")))
        assertTrue(customers.saveCustomer(Customer(name = "No phone", phone = null)) is SaveCustomerResult.Saved)
        assertTrue(customers.saveCustomer(Customer(name = "Also no phone", phone = null)) is SaveCustomerResult.Saved)
        assertEquals(3, customers.observeAccounts().first().size)
    }

    @Test
    fun saleOnAccount_addsTheCreditPart_andRespectsTheLimit() = runTest {
        val anita = newCustomer(limit = Money(100_000))
        val sale = saleOnAccount(anita, cash = Money(20_000)) // ₹998 bill, ₹200 cash, ₹798 on account
        val recorded = billing.recordSale(sale, "INV") as SaleResult.Recorded
        assertEquals(Money(79_800), balance(anita))
        val entry = customers.observeLedger(anita.id).first().single()
        assertEquals(LedgerEntryType.SALE, entry.type)
        assertEquals(recorded.number, entry.invoiceNumber)
        assertEquals(anita.id, billing.getInvoice(recorded.invoiceId)!!.customerId)
        assertEquals(listOf(recorded.invoiceId), customers.observeInvoices(anita.id).first().map { it.id })

        // ₹798 + ₹499 would pass the ₹1,000 limit: nothing is saved.
        val over = billing.recordSale(saleOnAccount(anita, cash = Money.ZERO, quantity = 1), "INV")
        assertEquals(SaleResult.CreditLimitExceeded, over)
        assertEquals(Money(79_800), balance(anita))
        assertEquals(6, stock())
        assertEquals(1, billing.observeInvoices(0, Long.MAX_VALUE).first().size)
    }

    @Test
    fun saleOnAccount_forAMissingCustomer_savesNothing() = runTest {
        val ghost = Customer(id = 404, name = "Ghost")
        assertEquals(SaleResult.CustomerMissing, billing.recordSale(saleOnAccount(ghost, Money.ZERO), "INV"))
        assertEquals(8, stock())
    }

    @Test
    fun payments_reduceTheBalance_neverBelowZero_andCountOnce() = runTest {
        val anita = newCustomer()
        billing.recordSale(saleOnAccount(anita, cash = Money.ZERO), "INV") // ₹998 on account

        assertEquals(AccountPaymentResult.MoreThanDue(Money(99_800)), customers.receivePayment(anita.id, Money(99_801), PaymentMethod.CASH, null, null, "p0"))
        assertTrue(customers.receivePayment(anita.id, Money(50_000), PaymentMethod.UPI, "UTR123", "Part payment", "p1") is AccountPaymentResult.Recorded)
        assertTrue(customers.receivePayment(anita.id, Money(50_000), PaymentMethod.UPI, "UTR123", "Part payment", "p1") is AccountPaymentResult.Recorded)
        assertEquals(Money(49_800), balance(anita))
        assertEquals(AccountPaymentResult.CustomerMissing, customers.receivePayment(404, Money(1), PaymentMethod.CASH, null, null, "p2"))

        val payments = customers.observeAccountPayments(0, Long.MAX_VALUE).first()
        assertEquals(Money(50_000), payments.single().amount)
        assertEquals("Anita Das", payments.single().customerName)
        assertEquals(PaymentMethod.UPI, payments.single().method)

        // The statement's running balance ends at the account balance.
        val statement = Ledger.statement(customers.observeLedger(anita.id).first())
        assertEquals(listOf(99_800L, 49_800L), statement.map { it.balanceAfter.minorUnits })
        assertEquals(balance(anita), statement.last().balanceAfter)
    }

    @Test
    fun refundToAccount_reducesWhatTheCustomerOwes() = runTest {
        val anita = newCustomer()
        val saleId = (billing.recordSale(saleOnAccount(anita, cash = Money.ZERO), "INV") as SaleResult.Recorded).invoiceId
        val original = billing.getInvoice(saleId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines.single().id to 1), PaymentMethod.CREDIT, "Size", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        val credit = note.payments.single().amount
        assertTrue(billing.recordReturn(note, "CN") is ReturnResult.Recorded)

        assertEquals(Money(99_800) - credit, balance(anita))
        val entries = customers.observeLedger(anita.id).first()
        assertEquals(listOf(LedgerEntryType.SALE, LedgerEntryType.RETURN), entries.map { it.type })
        assertEquals(Money(-credit.minorUnits), entries.last().amount)
        assertEquals(7, stock())
        // A walk-in sale can't be refunded to an account.
        val walkIn = (billing.recordSale(
            SaleBuilder.build(
                Cart(lines = listOf(CartLine.from(shirt, shirt.variants[0]))),
                BillCalculator.calculate(Cart(lines = listOf(CartLine.from(shirt, shirt.variants[0]))), TestData.settings.taxModeFor(Cart())),
                TestData.settings,
                listOf(PaymentRecord(method = PaymentMethod.CASH, amount = Money(49_900))),
                TestData.clock.millis(),
            ),
            "INV",
        ) as SaleResult.Recorded).invoiceId
        val walkInSale = billing.getInvoice(walkIn)!!
        assertTrue(ReturnBuilder.build(walkInSale, mapOf(walkInSale.lines.single().id to 1), PaymentMethod.CREDIT, "", 0) is ReturnBuild.Rejected)
    }

    private suspend fun pointsOf(customer: Customer): Int =
        customers.observeLedger(customer.id).first().filter { it.type == LedgerEntryType.POINTS_EARNED }.sumOf { it.points }

    @Test
    fun clearingWhatIsOwed_onTime_earnsTheBonus_whenLoyaltyIsOn() = runTest {
        database.customerDao().upsertLoyalty(com.auxo.pos.data.local.entity.LoyaltySettingsEntity(enabled = true, spendPerPointMinor = 10_000, pointValueMinor = 100))
        val repo = RoomCustomerRepository(database, TestData.clock, onTimeBonus = { 25 })
        val anita = newCustomer(opening = Money(10_000))
        // A part payment leaves something owing: no bonus yet.
        repo.receivePayment(anita.id, Money(4_000), PaymentMethod.CASH, null, null, "a")
        assertEquals(0, pointsOf(anita))
        repo.receivePayment(anita.id, Money(6_000), PaymentMethod.CASH, null, null, "b")
        assertEquals(25, pointsOf(anita))
        assertEquals(Money.ZERO, balance(anita))
    }

    @Test
    fun clearingWhatIsOwed_toolate_orWithLoyaltyOff_earnsNothing() = runTest {
        val late = java.time.Clock.offset(TestData.clock, java.time.Duration.ofDays(40))
        val anita = newCustomer(opening = Money(10_000))
        // Loyalty is off.
        RoomCustomerRepository(database, TestData.clock, onTimeBonus = { 25 })
            .receivePayment(anita.id, Money(10_000), PaymentMethod.CASH, null, null, "off")
        assertEquals(0, pointsOf(anita))

        database.customerDao().upsertLoyalty(com.auxo.pos.data.local.entity.LoyaltySettingsEntity(enabled = true, spendPerPointMinor = 10_000, pointValueMinor = 100))
        val bala = (customers.saveCustomer(Customer(name = "Bala", phone = "9830099999"), Money(10_000)) as SaveCustomerResult.Saved).customerId
        RoomCustomerRepository(database, late, onTimeBonus = { 25 })
            .receivePayment(bala, Money(10_000), PaymentMethod.CASH, null, null, "late")
        assertEquals(0, customers.observeLedger(bala).first().sumOf { it.points })
    }

    @Test
    fun aPromisedDate_isRemembered_changesNothingElse_andCanBeCleared() = runTest {
        val anita = newCustomer(opening = Money(10_000))
        val date = java.time.LocalDate.of(2026, 12, 5)
        customers.setPromise(anita.id, date)
        assertEquals(date, customers.observeAccounts().first().single().promisedOn)
        assertEquals(Money(10_000), balance(anita))
        // It is not a money entry, so the statement and the owing-since date ignore it.
        assertEquals(listOf(LedgerEntryType.OPENING_BALANCE), Ledger.statement(customers.observeLedger(anita.id).first()).map { it.entry.type })
        assertEquals(date, Ledger.promisedOn(customers.observeLedger(anita.id).first()))

        customers.setPromise(anita.id, date.plusDays(3))
        assertEquals(date.plusDays(3), customers.observeAccounts().first().single().promisedOn)
        customers.setPromise(anita.id, null)
        assertEquals(null, customers.observeAccounts().first().single().promisedOn)
    }
}
