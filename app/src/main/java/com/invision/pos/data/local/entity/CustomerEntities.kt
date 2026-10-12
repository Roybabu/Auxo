package com.invision.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Added in version 3. A mobile number belongs to at most one customer. */
@Entity(
    tableName = "customers",
    indices = [Index("name"), Index(value = ["phone"], unique = true)],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String?,
    val email: String?,
    val gstin: String?,
    @ColumnInfo(name = "state_code") val stateCode: String?,
    val address: String?,
    @ColumnInfo(name = "credit_limit_minor") val creditLimitMinor: Long?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Customer account entries. Added in version 3. The balance is the sum of
 * amount_minor: positive means the customer owes the store.
 */
@Entity(
    tableName = "customer_ledger",
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customer_id"]),
    ],
    indices = [Index("customer_id"), Index("created_at"), Index("invoice_id")],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "customer_id") val customerId: Long,
    /** [com.invision.pos.domain.model.LedgerEntryType] name. */
    val type: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "invoice_id") val invoiceId: Long?,
    /** [com.invision.pos.domain.model.PaymentMethod] name for payments. */
    val method: String?,
    val reference: String?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Loyalty points added or taken off. Added in version 6; 0 for money entries. */
    @ColumnInfo(name = "points", defaultValue = "0") val points: Int = 0,
)

/**
 * The shop's loyalty rules, one row (id 1). Added in version 6. Kept apart from the store settings so saving
 * those can never switch loyalty on or change its rules.
 */
@Entity(tableName = "loyalty_settings")
data class LoyaltySettingsEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean,
    @ColumnInfo(name = "spend_per_point_minor") val spendPerPointMinor: Long,
    @ColumnInfo(name = "point_value_minor") val pointValueMinor: Long,
)

data class CustomerAccountRow(
    @Embedded val customer: CustomerEntity,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
    @ColumnInfo(name = "last_activity") val lastActivity: Long?,
    @ColumnInfo(name = "points_balance") val pointsBalance: Int = 0,
)

data class LedgerEntryRow(
    @Embedded val entry: LedgerEntryEntity,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String?,
)

/** A payment received against an account, for collection totals. */
data class AccountPaymentRow(
    @Embedded val entry: LedgerEntryEntity,
    @ColumnInfo(name = "customer_name") val customerName: String,
)
