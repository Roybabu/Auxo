package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.testutil.TestData
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
        assertEquals("Sample Mart", sale.seller.name)
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

    private fun build(quantities: Map<Long, Int>, original: Invoice = sale) =
        ReturnBuilder.build(original, quantities, PaymentMethod.CASH, "Wrong size", TestData.clock.millis())

    /** A bill for [quantity] packs at [unitPrice] paise each (5% GST, prices include GST). */
    private fun saleOf(unitPrice: Long, quantity: Int, roundOff: Boolean = true): Invoice {
        val biscuits = TestData.biscuits()
        val variant = biscuits.variants.single().copy(sellingPrice = Money(unitPrice), mrp = null, stockQuantity = 10)
        val cart = Cart().add(CartLine.from(biscuits.copy(variants = listOf(variant)), variant, quantity = quantity))
        val settings = TestData.settings.copy(roundOffTotal = roundOff)
        val totals = BillCalculator.calculate(cart, settings.taxModeFor(cart))
        val payments = listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal))
        val built = SaleBuilder.build(cart, totals, settings, payments, TestData.clock.millis())
        return built.copy(id = 9, number = "INV/26-27/00009", lines = built.lines.mapIndexed { i, l -> l.copy(id = 200L + i) })
    }

    /** The original invoice as it is stored after [note] was saved. */
    private fun afterReturn(original: Invoice, note: Invoice): Invoice = original.copy(
        lines = original.lines.map { line ->
            line.copy(returnedQuantity = line.returnedQuantity + note.lines.filter { it.originalLineId == line.id }.sumOf { it.quantity })
        },
    )

    /** Returns one unit at a time until everything is back; gives the refund of each credit note. */
    private fun refundsOneByOne(original: Invoice): List<Money> {
        var invoice = original
        val refunds = mutableListOf<Money>()
        while (!invoice.isFullyReturned) {
            val line = invoice.lines.first { it.returnableQuantity > 0 }
            val note = (build(mapOf(line.id to 1), invoice) as ReturnBuild.Ready).creditNote
            assertEquals(note.taxableTotal + note.taxTotal + note.roundOff, note.grandTotal)
            assertEquals(note.grandTotal, note.payments.fold(Money.ZERO) { acc, p -> acc + p.amount })
            refunds += note.grandTotal
            invoice = afterReturn(invoice, note)
        }
        return refunds
    }

    private fun List<Money>.total(): Money = fold(Money.ZERO) { acc, m -> acc + m }

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
        val note = (build(sale.lines.associate { it.id to it.quantity }) as ReturnBuild.Ready).creditNote
        assertEquals(sale.grandTotal, note.grandTotal)
        assertEquals(sale.grandTotal, note.payments.single().amount)
    }

    @Test
    fun partialReturnsOnARoundedBill_addUpToWhatWasPaid() {
        // 3 x 99.50 = 298.50, rounded to 299.00.
        val sale = saleOf(unitPrice = 99_50, quantity = 3)
        assertEquals(Money(299_00), sale.grandTotal)
        // Each refund is rounded too, but they still add up to the bill.
        assertEquals(listOf(Money(100_00), Money(99_00), Money(100_00)), refundsOneByOne(sale))
    }

    @Test
    fun partialReturnsOnAnUnroundedBill_refundTheExactAmounts() {
        val sale = saleOf(unitPrice = 99_50, quantity = 3, roundOff = false)
        assertEquals(Money(298_50), sale.grandTotal)
        assertEquals(List(3) { Money(99_50) }, refundsOneByOne(sale))
    }

    @Test
    fun refundsNeverGoNegativeAndAlwaysAddUpToTheBill() {
        for (price in listOf(1L, 30L, 49L, 50L, 51L, 99L, 99_50L, 123_45L, 2_624_99L)) {
            for (quantity in 1..4) {
                for (roundOff in listOf(true, false)) {
                    val sale = saleOf(price, quantity, roundOff)
                    val refunds = refundsOneByOne(sale)
                    val case = "price $price x $quantity, round off $roundOff"
                    assertTrue(case, refunds.all { it >= Money.ZERO })
                    assertEquals(case, sale.grandTotal, refunds.total())
                }
            }
        }
    }

    @Test
    fun refundDue_followsTheBillsRounding() {
        val sale = saleOf(unitPrice = 99_50, quantity = 3)
        val line = sale.lines.single().id
        assertEquals(Money.ZERO, ReturnBuilder.refundDue(sale, emptyMap()))
        assertEquals(Money(100_00), ReturnBuilder.refundDue(sale, mapOf(line to 1)))
        assertEquals(Money(199_00), ReturnBuilder.refundDue(sale, mapOf(line to 2)))
        assertEquals(sale.grandTotal, ReturnBuilder.refundDue(sale, mapOf(line to 3)))
    }

    @Test
    fun aBillThatWasNotRounded_refundsExactPaise_evenWhenItsTotalIsWholeRupees() {
        val biscuits = TestData.biscuits()
        val a = biscuits.variants.single().copy(id = 1, sellingPrice = Money(33_33), mrp = null, stockQuantity = 10)
        val b = biscuits.variants.single().copy(id = 2, sellingPrice = Money(1), mrp = null, stockQuantity = 10)
        val product = biscuits.copy(variants = listOf(a, b))
        val cart = Cart().add(CartLine.from(product, a, quantity = 3)).add(CartLine.from(product, b, quantity = 1))
        val settings = TestData.settings.copy(roundOffTotal = false)
        val totals = BillCalculator.calculate(cart, settings.taxModeFor(cart))
        assertEquals(Money(100_00), totals.grandTotal)
        val payments = listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal))
        val built = SaleBuilder.build(cart, totals, settings, payments, TestData.clock.millis())
        val sale = built.copy(id = 9, number = "INV/26-27/00009", lines = built.lines.mapIndexed { i, l -> l.copy(id = 300L + i) })
        assertTrue(!sale.roundedToRupee)
        assertEquals(Money(33_33), ReturnBuilder.refundDue(sale, mapOf(sale.lines[0].id to 1)))
        // The same bill made with round-off on refunds whole rupees.
        assertEquals(Money(33_00), ReturnBuilder.refundDue(sale.copy(roundedToRupee = true), mapOf(sale.lines[0].id to 1)))
    }

    @Test
    fun rejections() {
        assertEquals(ReturnBuild.Rejected(ReturnError.NOTHING_SELECTED), build(emptyMap()))
        assertEquals(ReturnBuild.Rejected(ReturnError.QUANTITY_TOO_LARGE), build(mapOf(shirtLine.id to 4)))
        val creditNote = (build(mapOf(shirtLine.id to 1)) as ReturnBuild.Ready).creditNote
        assertEquals(ReturnBuild.Rejected(ReturnError.NOT_A_SALE), build(mapOf(1L to 1), creditNote))
    }
}
