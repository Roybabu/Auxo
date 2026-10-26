// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.customers

import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod

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

    /** Money entries oldest first, each with the running balance. Loyalty point entries are not money and are left out. */
    fun statement(entries: List<LedgerEntry>): List<LedgerLine> {
        var balance = Money.ZERO
        return entries.filterNot { it.type.isPoints }.sortedWith(compareBy({ it.createdAtMillis }, { it.id })).map { entry ->
            balance += entry.amount
            LedgerLine(entry, balance)
        }
    }

    /**
     * When the oldest money still owed was charged, or null if nothing is owed. Payments and returns settle the
     * oldest charges first, so a customer who has paid the old bills but not the new one is "owing since" the new
     * one. Money paid in advance is used up by the next charges. Loyalty point entries are ignored.
     */
    fun owingSince(entries: List<LedgerEntry>): Long? {
        val money = entries.filterNot { it.type.isPoints }.sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
        if (balance(money) <= Money.ZERO) return null
        val open = ArrayDeque<Pair<Long, Long>>()
        var credit = 0L
        for (entry in money) {
            val amount = entry.amount.minorUnits
            if (amount > 0) {
                val used = minOf(credit, amount)
                credit -= used
                if (amount - used > 0) open.addLast(entry.createdAtMillis to amount - used)
            } else if (amount < 0) {
                var pay = -amount
                while (pay > 0 && open.isNotEmpty()) {
                    val (time, remaining) = open.first()
                    if (remaining <= pay) {
                        pay -= remaining
                        open.removeFirst()
                    } else {
                        open[0] = time to remaining - pay
                        pay = 0
                    }
                }
                credit += pay
            }
        }
        return open.firstOrNull()?.first
    }

    /** Loyalty point entries, newest first. */
    fun pointsHistory(entries: List<LedgerEntry>): List<LedgerEntry> =
        entries.filter { it.type.isPoints }.sortedWith(compareByDescending<LedgerEntry> { it.createdAtMillis }.thenByDescending { it.id })

    /** Total put on accounts and total collected, for summaries. */
    fun totals(entries: List<LedgerEntry>): Pair<Money, Money> {
        val charged = entries.filter { it.type == LedgerEntryType.SALE }.fold(Money.ZERO) { acc, e -> acc + e.amount }
        val paid = entries.filter { it.type == LedgerEntryType.PAYMENT }.fold(Money.ZERO) { acc, e -> acc - e.amount }
        return charged to paid
    }

    /**
     * What the customer has bought from the shop and what they have paid for it, all time. Bought is every bill
     * minus the credit notes; paid is that less what is still owed on those bills, so it counts money handed over at
     * the counter as well as payments received later. A balance brought in when the account was opened is not a bill
     * and is left out of both.
     */
    fun lifetime(invoices: List<InvoiceSummary>): CustomerLifetime {
        val sales = invoices.filter { it.type == InvoiceType.SALE }
        if (sales.isEmpty()) return CustomerLifetime(null, null)
        val total = sales.fold(Money.ZERO) { acc, invoice -> acc + invoice.grandTotal }
        return CustomerLifetime(sales.maxOf { it.createdAtMillis }, Money(total.minorUnits / sales.size))
    }

    fun purchases(invoices: List<InvoiceSummary>, entries: List<LedgerEntry>): Pair<Money, Money> {
        val bought = invoices.fold(Money.ZERO) { acc, invoice ->
            if (invoice.type == InvoiceType.RETURN) acc - invoice.grandTotal else acc + invoice.grandTotal
        }
        val stillOwed = entries.filterNot { it.type.isPoints || it.type == LedgerEntryType.OPENING_BALANCE }
            .fold(Money.ZERO) { acc, entry -> acc + entry.amount }
        return bought to (bought - stillOwed)
    }
}

/** When the customer last bought something and what an average bill comes to. */
data class CustomerLifetime(val lastPurchaseMillis: Long?, val averageBill: Money?)

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
