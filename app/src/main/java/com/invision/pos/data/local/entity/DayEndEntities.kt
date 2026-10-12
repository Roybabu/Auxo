package com.invision.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A closed business day: the figures as they were when the day was closed and the cash that was counted.
 * Added in version 5. A day is closed once; rows are never updated or deleted by the app (the DAO has no
 * way to do either), so a closed day stays exactly as it was recorded.
 */
@Entity(
    tableName = "day_closings",
    indices = [Index(value = ["business_date"], unique = true)],
)
data class DayClosingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The day as ISO text, e.g. 2026-10-12, in the shop's time zone. */
    @ColumnInfo(name = "business_date") val businessDate: String,
    val bills: Int,
    val returns: Int,
    @ColumnInfo(name = "net_sales_minor") val netSalesMinor: Long,
    @ColumnInfo(name = "cash_received_minor") val cashReceivedMinor: Long,
    @ColumnInfo(name = "cash_refunded_minor") val cashRefundedMinor: Long,
    @ColumnInfo(name = "cash_from_accounts_minor") val cashFromAccountsMinor: Long,
    @ColumnInfo(name = "upi_net_minor") val upiNetMinor: Long,
    @ColumnInfo(name = "card_net_minor") val cardNetMinor: Long,
    @ColumnInfo(name = "on_account_net_minor") val onAccountNetMinor: Long,
    @ColumnInfo(name = "counted_cash_minor") val countedCashMinor: Long,
    val note: String?,
    @ColumnInfo(name = "closed_at") val closedAt: Long,
    @ColumnInfo(name = "closed_by") val closedBy: String,
)

/** Totals of saved payments for one method and kind, for working out a day's figures. */
data class PaymentTotalRow(
    val method: String,
    val kind: String,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

/** Customer account receipts for one method. */
data class ReceiptTotalRow(
    val method: String?,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

data class DayCountsRow(
    val bills: Int,
    val returns: Int,
    @ColumnInfo(name = "net_minor") val netMinor: Long,
)
