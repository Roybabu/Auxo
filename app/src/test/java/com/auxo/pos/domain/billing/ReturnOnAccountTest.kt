package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A return on a bill that was partly on the customer's account gives the account its share back first. */
class ReturnOnAccountTest {

    /** The sample bill (₹1,557.00) paid ₹600 cash and ₹957 on account, for customer 5. */
    private val sale: Invoice = SampleSales.shirtsAndBiscuits().let {
        it.copy(
            customerId = 5,
            payments = listOf(
                PaymentRecord(method = PaymentMethod.CASH, amount = Money(600_00)),
                PaymentRecord(method = PaymentMethod.CREDIT, amount = Money(957_00)),
            ),
        )
    }

    private fun build(original: Invoice, quantities: Map<Long, Int>, method: PaymentMethod = PaymentMethod.CASH): Invoice =
        (ReturnBuilder.build(original, quantities, method, "Test", TestData.clock.millis()) as ReturnBuild.Ready).creditNote

    private fun List<PaymentRecord>.sumOf(method: PaymentMethod) =
        filter { it.method == method }.fold(Money.ZERO) { acc, p -> acc + p.amount }

    @Test
    fun fullReturn_putsTheAccountPartBackOnTheAccountAndRefundsTheRestInCash() {
        val note = build(sale, sale.lines.associate { it.id to it.quantity })
        assertEquals(Money(1_557_00), note.grandTotal)
        assertEquals(Money(957_00), note.payments.sumOf(PaymentMethod.CREDIT))
        assertEquals(Money(600_00), note.payments.sumOf(PaymentMethod.CASH))
        assertTrue(note.payments.all { it.kind == PaymentKind.REFUND })
        assertEquals(note.grandTotal, note.payments.fold(Money.ZERO) { acc, p -> acc + p.amount })
    }

    @Test
    fun refundNeverPaysOutMoreMoneyThanWasPaid() {
        // Returning the shirts only (3 x 499 = 1,497): the account share follows the same proportion.
        val note = build(sale, mapOf(sale.lines[0].id to 3), PaymentMethod.UPI)
        val credit = note.payments.sumOf(PaymentMethod.CREDIT)
        val upi = note.payments.sumOf(PaymentMethod.UPI)
        assertEquals(note.grandTotal, credit + upi)
        assertTrue(credit > Money.ZERO)
        assertTrue(upi <= Money(600_00))
    }

    @Test
    fun returnsInSteps_giveBackExactlyTheAmountThatWasOnAccount() {
        var invoice = sale
        var credited = Money.ZERO
        var refunded = Money.ZERO
        while (!invoice.isFullyReturned) {
            val line = invoice.lines.first { it.returnableQuantity > 0 }
            val note = build(invoice, mapOf(line.id to 1))
            credited += note.payments.sumOf(PaymentMethod.CREDIT)
            refunded += note.grandTotal
            invoice = invoice.copy(
                lines = invoice.lines.map {
                    it.copy(returnedQuantity = it.returnedQuantity + note.lines.filter { n -> n.originalLineId == it.id }.sumOf { n -> n.quantity })
                },
            )
        }
        assertEquals(Money(957_00), credited)
        assertEquals(sale.grandTotal, refunded)
    }

    @Test
    fun refundToAccountChosen_isNotSplit() {
        val note = build(sale, sale.lines.associate { it.id to it.quantity }, PaymentMethod.CREDIT)
        assertEquals(listOf(PaymentMethod.CREDIT), note.payments.map { it.method })
        assertEquals(sale.grandTotal, note.payments.single().amount)
    }

    @Test
    fun billPaidFullyInMoney_isRefundedWholeByTheChosenMethod() {
        val paid = SampleSales.shirtsAndBiscuits().copy(customerId = 5)
        val note = build(paid, paid.lines.associate { it.id to it.quantity }, PaymentMethod.UPI)
        assertEquals(listOf(PaymentMethod.UPI), note.payments.map { it.method })
    }

    @Test
    fun billWithoutACustomer_neverGetsAnAccountRefund() {
        val walkIn = sale.copy(customerId = null)
        val note = build(walkIn, walkIn.lines.associate { it.id to it.quantity })
        assertEquals(listOf(PaymentMethod.CASH), note.payments.map { it.method })
    }
}
