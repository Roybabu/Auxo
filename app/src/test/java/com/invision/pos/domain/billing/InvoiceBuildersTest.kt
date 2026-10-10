package com.invision.pos.domain.billing

import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds a sale like checkout does and gives it saved ids. */
object SampleSales {
    fun shirtsAndBiscuits(customer: CustomerDetails = CustomerDetails()): Invoice {
        val shirt = TestData.tShirt()
        val biscuits = TestData.biscuits()
        val cart = Cart(customer = customer)
            .add(CartLine.from(shirt, shirt.variants[0], quantity = 3))
            .add(CartLine.from(biscuits, biscuits.variants.single(), quantity = 2))
        val settings = TestData.settings
        val totals = BillCalculator.calculate(cart, settings.taxModeFor(cart))
        val payments = listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal))
        val built = SaleBuilder.build(cart, totals, settings, payments, TestData.clock.millis())
        return built.copy(id = 7, number = "INV/26-27/00007", lines = built.lines.mapIndexed { i, l -> l.copy(id = 100L + i) })
    }
}

class SaleBuilderTest {

    @Test
    fun sale_snapshotsSellerBuyerAndAmounts() {
        val sale = SampleSales.shirtsAndBiscuits(CustomerDetails(name = " Anita Das ", phone = "+91 98310 00000"))
        assertEquals(InvoiceType.SALE, sale.type)
        assertEquals("Roy Mart", sale.seller.name)
        assertEquals("19AABCU9603R1ZK", sale.seller.gstin)
        assertEquals("Anita Das", sale.buyer.name)
        assertEquals("9831000000", sale.buyer.phone)
        assertNull(sale.buyer.gstin)
        assertEquals("19", sale.placeOfSupplyStateCode)
        assertTrue(!sale.interState)
        // 3 x 499 + 2 x 30, prices include GST, rounded to the rupee.
        assertEquals(Money(1_557_00), sale.grandTotal)
        assertEquals(sale.taxableTotal + sale.taxTotal + sale.roundOff, sale.grandTotal)
        assertEquals(listOf(500, 500), sale.lines.map { it.gstRateBasisPoints })
        assertEquals("6109", sale.lines[0].hsnCode)
    }

    @Test
    fun walkInCustomer_hasNoBuyerDetails() {
        val sale = SampleSales.shirtsAndBiscuits()
        assertNull(sale.buyer.name)
        assertNull(sale.buyer.phone)
    }

    @Test
    fun otherState_isInterState() {
        val settings = TestData.settings
        val cart = Cart(placeOfSupplyStateCode = "27")
        assertTrue(settings.taxModeFor(cart).interState)
        assertTrue(!settings.copy(gstRegistered = false).taxModeFor(cart).interState)
    }
}

class ReturnBuilderTest {

    private val sale = SampleSales.shirtsAndBiscuits()
    private val shirtLine = sale.lines[0]

    private fun build(quantities: Map<Long, Int>, original: Invoice = sale, roundOff: Boolean = false) =
        ReturnBuilder.build(original, quantities, PaymentMethod.CASH, "Wrong size", roundOff, TestData.clock.millis())

    @Test
    fun partialReturn_takesAProportionalShare() {
        val note = (build(mapOf(shirtLine.id to 1)) as ReturnBuild.Ready).creditNote
        assertEquals(InvoiceType.RETURN, note.type)
        assertEquals(sale.id, note.originalInvoiceId)
        assertEquals(sale.number, note.originalInvoiceNumber)
        assertEquals("Wrong size", note.note)
        val line = note.lines.single()
        assertEquals(1, line.quantity)
        assertEquals(shirtLine.id, line.originalLineId)
        assertEquals(Money(47_524), line.taxable)
        assertEquals(line.taxable + line.tax, line.total)
        val refund = note.payments.single()
        assertEquals(PaymentKind.REFUND, refund.kind)
        assertEquals(note.grandTotal, refund.amount)
    }

    @Test
    fun returningEveryUnitInSteps_givesBackTheOriginalAmounts() {
        val first = (build(mapOf(shirtLine.id to 1)) as ReturnBuild.Ready).creditNote.lines.single()
        val afterFirst = sale.copy(lines = sale.lines.map { if (it.id == shirtLine.id) it.copy(returnedQuantity = 1) else it })
        val second = (build(mapOf(shirtLine.id to 2), afterFirst) as ReturnBuild.Ready).creditNote.lines.single()
        assertEquals(shirtLine.taxable, first.taxable + second.taxable)
        assertEquals(shirtLine.cgst, first.cgst + second.cgst)
        assertEquals(shirtLine.sgst, first.sgst + second.sgst)
        assertEquals(shirtLine.total, first.total + second.total)
    }

    @Test
    fun fullReturn_refundsTheBillTotal() {
        val note = (build(sale.lines.associate { it.id to it.quantity }, roundOff = true) as ReturnBuild.Ready).creditNote
        assertEquals(sale.grandTotal, note.grandTotal)
    }

    @Test
    fun rejections() {
        assertEquals(ReturnBuild.Rejected(ReturnError.NOTHING_SELECTED), build(emptyMap()))
        assertEquals(ReturnBuild.Rejected(ReturnError.QUANTITY_TOO_LARGE), build(mapOf(shirtLine.id to 4)))
        val creditNote = (build(mapOf(shirtLine.id to 1)) as ReturnBuild.Ready).creditNote
        assertEquals(ReturnBuild.Rejected(ReturnError.NOT_A_SALE), build(mapOf(1L to 1), creditNote))
    }
}
