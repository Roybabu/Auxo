// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
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
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
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

/** One bill paid by several methods with the rest on account, kept consistent in invoices, stock and the ledger. */
@RunWith(AndroidJUnit4::class)
class RoomMixedPaymentTest {

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

    /** Three shirts (₹1,497): ₹300 cash, ₹400 UPI, the rest (₹797) on account. */
    private fun mixedSale(customer: Customer): Invoice {
        val cart = Cart(
            lines = listOf(CartLine.from(shirt, shirt.variants[0], quantity = 3)),
            customer = CustomerDetails(name = customer.name, phone = customer.phone.orEmpty(), customerId = customer.id),
        )
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val payments = listOf(
            PaymentRecord(method = PaymentMethod.CASH, amount = Money(30_000)),
            PaymentRecord(method = PaymentMethod.UPI, amount = Money(40_000), reference = "UTR12345678"),
            PaymentRecord(method = PaymentMethod.CREDIT, amount = totals.grandTotal - Money(70_000)),
        )
        return SaleBuilder.build(cart, totals, TestData.settings, payments, TestData.clock.millis())
    }

    @Test
    fun cashUpiAndAccount_areSavedTogether_withTaxStockAndLedgerInStep() = runTest {
        val anita = newCustomer(limit = Money(200_000))
        val sale = mixedSale(anita)
        val recorded = billing.recordSale(sale, "INV") as SaleResult.Recorded
        val saved = billing.getInvoice(recorded.invoiceId)!!

        assertEquals(Money(149_700), saved.grandTotal)
        assertEquals(saved.grandTotal, saved.payments.fold(Money.ZERO) { acc, p -> acc + p.amount })
        assertEquals(
            listOf(Money(30_000), Money(40_000), Money(79_700)),
            listOf(PaymentMethod.CASH, PaymentMethod.UPI, PaymentMethod.CREDIT).map { m -> saved.payments.single { it.method == m }.amount },
        )
        // Tax is on the whole bill, not on the part paid now.
        assertEquals(saved.taxableTotal + saved.taxTotal + saved.roundOff, saved.grandTotal)
        assertEquals(sale.taxTotal, saved.taxTotal)
        assertEquals(5, stock())
        // Only the account part reaches the customer's ledger.
        assertEquals(Money(79_700), balance(anita))
        assertEquals(listOf(Money(79_700)), customers.observeLedger(anita.id).first().map { it.amount })
    }

    @Test
    fun returningAPartCreditBill_refundsTheMoneyPartAndEasesTheAccount() = runTest {
        val anita = newCustomer(limit = Money(200_000))
        val recorded = billing.recordSale(mixedSale(anita), "INV") as SaleResult.Recorded
        val original = billing.getInvoice(recorded.invoiceId)!!

        val note = (ReturnBuilder.build(original, original.lines.associate { it.id to it.quantity }, PaymentMethod.CASH, "All back", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        val result = billing.recordReturn(note, "CN") as ReturnResult.Recorded
        val saved = billing.getInvoice(result.creditNoteId)!!

        assertTrue(saved.payments.all { it.kind == PaymentKind.REFUND })
        assertEquals(Money(79_700), saved.payments.single { it.method == PaymentMethod.CREDIT }.amount)
        assertEquals(Money(70_000), saved.payments.single { it.method == PaymentMethod.CASH }.amount)
        assertEquals(Money.ZERO, balance(anita))
        assertEquals(8, stock())
        assertEquals(setOf(LedgerEntryType.SALE, LedgerEntryType.RETURN), customers.observeLedger(anita.id).first().map { it.type }.toSet())
    }

    @Test
    fun overTheLimit_withSeveralMethods_savesNothing() = runTest {
        val anita = newCustomer(limit = Money(50_000)) // ₹797 on account is over ₹500
        assertEquals(SaleResult.CreditLimitExceeded, billing.recordSale(mixedSale(anita), "INV"))
        assertEquals(Money.ZERO, balance(anita))
        assertEquals(8, stock())
        assertTrue(billing.observeInvoices(0, Long.MAX_VALUE).first().isEmpty())
    }
}
