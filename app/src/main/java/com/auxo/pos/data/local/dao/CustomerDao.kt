// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.auxo.pos.data.local.entity.AccountPaymentRow
import com.auxo.pos.data.local.entity.CustomerAccountRow
import com.auxo.pos.data.local.entity.CustomerEntity
import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.local.entity.LedgerEntryRow
import com.auxo.pos.data.local.entity.LoyaltySettingsEntity
import kotlinx.coroutines.flow.Flow

/** Thrown inside a payment transaction when the amount is more than the customer owes. */
class OverpaymentException(val balanceMinor: Long) : RuntimeException("Payment is more than the balance")

@Dao
abstract class CustomerDao {

    /** Every money entry (no loyalty points), grouped by customer and oldest first, to work out how long money has been owed. */
    @Query(
        "SELECT * FROM customer_ledger WHERE type IN ('OPENING_BALANCE', 'SALE', 'RETURN', 'PAYMENT') " +
            "ORDER BY customer_id, created_at, id",
    )
    abstract fun observeMoneyEntries(): Flow<List<LedgerEntryEntity>>

    @Query(
        "SELECT c.*, COALESCE(SUM(l.amount_minor), 0) AS balance_minor, MAX(CASE WHEN l.type = 'PROMISE' THEN NULL ELSE l.created_at END) AS last_activity, " +
            "COALESCE(SUM(l.points), 0) AS points_balance " +
            "FROM customers c LEFT JOIN customer_ledger l ON l.customer_id = c.id " +
            "GROUP BY c.id ORDER BY c.name COLLATE NOCASE",
    )
    abstract fun observeAccounts(): Flow<List<CustomerAccountRow>>

    @Query(
        "SELECT c.*, COALESCE(SUM(l.amount_minor), 0) AS balance_minor, MAX(CASE WHEN l.type = 'PROMISE' THEN NULL ELSE l.created_at END) AS last_activity, " +
            "COALESCE(SUM(l.points), 0) AS points_balance " +
            "FROM customers c LEFT JOIN customer_ledger l ON l.customer_id = c.id WHERE c.id = :customerId GROUP BY c.id",
    )
    abstract fun observeAccount(customerId: Long): Flow<CustomerAccountRow?>

    @Query("SELECT * FROM customers WHERE id = :customerId")
    abstract suspend fun getCustomer(customerId: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    abstract suspend fun findByPhone(phone: String): CustomerEntity?

    @Insert
    abstract suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    abstract suspend fun updateCustomer(customer: CustomerEntity)

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM customer_ledger WHERE customer_id = :customerId")
    abstract suspend fun balance(customerId: Long): Long

    @Query(
        "SELECT l.*, i.number AS invoice_number FROM customer_ledger l LEFT JOIN invoices i ON i.id = l.invoice_id " +
            "WHERE l.customer_id = :customerId ORDER BY l.created_at, l.id",
    )
    abstract fun observeLedger(customerId: Long): Flow<List<LedgerEntryRow>>

    /** Every promised payment date ever recorded, oldest first; the last one for a customer is the one that counts. */
    @Query("SELECT * FROM customer_ledger WHERE type = 'PROMISE' ORDER BY customer_id, created_at, id")
    abstract fun observePromises(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM customer_ledger WHERE customer_id = :customerId AND type IN ('OPENING_BALANCE', 'SALE', 'RETURN', 'PAYMENT') ORDER BY created_at, id")
    abstract suspend fun moneyEntriesOf(customerId: Long): List<LedgerEntryEntity>

    @Insert
    abstract suspend fun insertEntry(entry: LedgerEntryEntity): Long

    @Query(
        "SELECT l.*, c.name AS customer_name FROM customer_ledger l JOIN customers c ON c.id = l.customer_id " +
            "WHERE l.type = 'PAYMENT' AND l.created_at >= :fromMillis AND l.created_at < :toMillis " +
            "ORDER BY l.created_at DESC, l.id DESC",
    )
    abstract fun observeAccountPayments(fromMillis: Long, toMillis: Long): Flow<List<AccountPaymentRow>>

    @Query("SELECT COALESCE(SUM(points), 0) FROM customer_ledger WHERE customer_id = :customerId")
    abstract suspend fun pointsBalance(customerId: Long): Int

    /** Points already taken back for returns of one sale (the returns are credit notes pointing at the sale). */
    @Query(
        "SELECT COALESCE(SUM(l.points), 0) FROM customer_ledger l JOIN invoices i ON i.id = l.invoice_id " +
            "WHERE l.type = 'POINTS_REVERSED' AND i.original_invoice_id = :saleId",
    )
    abstract suspend fun pointsReversedFor(saleId: Long): Int

    /** Points earned on one sale. */
    @Query("SELECT COALESCE(SUM(points), 0) FROM customer_ledger WHERE type = 'POINTS_EARNED' AND invoice_id = :saleId")
    abstract suspend fun pointsEarnedOn(saleId: Long): Int

    @Query("SELECT * FROM loyalty_settings WHERE id = 1")
    abstract fun observeLoyalty(): Flow<LoyaltySettingsEntity?>

    @Query("SELECT * FROM loyalty_settings WHERE id = 1")
    abstract suspend fun getLoyalty(): LoyaltySettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertLoyalty(settings: LoyaltySettingsEntity)
}
