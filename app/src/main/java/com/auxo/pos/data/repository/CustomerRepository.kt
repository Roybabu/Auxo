// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.repository

import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import kotlinx.coroutines.flow.Flow

sealed interface SaveCustomerResult {
    data class Saved(val customerId: Long) : SaveCustomerResult

    /** Another customer already has this mobile number. */
    data object PhoneInUse : SaveCustomerResult
}

sealed interface AccountPaymentResult {
    data class Recorded(val entryId: Long) : AccountPaymentResult

    /** Nothing was saved: the amount is more than the customer owes ([balance]). */
    data class MoreThanDue(val balance: Money) : AccountPaymentResult

    data object CustomerMissing : AccountPaymentResult
}

interface CustomerRepository {
    fun observeAccounts(): Flow<List<CustomerAccount>>

    fun observeAccount(customerId: Long): Flow<CustomerAccount?>

    suspend fun getCustomer(customerId: Long): Customer?

    suspend fun findByPhone(phone: String): Customer?

    /** Inserts or updates a customer. A new customer can start with an [openingBalance] (positive when they owe). */
    suspend fun saveCustomer(customer: Customer, openingBalance: Money = Money.ZERO): SaveCustomerResult

    /** Account entries, oldest first. */
    fun observeLedger(customerId: Long): Flow<List<LedgerEntry>>

    fun observeInvoices(customerId: Long): Flow<List<InvoiceSummary>>

    /** Every customer's money entries (no loyalty points), for reports that look at how old the dues are. */
    fun observeMoneyLedger(): Flow<List<LedgerEntry>> = kotlinx.coroutines.flow.flowOf(emptyList())

    /**
     * Records money received against the account, refusing more than the customer
     * owes. A [requestId] that was already used is not recorded again.
     */
    suspend fun receivePayment(
        customerId: Long,
        amount: Money,
        method: PaymentMethod,
        reference: String?,
        note: String?,
        requestId: String,
    ): AccountPaymentResult

    /** Payments received against accounts in [fromMillis, toMillis), newest first. */
    fun observeAccountPayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentRecord>>
}
