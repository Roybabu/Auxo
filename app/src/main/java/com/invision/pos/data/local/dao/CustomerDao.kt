package com.invision.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.invision.pos.data.local.entity.AccountPaymentRow
import com.invision.pos.data.local.entity.CustomerAccountRow
import com.invision.pos.data.local.entity.CustomerEntity
import com.invision.pos.data.local.entity.LedgerEntryEntity
import com.invision.pos.data.local.entity.LedgerEntryRow
import kotlinx.coroutines.flow.Flow

/** Thrown inside a payment transaction when the amount is more than the customer owes. */
class OverpaymentException(val balanceMinor: Long) : RuntimeException("Payment is more than the balance")

@Dao
abstract class CustomerDao {

    @Query(
        "SELECT c.*, COALESCE(SUM(l.amount_minor), 0) AS balance_minor, MAX(l.created_at) AS last_activity " +
            "FROM customers c LEFT JOIN customer_ledger l ON l.customer_id = c.id " +
            "GROUP BY c.id ORDER BY c.name COLLATE NOCASE",
    )
    abstract fun observeAccounts(): Flow<List<CustomerAccountRow>>

    @Query(
        "SELECT c.*, COALESCE(SUM(l.amount_minor), 0) AS balance_minor, MAX(l.created_at) AS last_activity " +
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

    @Insert
    abstract suspend fun insertEntry(entry: LedgerEntryEntity): Long

    @Query(
        "SELECT l.*, c.name AS customer_name FROM customer_ledger l JOIN customers c ON c.id = l.customer_id " +
            "WHERE l.type = 'PAYMENT' AND l.created_at >= :fromMillis AND l.created_at < :toMillis " +
            "ORDER BY l.created_at DESC, l.id DESC",
    )
    abstract fun observeAccountPayments(fromMillis: Long, toMillis: Long): Flow<List<AccountPaymentRow>>
}
