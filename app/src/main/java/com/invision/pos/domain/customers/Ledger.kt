package com.invision.pos.domain.customers

import com.invision.pos.domain.model.LedgerEntry
import com.invision.pos.domain.model.LedgerEntryType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod

/** A ledger entry with the balance straight after it. */
data class LedgerLine(val entry: LedgerEntry, val balanceAfter: Money) {
    /** Amount added to what the customer owes (sales on account, opening balance owed). */
    val debit: Money get() = if (entry.amount > Money.ZERO) entry.amount else Money.ZERO

    /** Amount taken off what the customer owes (payments, returns, advance). */
    val credit: Money get() = if (entry.amount < Money.ZERO) Money(-entry.amount.minorUnits) else Money.ZERO
}

object Ledger {
    /** Positive when the customer owes the store. */
    fun balance(entries: List<LedgerEntry>): Money = entries.fold(Money.ZERO) { acc, entry -> acc + entry.amount }

    /** Entries oldest first, each with the running balance. */
    fun statement(entries: List<LedgerEntry>): List<LedgerLine> {
        var balance = Money.ZERO
        return entries.sortedWith(compareBy({ it.createdAtMillis }, { it.id })).map { entry ->
            balance += entry.amount
            LedgerLine(entry, balance)
        }
    }

    /** Total put on accounts and total collected, for summaries. */
    fun totals(entries: List<LedgerEntry>): Pair<Money, Money> {
        val charged = entries.filter { it.type == LedgerEntryType.SALE }.fold(Money.ZERO) { acc, e -> acc + e.amount }
        val paid = entries.filter { it.type == LedgerEntryType.PAYMENT }.fold(Money.ZERO) { acc, e -> acc - e.amount }
        return charged to paid
    }
}

enum class AccountPaymentError { AMOUNT_REQUIRED, AMOUNT_INVALID, MORE_THAN_DUE, NOTHING_DUE, REFERENCE_INVALID, NOTE_TOO_LONG }

sealed interface AccountPaymentCheck {
    data class Valid(val amount: Money, val reference: String?, val note: String?) : AccountPaymentCheck
    data class Invalid(val errors: Set<AccountPaymentError>) : AccountPaymentCheck
}

/** Checks a payment received against a customer account. */
object AccountPaymentValidator {
    const val NOTE_MAX = 120
    private val UPI_REFERENCE = Regex("""[A-Za-z0-9]{4,35}""")
    private val CARD_LAST_FOUR = Regex("""\d{4}""")

    fun check(amountText: String, method: PaymentMethod, reference: String, note: String, balance: Money): AccountPaymentCheck {
        val errors = mutableSetOf<AccountPaymentError>()
        val amount = Money.parseOrNull(amountText)?.takeIf { !it.isZero }
        when {
            balance <= Money.ZERO -> errors += AccountPaymentError.NOTHING_DUE
            amountText.isBlank() -> errors += AccountPaymentError.AMOUNT_REQUIRED
            amount == null -> errors += AccountPaymentError.AMOUNT_INVALID
            amount > balance -> errors += AccountPaymentError.MORE_THAN_DUE
        }
        val ref = reference.trim()
        val referenceOk = when {
            ref.isEmpty() -> true
            method == PaymentMethod.UPI -> UPI_REFERENCE.matches(ref)
            method == PaymentMethod.CARD -> CARD_LAST_FOUR.matches(ref)
            else -> true
        }
        if (!referenceOk) errors += AccountPaymentError.REFERENCE_INVALID
        if (note.trim().length > NOTE_MAX) errors += AccountPaymentError.NOTE_TOO_LONG
        return if (errors.isEmpty() && amount != null) {
            AccountPaymentCheck.Valid(amount, ref.ifEmpty { null }.takeIf { method != PaymentMethod.CASH }, note.trim().ifEmpty { null })
        } else {
            AccountPaymentCheck.Invalid(errors)
        }
    }
}
