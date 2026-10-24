// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.customers

import com.auxo.pos.domain.contacts.ContactError
import com.auxo.pos.domain.contacts.ContactField
import com.auxo.pos.domain.contacts.CustomerForm
import com.auxo.pos.domain.contacts.CustomerValidation
import com.auxo.pos.domain.contacts.CustomerValidator
import com.auxo.pos.domain.contacts.SupplierForm
import com.auxo.pos.domain.contacts.SupplierValidation
import com.auxo.pos.domain.contacts.SupplierValidator
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {

    private fun entry(id: Long, type: LedgerEntryType, amount: Long, at: Long) =
        LedgerEntry(id = id, customerId = 1, type = type, amount = Money(amount), createdAtMillis = at)

    private val entries = listOf(
        entry(3, LedgerEntryType.PAYMENT, -30_000, 300),
        entry(1, LedgerEntryType.OPENING_BALANCE, 10_000, 100),
        entry(2, LedgerEntryType.SALE, 50_000, 200),
        entry(4, LedgerEntryType.RETURN, -5_000, 300),
    )

    @Test
    fun balance_isTheSumOfEntries() {
        assertEquals(Money(25_000), Ledger.balance(entries))
        assertEquals(Money.ZERO, Ledger.balance(emptyList()))
    }

    @Test
    fun statement_runsTheBalanceInDateOrder() {
        val lines = Ledger.statement(entries)
        assertEquals(listOf(1L, 2L, 3L, 4L), lines.map { it.entry.id })
        assertEquals(listOf(10_000L, 60_000L, 30_000L, 25_000L), lines.map { it.balanceAfter.minorUnits })
        assertEquals(Money(50_000), lines[1].debit)
        assertEquals(Money(30_000), lines[2].credit)
        assertEquals(Money.ZERO, lines[2].debit)
        // The last running balance always equals the account balance.
        assertEquals(Ledger.balance(entries), lines.last().balanceAfter)
    }

    @Test
    fun totals_countSalesOnAccountAndPaymentsReceived() {
        assertEquals(Money(50_000) to Money(30_000), Ledger.totals(entries))
    }
}

class AccountPaymentValidatorTest {

    private val due = Money(25_000)

    @Test
    fun validPayment_keepsReferenceOnlyForUpiAndCard() {
        val upi = AccountPaymentValidator.check("150", PaymentMethod.UPI, "ABC123XYZ", " paid at shop ", due) as AccountPaymentCheck.Valid
        assertEquals(Money(15_000), upi.amount)
        assertEquals("ABC123XYZ", upi.reference)
        assertEquals("paid at shop", upi.note)
        val cash = AccountPaymentValidator.check("250", PaymentMethod.CASH, "ignored", "", due) as AccountPaymentCheck.Valid
        assertEquals(null, cash.reference)
    }

    @Test
    fun invalidPayments_areRejected() {
        fun errors(amount: String, method: PaymentMethod = PaymentMethod.CASH, reference: String = "", balance: Money = due) =
            (AccountPaymentValidator.check(amount, method, reference, "", balance) as AccountPaymentCheck.Invalid).errors
        assertEquals(setOf(AccountPaymentError.AMOUNT_REQUIRED), errors(""))
        assertEquals(setOf(AccountPaymentError.AMOUNT_INVALID), errors("0"))
        assertEquals(setOf(AccountPaymentError.AMOUNT_INVALID), errors("12.345"))
        assertEquals(setOf(AccountPaymentError.MORE_THAN_DUE), errors("250.01"))
        assertEquals(setOf(AccountPaymentError.NOTHING_DUE), errors("10", balance = Money.ZERO))
        assertEquals(setOf(AccountPaymentError.REFERENCE_INVALID), errors("10", PaymentMethod.CARD, "12a4"))
    }
}

class ContactValidatorTest {

    @Test
    fun customer_normalisesMobile_andTakesStateFromGstin() {
        val result = CustomerValidator.validate(
            CustomerForm(name = " Anita Das ", phone = "+91 98300 12345", gstin = "19aabcu9603r1zk", creditLimit = "5000", openingBalance = "1200.50"),
        ) as CustomerValidation.Valid
        assertEquals("Anita Das", result.customer.name)
        assertEquals("9830012345", result.customer.phone)
        assertEquals("19AABCU9603R1ZK", result.customer.gstin)
        assertEquals("19", result.customer.stateCode)
        assertEquals(Money(500_000), result.customer.creditLimit)
        assertEquals(Money(120_050), result.openingBalance)
    }

    @Test
    fun customer_errors() {
        val result = CustomerValidator.validate(
            CustomerForm(name = "", phone = "12345", email = "not-an-email", gstin = "19AABCU9603R1ZK", stateCode = "27", creditLimit = "lots"),
        ) as CustomerValidation.Invalid
        assertEquals(ContactError.REQUIRED, result.errors[ContactField.NAME])
        assertEquals(ContactError.INVALID_MOBILE, result.errors[ContactField.PHONE])
        assertEquals(ContactError.INVALID_EMAIL, result.errors[ContactField.EMAIL])
        assertEquals(ContactError.GSTIN_STATE_MISMATCH, result.errors[ContactField.GSTIN])
        assertEquals(ContactError.INVALID_AMOUNT, result.errors[ContactField.CREDIT_LIMIT])
    }

    @Test
    fun editingACustomer_ignoresTheOpeningBalance() {
        val existing = Customer(id = 4, name = "Anita", createdAtMillis = 50)
        val result = CustomerValidator.validate(CustomerForm(name = "Anita Das", openingBalance = "oops"), existing) as CustomerValidation.Valid
        assertEquals(4L, result.customer.id)
        assertEquals(50L, result.customer.createdAtMillis)
        assertEquals(Money.ZERO, result.openingBalance)
    }

    @Test
    fun supplier_acceptsLandlines_andChecksName() {
        val valid = SupplierValidator.validate(SupplierForm(name = "Fresh Mills", phone = "033 2229 1234", email = "orders@freshmills.in"))
        assertTrue(valid is SupplierValidation.Valid)
        assertEquals(true, (valid as SupplierValidation.Valid).supplier.active)
        val invalid = SupplierValidator.validate(SupplierForm(name = "x".repeat(81), phone = "12")) as SupplierValidation.Invalid
        assertEquals(ContactError.TOO_LONG, invalid.errors[ContactField.NAME])
        assertEquals(ContactError.INVALID_PHONE, invalid.errors[ContactField.PHONE])
    }
}
