package com.invision.pos.domain.purchasing

import com.invision.pos.domain.billing.InvoiceNumbers
import java.time.Instant
import java.time.ZoneId

/** Numbers for purchase orders ("PO/26-27/00001") and goods receipts ("GRN/26-27/00001"). */
object PurchaseNumbers {
    const val ORDER_PREFIX = "PO"
    const val RECEIPT_PREFIX = "GRN"

    fun financialYear(epochMillis: Long, zone: ZoneId): Int =
        InvoiceNumbers.financialYearStart(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())

    fun order(financialYearStart: Int, sequence: Long): String = InvoiceNumbers.format(ORDER_PREFIX, financialYearStart, sequence)

    fun receipt(financialYearStart: Int, sequence: Long): String = InvoiceNumbers.format(RECEIPT_PREFIX, financialYearStart, sequence)

    // Kept apart from invoice counters, even if an invoice prefix is also "PO".
    fun orderSequence(financialYearStart: Int): String = "purchase-order|$financialYearStart"

    fun receiptSequence(financialYearStart: Int): String = "goods-receipt|$financialYearStart"
}
