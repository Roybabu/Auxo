// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.repository.ReturnResult
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomCustomerRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.RoomLoyaltyRepository
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
import com.auxo.pos.domain.loyalty.LoyaltyRedemption
import com.auxo.pos.domain.loyalty.LoyaltyRules
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

/** Earning, using and taking back loyalty points, against the real Room database (in memory). */
@RunWith(AndroidJUnit4::class)
class RoomLoyaltyBillingTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private lateinit var customers: RoomCustomerRepository
    private lateinit var loyalty: RoomLoyaltyRepository
    private lateinit var shirt: Product
    private lateinit var anita: Customer

    /** One point for every Rs 100, each worth Re 1. */
    private val on = LoyaltyRules(true, Money(10_000), Money(100))

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        customers = RoomCustomerRepository(database, TestData.clock)
        loyalty = RoomLoyaltyRepository(database)
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        shirt = inventory.getProduct((inventory.saveProduct(fresh) as SaveProductResult.Saved).productId)!!
        val id = (customers.saveCustomer(Customer(name = "Anita Das", phone = "9830012345")) as SaveCustomerResult.Saved).customerId
        anita = customers.getCustomer(id)!!
    }

    @After
    fun tearDown() = database.close()

    /** [quantity] shirts at Rs 499 each, paid in cash, optionally for [customer] and using [points]. */
    private fun sale(quantity: Int = 2, customer: Customer? = anita, points: Int = 0, value: Money = Money(points * 100L)): Invoice {
        val cart = Cart(
            lines = listOf(CartLine.from(shirt, shirt.variants[0], quantity = quantity)),
            loyalty = if (points > 0) LoyaltyRedemption(points, value) else null,
            customer = customer?.let { CustomerDetails(name = it.name, phone = it.phone.orEmpty(), customerId = it.id) } ?: CustomerDetails(),
        )
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        val payments = listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal))
        return SaleBuilder.build(cart, totals, TestData.settings, payments, TestData.clock.millis())
    }

    private suspend fun givePoints(points: Int) {
        database.customerDao().insertEntry(
            LedgerEntryEntity(
                customerId = anita.id, type = LedgerEntryType.POINTS_EARNED.name, amountMinor = 0, invoiceId = null,
                method = null, reference = null, note = null, createdAt = 1, points = points,
            ),
        )
    }

    private suspend fun points() = customers.observeAccount(anita.id).first()!!.points

    private suspend fun balance() = customers.observeAccount(anita.id).first()!!.balance

    private suspend fun pointEntries() = customers.observeLedger(anita.id).first().filter { it.type.isPoints }

    private suspend fun stock() = inventory.getProduct(shirt.id)!!.variants[0].stockQuantity

    // Earning

    @Test
    fun aSaleToACustomer_earnsWholePoints_onTheLedger() = runTest {
        loyalty.saveRules(on)
        val recorded = billing.recordSale(sale(), "INV") as SaleResult.Recorded // Rs 998 -> 9 points
        val entry = pointEntries().single()
        assertEquals(LedgerEntryType.POINTS_EARNED, entry.type)
        assertEquals(9, entry.points)
        assertEquals(Money.ZERO, entry.amount)
        assertEquals(recorded.number, entry.invoiceNumber)
        assertEquals(9, points())
        assertEquals(Money.ZERO, balance()) // points are not money
    }

    @Test
    fun loyaltyOff_earnsNothing() = runTest {
        billing.recordSale(sale(), "INV")
        assertTrue(pointEntries().isEmpty())
        loyalty.saveRules(on.copy(enabled = false))
        billing.recordSale(sale(), "INV")
        assertTrue(pointEntries().isEmpty())
    }

    @Test
    fun aWalkInSale_earnsNothing() = runTest {
        loyalty.saveRules(on)
        assertTrue(billing.recordSale(sale(customer = null), "INV") is SaleResult.Recorded)
        assertTrue(pointEntries().isEmpty())
    }

    @Test
    fun aSmallBill_earnsNoPoints_andNoEmptyEntry() = runTest {
        loyalty.saveRules(on.copy(spendPerPoint = Money(200_000))) // 1 point per Rs 2,000
        billing.recordSale(sale(), "INV")
        assertTrue(pointEntries().isEmpty())
    }

    @Test
    fun theRulesAtTheTimeOfTheSale_decideThePoints() = runTest {
        loyalty.saveRules(on)
        billing.recordSale(sale(), "INV") // 9 points
        loyalty.saveRules(on.copy(spendPerPoint = Money(5_000))) // now 1 per Rs 50
        billing.recordSale(sale(), "INV") // 19 points
        assertEquals(listOf(9, 19), pointEntries().map { it.points })
    }

    @Test
    fun aRepeatedRequest_earnsOnce() = runTest {
        loyalty.saveRules(on)
        val invoice = sale()
        val first = billing.recordSale(invoice, "INV", "req-1") as SaleResult.Recorded
        val again = billing.recordSale(invoice, "INV", "req-1") as SaleResult.Recorded
        assertEquals(first.invoiceId, again.invoiceId)
        assertEquals(1, pointEntries().size)
        assertEquals(9, points())
    }

    // Using points

    @Test
    fun usingPoints_takesThemOff_andTheSaleEarnsOnTheSmallerBill() = runTest {
        loyalty.saveRules(on)
        givePoints(50)
        val invoice = sale(points = 20) // Rs 998 - Rs 20 = Rs 978
        assertEquals(Money(97_800), invoice.grandTotal)
        val recorded = billing.recordSale(invoice, "INV") as SaleResult.Recorded
        val entries = pointEntries()
        assertEquals(listOf(LedgerEntryType.POINTS_EARNED, LedgerEntryType.POINTS_REDEEMED, LedgerEntryType.POINTS_EARNED), entries.map { it.type })
        assertEquals(-20, entries[1].points)
        assertEquals(recorded.number, entries[1].invoiceNumber)
        assertEquals(9, entries[2].points) // 978 / 100
        assertEquals(50 - 20 + 9, points())
        assertEquals(Money(20_00), billing.getInvoice(recorded.invoiceId)!!.discountTotal)
    }

    @Test
    fun usingMorePointsThanTheCustomerHas_savesNothing() = runTest {
        loyalty.saveRules(on)
        givePoints(10)
        assertEquals(SaleResult.LoyaltyUnavailable, billing.recordSale(sale(points = 11), "INV"))
        assertEquals(10, points())
        assertEquals(8, stock())
        assertTrue(billing.observeInvoices(0, Long.MAX_VALUE).first().isEmpty())
        // The invoice number was not used up.
        assertEquals("INV/26-27/00001", (billing.recordSale(sale(), "INV") as SaleResult.Recorded).number)
    }

    @Test
    fun usingPoints_whileLoyaltyIsOff_savesNothing() = runTest {
        loyalty.saveRules(on)
        givePoints(10)
        val invoice = sale(points = 5)
        loyalty.saveRules(on.copy(enabled = false)) // switched off while the bill was open
        assertEquals(SaleResult.LoyaltyUnavailable, billing.recordSale(invoice, "INV"))
        assertEquals(10, points())
        assertEquals(8, stock())
    }

    @Test
    fun usingPoints_withoutACustomer_savesNothing() = runTest {
        loyalty.saveRules(on)
        assertEquals(SaleResult.LoyaltyUnavailable, billing.recordSale(sale(customer = null, points = 5), "INV"))
        assertEquals(8, stock())
    }

    @Test
    fun aDiscountSmallerThanThePointsAreWorth_isRefused() = runTest {
        loyalty.saveRules(on)
        givePoints(50)
        // The bill says 30 points were used but only Rs 20 came off.
        val tampered = sale(points = 20).copy(loyaltyPointsRedeemed = 30)
        assertEquals(SaleResult.LoyaltyUnavailable, billing.recordSale(tampered, "INV"))
        assertEquals(50, points())
    }

    @Test
    fun usingAllThePoints_leavesZero_neverNegative() = runTest {
        loyalty.saveRules(on)
        givePoints(20)
        assertTrue(billing.recordSale(sale(points = 20), "INV") is SaleResult.Recorded)
        assertEquals(0 + 9, points()) // 20 used, 9 earned on Rs 978
        assertTrue(pointEntries().sumOf { it.points } >= 0)
    }

    @Test
    fun usingPoints_doesNotTouchTheMoneyBalance() = runTest {
        loyalty.saveRules(on)
        givePoints(50)
        billing.recordSale(sale(points = 50), "INV")
        assertEquals(Money.ZERO, balance())
    }

    // Returns

    private suspend fun returnShirts(saleId: Long, quantity: Int, prefix: String = "CN"): ReturnResult {
        val original = billing.getInvoice(saleId)!!
        val build = ReturnBuilder.build(original, mapOf(original.lines.single().id to quantity), PaymentMethod.CASH, "Wrong size", TestData.clock.millis())
        return billing.recordReturn((build as ReturnBuild.Ready).creditNote, prefix)
    }

    @Test
    fun returningGoods_takesBackAShareOfThePoints() = runTest {
        loyalty.saveRules(on)
        val sale = billing.recordSale(sale(), "INV") as SaleResult.Recorded // Rs 998 -> 9 points
        assertTrue(returnShirts(sale.invoiceId, 1) is ReturnResult.Recorded) // half -> 4 points
        assertEquals(5, points())
        val reversal = pointEntries().last()
        assertEquals(LedgerEntryType.POINTS_REVERSED, reversal.type)
        assertEquals(-4, reversal.points)
        assertTrue(returnShirts(sale.invoiceId, 1) is ReturnResult.Recorded) // the rest -> the other 5
        assertEquals(0, points())
    }

    @Test
    fun returningInPieces_takesBackExactlyWhatWasEarned() = runTest {
        loyalty.saveRules(on)
        val sale = billing.recordSale(sale(quantity = 4), "INV") as SaleResult.Recorded // Rs 1,996 -> 19 points
        repeat(4) { assertTrue(returnShirts(sale.invoiceId, 1) is ReturnResult.Recorded) }
        assertEquals(0, points())
        assertEquals(-19, pointEntries().filter { it.type == LedgerEntryType.POINTS_REVERSED }.sumOf { it.points })
    }

    @Test
    fun ifThePointsWereAlreadySpent_onlyWhatIsLeftIsTakenBack() = runTest {
        loyalty.saveRules(on)
        val sale = billing.recordSale(sale(), "INV") as SaleResult.Recorded // 9 points
        database.customerDao().insertEntry(
            LedgerEntryEntity(
                customerId = anita.id, type = LedgerEntryType.POINTS_REDEEMED.name, amountMinor = 0, invoiceId = null,
                method = null, reference = null, note = null, createdAt = 5, points = -8,
            ),
        )
        assertTrue(returnShirts(sale.invoiceId, 2) is ReturnResult.Recorded)
        assertEquals(0, points()) // took the 1 left, not 9
        assertEquals(-1, pointEntries().last().points)
    }

    @Test
    fun returningASaleWithNoPoints_addsNoEntry() = runTest {
        val sale = billing.recordSale(sale(), "INV") as SaleResult.Recorded // loyalty off
        assertTrue(returnShirts(sale.invoiceId, 1) is ReturnResult.Recorded)
        assertTrue(pointEntries().isEmpty())
    }

    @Test
    fun pointsUsedOnASale_areNotGivenBackOnAReturn() = runTest {
        loyalty.saveRules(on)
        givePoints(50)
        val sale = billing.recordSale(sale(points = 20), "INV") as SaleResult.Recorded // earned 9
        assertTrue(returnShirts(sale.invoiceId, 2) is ReturnResult.Recorded)
        // 50 - 20 + 9 - 9 = 30: the 20 used points stay used.
        assertEquals(30, points())
    }

    @Test
    fun aRepeatedReturnRequest_takesBackOnce() = runTest {
        loyalty.saveRules(on)
        val sale = billing.recordSale(sale(), "INV") as SaleResult.Recorded
        val original = billing.getInvoice(sale.invoiceId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines.single().id to 2), PaymentMethod.CASH, "x", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        billing.recordReturn(note, "CN", "ret-1")
        billing.recordReturn(note, "CN", "ret-1")
        assertEquals(0, points())
        assertEquals(1, pointEntries().count { it.type == LedgerEntryType.POINTS_REVERSED })
    }
}
