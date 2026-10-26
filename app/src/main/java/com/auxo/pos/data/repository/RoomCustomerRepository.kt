// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.RequestClaim
import com.auxo.pos.data.local.claim
import com.auxo.pos.data.local.dao.OverpaymentException
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.local.toDomain
import com.auxo.pos.data.local.toEntity
import com.auxo.pos.data.local.toPaymentRecord
import com.auxo.pos.domain.model.Customer
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.customers.Ledger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock

/** Thrown inside a payment transaction when the customer no longer exists. */
private class NoCustomerException : RuntimeException("Customer not found")

class RoomCustomerRepository(
    private val database: AuxoDatabase,
    private val clock: Clock,
) : CustomerRepository {

    private val dao = database.customerDao()
    private val billing = database.billingDao()
    private val operations = database.operationDao()

    override fun observeAccounts(): Flow<List<CustomerAccount>> = combine(dao.observeAccounts(), dao.observeMoneyEntries()) { rows, entries ->
        val byCustomer = entries.map { it.toDomain() }.groupBy { it.customerId }
        rows.map { row ->
            val account = row.toDomain()
            if (account.balance > Money.ZERO) account.copy(owingSinceMillis = Ledger.owingSince(byCustomer[account.customer.id].orEmpty())) else account
        }
    }

    override fun observeMoneyLedger(): Flow<List<LedgerEntry>> = dao.observeMoneyEntries().map { rows -> rows.map { it.toDomain() } }

    override fun observeAccount(customerId: Long): Flow<CustomerAccount?> = dao.observeAccount(customerId).map { it?.toDomain() }

    override suspend fun getCustomer(customerId: Long): Customer? = dao.getCustomer(customerId)?.toDomain()

    override suspend fun findByPhone(phone: String): Customer? = dao.findByPhone(phone)?.toDomain()

    override suspend fun saveCustomer(customer: Customer, openingBalance: Money): SaveCustomerResult {
        val phone = customer.phone
        if (phone != null) {
            val owner = dao.findByPhone(phone)
            if (owner != null && owner.id != customer.id) return SaveCustomerResult.PhoneInUse
        }
        return try {
            database.withTransaction {
                val now = clock.millis()
                val entity = customer.toEntity(now)
                val id = if (customer.id == 0L) {
                    val newId = dao.insertCustomer(entity)
                    if (!openingBalance.isZero) {
                        dao.insertEntry(
                            LedgerEntryEntity(
                                customerId = newId,
                                type = LedgerEntryType.OPENING_BALANCE.name,
                                amountMinor = openingBalance.minorUnits,
                                invoiceId = null,
                                method = null,
                                reference = null,
                                note = null,
                                createdAt = now,
                            ),
                        )
                    }
                    newId
                } else {
                    dao.updateCustomer(entity)
                    customer.id
                }
                SaveCustomerResult.Saved(id)
            }
        } catch (e: SQLiteConstraintException) {
            // Another save took the number in the meantime.
            SaveCustomerResult.PhoneInUse
        }
    }

    override fun observeLedger(customerId: Long): Flow<List<LedgerEntry>> =
        dao.observeLedger(customerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeInvoices(customerId: Long): Flow<List<InvoiceSummary>> =
        billing.observeInvoicesForCustomer(customerId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun receivePayment(
        customerId: Long,
        amount: Money,
        method: PaymentMethod,
        reference: String?,
        note: String?,
        requestId: String,
    ): AccountPaymentResult {
        require(amount > Money.ZERO && method.movesMoney) { "Payments need a positive amount of cash, UPI or card" }
        return try {
            database.withTransaction {
                val now = clock.millis()
                when (val claim = operations.claim(requestId, "ACCOUNT_PAYMENT", now)) {
                    is RequestClaim.AlreadyDone -> AccountPaymentResult.Recorded(claim.resultId ?: 0)
                    RequestClaim.New -> {
                        dao.getCustomer(customerId) ?: throw NoCustomerException()
                        val balance = dao.balance(customerId)
                        if (amount.minorUnits > balance) throw OverpaymentException(balance)
                        val id = dao.insertEntry(
                            LedgerEntry(
                                customerId = customerId,
                                type = LedgerEntryType.PAYMENT,
                                amount = Money(-amount.minorUnits),
                                method = method,
                                reference = reference?.trim()?.ifEmpty { null },
                                note = note?.trim()?.ifEmpty { null },
                                createdAtMillis = now,
                            ).toEntity(),
                        )
                        operations.setRequestResult(requestId, id)
                        AccountPaymentResult.Recorded(id)
                    }
                }
            }
        } catch (e: OverpaymentException) {
            AccountPaymentResult.MoreThanDue(Money(e.balanceMinor))
        } catch (e: NoCustomerException) {
            AccountPaymentResult.CustomerMissing
        }
    }

    override fun observeAccountPayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentRecord>> =
        dao.observeAccountPayments(fromMillis, toMillis).map { rows -> rows.map { it.toPaymentRecord() } }
}
