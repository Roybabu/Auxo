package com.invision.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.ReturnResult
import com.invision.pos.data.repository.RoomBillingRepository
import com.invision.pos.data.repository.CloseDayResult
import com.invision.pos.data.repository.RoomCustomerRepository
import com.invision.pos.data.repository.RoomDayEndRepository
import com.invision.pos.data.repository.AccountPaymentResult
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.data.repository.SaveCustomerResult
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.LedgerEntryType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Product
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

/** Day-end closing against the real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class RoomDayEndTest {

    private lateinit var database: InvisionDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private lateinit var customers: RoomCustomerRepository
    private lateinit var dayEnd: RoomDayEndRepository
    private lateinit var shirt: Product

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        customers = RoomCustomerRepository(database, TestData.clock)
        dayEnd = RoomDayEndRepository(database, TestData.clock)
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

    private val today: LocalDate get() = LocalDate.now(TestData.clock)

    /** Three shirts (₹1,497): ₹300 cash, ₹400 UPI, ₹797 on account, for [customer]. */
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
    fun figures_comeFromStoredBillsReturnsAndAccountReceipts() = runTest {
        val anita = newCustomer(limit = Money(200_000))
        val recorded = billing.recordSale(mixedSale(anita), "INV") as SaleResult.Recorded
        assertTrue(customers.receivePayment(anita.id, Money(20_000), PaymentMethod.CASH, null, null, "req-1") is AccountPaymentResult.Recorded)
        val original = billing.getInvoice(recorded.invoiceId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines[0].id to 1), PaymentMethod.CASH, "One back", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        billing.recordReturn(note, "CN")
        val cashRefund = note.payments.filter { it.method == PaymentMethod.CASH }.fold(Money.ZERO) { acc, p -> acc + p.amount }
        val creditRefund = note.payments.filter { it.method == PaymentMethod.CREDIT }.fold(Money.ZERO) { acc, p -> acc + p.amount }

        val figures = dayEnd.figuresFor(today)
        assertEquals(1, figures.bills)
        assertEquals(1, figures.returns)
        assertEquals(original.grandTotal - note.grandTotal, figures.netSales)
        assertEquals(Money(30_000), figures.cashReceived)
        assertEquals(cashRefund, figures.cashRefunded)
        assertEquals(Money(20_000), figures.cashFromAccounts)
        assertEquals(Money(30_000) - cashRefund + Money(20_000), figures.expectedCash)
        assertEquals(Money(40_000), figures.upiNet)
        assertEquals(Money(79_700) - creditRefund, figures.onAccountNet)
    }

    @Test
    fun anotherDay_hasNoFigures() = runTest {
        billing.recordSale(mixedSale(newCustomer(limit = Money(200_000))), "INV")
        val yesterday = dayEnd.figuresFor(today.minusDays(1))
        assertEquals(0, yesterday.bills)
        assertEquals(Money.ZERO, yesterday.expectedCash)
    }

    @Test
    fun closing_keepsTheFiguresAndTheCountedCash_andLogsNothingElse() = runTest {
        billing.recordSale(mixedSale(newCustomer(limit = Money(200_000))), "INV")
        val result = dayEnd.closeDay(today, Money(29_000), " Short by ten ", "Test Admin") as CloseDayResult.Closed
        val closing = result.closing
        assertEquals(today, closing.date)
        assertEquals(Money(30_000), closing.figures.expectedCash)
        assertEquals(Money(29_000), closing.counted)
        assertEquals(Money(-1_000), closing.difference)
        assertEquals("Short by ten", closing.note)
        assertEquals("Test Admin", closing.closedBy)

        assertEquals(closing.copy(billsAfterClosing = 0), dayEnd.closingFor(today)!!.copy(billsAfterClosing = 0))
        assertEquals(listOf(closing.id), dayEnd.observeClosings().first().map { it.id })
        assertEquals(closing.id, dayEnd.getClosing(closing.id)!!.id)
    }

    @Test
    fun aDay_canBeClosedOnlyOnce_andTheFirstClosingStands() = runTest {
        val first = dayEnd.closeDay(today, Money(10_000), null, "Test Admin") as CloseDayResult.Closed
        val second = dayEnd.closeDay(today, Money(99_999), "Again", "Someone") as CloseDayResult.AlreadyClosed
        assertEquals(first.closing.id, second.closing.id)
        assertEquals(Money(10_000), second.closing.counted)
        assertEquals(1, dayEnd.observeClosings().first().size)
    }

    @Test
    fun aFutureDay_cannotBeClosed() = runTest {
        assertEquals(CloseDayResult.FutureDay, dayEnd.closeDay(today.plusDays(1), Money.ZERO, null, "Test Admin"))
        assertTrue(dayEnd.observeClosings().first().isEmpty())
    }

    @Test
    fun aClosedDay_staysAsItWas_whenMoreIsBilledAfterwards() = runTest {
        val closing = (dayEnd.closeDay(today, Money.ZERO, null, "Test Admin") as CloseDayResult.Closed).closing
        assertEquals(0, closing.figures.bills)

        billing.recordSale(mixedSale(newCustomer(limit = Money(200_000))), "INV")

        val reread = dayEnd.closingFor(today)!!
        assertEquals(0, reread.figures.bills)
        assertEquals(Money.ZERO, reread.figures.cashReceived)
        assertEquals(Money.ZERO, reread.figures.expectedCash)
        // The late bill is reported next to the closing instead of changing it.
        assertEquals(1, reread.billsAfterClosing)
        assertEquals(1, dayEnd.figuresFor(today).bills)
    }

    @Test
    fun anEmptyDrawer_isAValidCount() = runTest {
        val closing = (dayEnd.closeDay(today, Money.ZERO, null, "Test Admin") as CloseDayResult.Closed).closing
        assertTrue(closing.difference.isZero)
    }
}
