package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.AccountPaymentResult
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
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.testutil.DataIntegrity
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** Exact limits (credit, balance, expiry day) and two tills selling the last unit at once. */
@RunWith(AndroidJUnit4::class)
class LimitsAndRacesTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private lateinit var customers: RoomCustomerRepository
    private val store = TestData.settings

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        customers = RoomCustomerRepository(database, TestData.clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun saveNew(product: Product): Product {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        return inventory.getProduct((inventory.saveProduct(fresh) as SaveProductResult.Saved).productId)!!
    }

    private suspend fun newCustomer(limit: Money? = null, opening: Money = Money.ZERO): Customer {
        val saved = customers.saveCustomer(Customer(name = "Anita Das", phone = "9830012345", creditLimit = limit), opening) as SaveCustomerResult.Saved
        return customers.getCustomer(saved.customerId)!!
    }

    private fun sale(line: CartLine, customer: Customer? = null, onAccount: Boolean = false): Invoice {
        val cart = Cart(
            lines = listOf(line),
            customer = customer?.let { CustomerDetails(name = it.name, phone = it.phone.orEmpty(), customerId = it.id) } ?: CustomerDetails(),
        )
        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        val method = if (onAccount) PaymentMethod.CREDIT else PaymentMethod.CASH
        return SaleBuilder.build(cart, totals, store, listOf(PaymentRecord(method = method, amount = totals.grandTotal)), TestData.clock.millis())
    }

    @Test
    fun creditLimit_isInclusive_toTheLastPaisa() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val twoShirts = CartLine.from(shirt, shirt.variants[0], 2) // ₹998
        val exact = newCustomer(limit = Money(99_800))
        assertTrue(billing.recordSale(sale(twoShirts, exact, onAccount = true), "INV") is SaleResult.Recorded)
        // Anything more is over: the account is full.
        assertEquals(SaleResult.CreditLimitExceeded, billing.recordSale(sale(CartLine.from(shirt, shirt.variants[0], 1), exact, onAccount = true), "INV"))

        val oneShort = customers.saveCustomer(Customer(name = "Ravi", phone = "9830099999", creditLimit = Money(99_799))) as SaveCustomerResult.Saved
        val ravi = customers.getCustomer(oneShort.customerId)!!
        assertEquals(SaleResult.CreditLimitExceeded, billing.recordSale(sale(twoShirts.copy(quantity = 2), ravi, onAccount = true), "INV"))
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun accountPayment_canClearTheBalanceExactly_butNotExceedIt() = runTest {
        val customer = newCustomer(opening = Money(100_000))

        assertEquals(AccountPaymentResult.MoreThanDue(Money(100_000)), customers.receivePayment(customer.id, Money(100_001), PaymentMethod.CASH, null, null, "p-1"))
        assertEquals(Money(100_000), customers.observeAccount(customer.id).first()!!.balance)

        assertTrue(customers.receivePayment(customer.id, Money(100_000), PaymentMethod.UPI, "UTR12345", null, "p-2") is AccountPaymentResult.Recorded)
        assertTrue(customers.observeAccount(customer.id).first()!!.balance.isZero)

        assertEquals(AccountPaymentResult.MoreThanDue(Money.ZERO), customers.receivePayment(customer.id, Money(1), PaymentMethod.CASH, null, null, "p-3"))
        assertEquals(2, customers.observeLedger(customer.id).first().size) // opening balance and the one payment
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun stockExpiringToday_stillSells_butYesterdaysDoesNot() = runTest {
        val today = saveNew(TestData.biscuits(id = 2, expiry = TestData.today))
        val expiredBiscuits = TestData.biscuits(id = 3, expiry = TestData.today.minusDays(1))
        val yesterday = saveNew(expiredBiscuits.copy(variants = expiredBiscuits.variants.map { it.copy(barcode = TestData.EAN[0]) }))

        assertTrue(billing.recordSale(sale(CartLine.from(today, today.variants[0], 1)), "INV") is SaleResult.Recorded)
        val expired = billing.recordSale(sale(CartLine.from(yesterday, yesterday.variants[0], 1)), "INV")
        assertEquals(SaleResult.StockShortage(setOf(yesterday.variants[0].id)), expired)
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun twoTillsSellingTheLastUnit_oneWins() = runTest {
        val shirt = saveNew(TestData.tShirt()) // size L has exactly 1
        val last = shirt.variants[1]
        assertEquals(1, last.stockQuantity)

        val results = (1..2).map { till -> async { billing.recordSale(sale(CartLine.from(shirt, last, 1)), "INV", "till-$till") } }.awaitAll()

        assertEquals(1, results.count { it is SaleResult.Recorded })
        assertEquals(1, results.count { it is SaleResult.StockShortage })
        assertEquals(0, inventory.getProduct(shirt.id)!!.variants[1].stockQuantity)
        assertEquals(1, DataIntegrity.count(database, "invoices"))
        DataIntegrity.assertConsistent(database)
    }
}
