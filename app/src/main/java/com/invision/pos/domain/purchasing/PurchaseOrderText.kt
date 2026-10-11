package com.invision.pos.domain.purchasing

import com.invision.pos.domain.billing.IndianNumberFormat
import com.invision.pos.domain.model.PurchaseOrder
import com.invision.pos.domain.model.StockUnit
import java.time.format.DateTimeFormatter

/** A purchase order as plain text, for sending to a supplier by WhatsApp, SMS or email. */
object PurchaseOrderText {
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    fun format(order: PurchaseOrder, storeName: String, storePhone: String = ""): String = buildString {
        appendLine("Purchase order ${order.number}")
        if (storeName.isNotBlank()) appendLine("From: $storeName" + if (storePhone.isNotBlank()) " ($storePhone)" else "")
        if (order.supplierName.isNotBlank()) appendLine("To: ${order.supplierName}")
        order.expectedDate?.let { appendLine("Needed by: ${DATE.format(it)}") }
        appendLine()
        order.lines.forEachIndexed { index, line ->
            val name = listOf(line.productName, line.variantLabel).filter { it.isNotBlank() }.joinToString(" ")
            val quantity = when (line.stockUnit) {
                StockUnit.UNIT -> "${line.quantityOrdered} pcs"
                else -> line.stockUnit.format(line.quantityOrdered)
            }
            appendLine("${index + 1}. $name - $quantity @ Rs. ${IndianNumberFormat.amount(line.unitCost)}/${line.stockUnit.entrySymbol}")
        }
        appendLine()
        append("Estimated total: Rs. ${IndianNumberFormat.amount(order.total)}")
        order.note?.let {
            appendLine()
            append("Note: $it")
        }
    }
}
