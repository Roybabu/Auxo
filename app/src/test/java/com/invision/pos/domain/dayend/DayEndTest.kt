package com.invision.pos.domain.dayend

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayEndTest {

    private val day = LocalDate.of(2026, 10, 12)

    private fun total(method: PaymentMethod, kind: PaymentKind, rupees: Long) = MethodTotal(method, kind, Money(rupees * 100))

    private val payments = listOf(
        total(PaymentMethod.CASH, PaymentKind.PAYMENT, 1000),
        total(PaymentMethod.CASH, PaymentKind.REFUND, 150),
        total(PaymentMethod.UPI, PaymentKind.PAYMENT, 500),
        total(PaymentMethod.UPI, PaymentKind.REFUND, 50),
        total(PaymentMethod.CARD, PaymentKind.PAYMENT, 200),
        total(PaymentMethod.CREDIT, PaymentKind.PAYMENT, 300),
        total(PaymentMethod.CREDIT, PaymentKind.REFUND, 100),
    )

    private fun build(receipts: Map<PaymentMethod, Money> = emptyMap(), rows: List<MethodTotal> = payments) =
        DayFiguresBuilder.build(day, DayCounts(bills = 5, returns = 1, netSales = Money(2_000_00)), rows, receipts)

    @Test
    fun expectedCash_isCashTakenMinusCashRefundedPlusCashFromAccounts() {
        val figures = build(receipts = mapOf(PaymentMethod.CASH to Money(400_00), PaymentMethod.UPI to Money(250_00)))
        assertEquals(Money(1_000_00), figures.cashReceived)
        assertEquals(Money(150_00), figures.cashRefunded)
        assertEquals(Money(400_00), figures.cashFromAccounts)
        assertEquals(Money(1_250_00), figures.expectedCash)
    }

    @Test
    fun upiAndCard_areShownNetOfRefundsAndIncludeAccountReceipts() {
        val figures = build(receipts = mapOf(PaymentMethod.UPI to Money(250_00)))
        assertEquals(Money(700_00), figures.upiNet) // 500 - 50 + 250
        assertEquals(Money(200_00), figures.cardNet)
        assertEquals(Money(200_00), figures.onAccountNet) // 300 put on accounts - 100 eased by returns
    }

    @Test
    fun moneyNet_addsCashUpiAndCard_butNotWhatWentOnAccount() {
        val figures = build(receipts = mapOf(PaymentMethod.CASH to Money(400_00), PaymentMethod.UPI to Money(250_00)))
        assertEquals(Money(1_250_00 + 700_00 + 200_00), figures.moneyNet)
    }

    @Test
    fun countsAreCarriedAsGiven() {
        val figures = build()
        assertEquals(5, figures.bills)
        assertEquals(1, figures.returns)
        assertEquals(Money(2_000_00), figures.netSales)
        assertEquals(day, figures.date)
    }

    @Test
    fun aDayWithNothing_isAllZero() {
        val figures = DayFiguresBuilder.build(day, DayCounts(), emptyList(), emptyMap())
        assertEquals(Money.ZERO, figures.expectedCash)
        assertEquals(Money.ZERO, figures.moneyNet)
        assertEquals(0, figures.bills)
    }

    @Test
    fun comparison_showsShortOverAndMatch() {
        val short = CashComparison(expected = Money(1_250_00), counted = Money(1_200_00))
        assertEquals(Money(-50_00), short.difference)
        assertEquals(CashStatus.SHORT, short.status)

        val over = CashComparison(expected = Money(1_250_00), counted = Money(1_251_50))
        assertEquals(Money(1_50), over.difference)
        assertEquals(CashStatus.OVER, over.status)

        val exact = CashComparison(expected = Money(1_250_00), counted = Money(1_250_00))
        assertEquals(CashStatus.MATCHES, exact.status)
        assertTrue(exact.difference.isZero)
    }

    @Test
    fun countedCash_isReadFromWhatWasTyped() {
        assertEquals(CountedCash.Valid(Money(4_250_00)), CountedCash.parse("4250"))
        assertEquals(CountedCash.Valid(Money(4_250_50)), CountedCash.parse(" 4250.50 "))
        assertEquals(CountedCash.Valid(Money.ZERO), CountedCash.parse("0"))
        assertEquals(CountedCash.Invalid(CountError.REQUIRED), CountedCash.parse(""))
        assertEquals(CountedCash.Invalid(CountError.REQUIRED), CountedCash.parse("   "))
        assertEquals(CountedCash.Invalid(CountError.INVALID), CountedCash.parse("-5"))
        assertEquals(CountedCash.Invalid(CountError.INVALID), CountedCash.parse("12.345"))
        assertEquals(CountedCash.Invalid(CountError.INVALID), CountedCash.parse("abc"))
    }
}
