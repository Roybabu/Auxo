package com.auxo.pos.domain.dayend

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import java.time.LocalDate

/** What was taken or given back in total for one method and kind, as read from the saved payments. */
data class MethodTotal(val method: PaymentMethod, val kind: PaymentKind, val amount: Money)

/** How many bills and credit notes a day has, and what they came to once returns are taken off. */
data class DayCounts(val bills: Int = 0, val returns: Int = 0, val netSales: Money = Money.ZERO)

/**
 * The day's money, worked out from stored invoices and account receipts only.
 *
 * Cash in the drawer should rise by [cashReceived] (what bills took, change already taken off) and
 * [cashFromAccounts] (customers paying off their account in cash) and fall by [cashRefunded]. That is
 * [expectedCash]. UPI and card are shown net of refunds; "on account" is what was put on customers'
 * accounts minus what returns eased off them.
 */
data class DayFigures(
    val date: LocalDate,
    val bills: Int,
    val returns: Int,
    val netSales: Money,
    val cashReceived: Money,
    val cashRefunded: Money,
    val cashFromAccounts: Money,
    val upiNet: Money,
    val cardNet: Money,
    val onAccountNet: Money,
) {
    val expectedCash: Money get() = cashReceived - cashRefunded + cashFromAccounts

    /** Everything that came in or went out of the till in cash, UPI or card, after refunds. */
    val moneyNet: Money get() = expectedCash + upiNet + cardNet
}

object DayFiguresBuilder {

    /**
     * [invoicePayments] are the payments and refunds saved with the day's bills and credit notes;
     * [accountReceipts] are the amounts customers paid against their accounts that day, by method.
     */
    fun build(
        date: LocalDate,
        counts: DayCounts,
        invoicePayments: List<MethodTotal>,
        accountReceipts: Map<PaymentMethod, Money>,
    ): DayFigures {
        fun sum(method: PaymentMethod, kind: PaymentKind) = invoicePayments
            .filter { it.method == method && it.kind == kind }
            .fold(Money.ZERO) { acc, row -> acc + row.amount }

        fun net(method: PaymentMethod) =
            sum(method, PaymentKind.PAYMENT) - sum(method, PaymentKind.REFUND) + (accountReceipts[method] ?: Money.ZERO)

        return DayFigures(
            date = date,
            bills = counts.bills,
            returns = counts.returns,
            netSales = counts.netSales,
            cashReceived = sum(PaymentMethod.CASH, PaymentKind.PAYMENT),
            cashRefunded = sum(PaymentMethod.CASH, PaymentKind.REFUND),
            cashFromAccounts = accountReceipts[PaymentMethod.CASH] ?: Money.ZERO,
            upiNet = net(PaymentMethod.UPI),
            cardNet = net(PaymentMethod.CARD),
            onAccountNet = sum(PaymentMethod.CREDIT, PaymentKind.PAYMENT) - sum(PaymentMethod.CREDIT, PaymentKind.REFUND),
        )
    }
}

/** Counted cash against what the day's records say should be in the drawer. */
enum class CashStatus { MATCHES, SHORT, OVER }

data class CashComparison(val expected: Money, val counted: Money) {
    /** Counted minus expected: negative when cash is missing. */
    val difference: Money get() = counted - expected

    val status: CashStatus
        get() = when {
            difference.isZero -> CashStatus.MATCHES
            difference < Money.ZERO -> CashStatus.SHORT
            else -> CashStatus.OVER
        }
}

enum class CountError { REQUIRED, INVALID }

sealed interface CountedCash {
    data class Valid(val amount: Money) : CountedCash

    data class Invalid(val error: CountError) : CountedCash

    companion object {
        /** Reads what was typed: a plain amount such as 4250 or 4250.50; zero is allowed (an empty drawer). */
        fun parse(text: String): CountedCash {
            if (text.isBlank()) return Invalid(CountError.REQUIRED)
            val amount = Money.parseOrNull(text) ?: return Invalid(CountError.INVALID)
            return Valid(amount)
        }
    }
}

/** A saved closing: the day's figures as they were at closing, the cash counted and who closed it. */
data class DayClosing(
    val id: Long,
    val figures: DayFigures,
    val counted: Money,
    val note: String?,
    val closedAtMillis: Long,
    val closedBy: String,
    /** Bills and credit notes saved for the day after it was closed (filled when read, not stored). */
    val billsAfterClosing: Int = 0,
) {
    val date: LocalDate get() = figures.date
    val comparison: CashComparison get() = CashComparison(figures.expectedCash, counted)
    val difference: Money get() = comparison.difference
}
