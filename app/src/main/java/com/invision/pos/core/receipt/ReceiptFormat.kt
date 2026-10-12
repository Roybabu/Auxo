package com.invision.pos.core.receipt

import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceLine
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.ReceiptPaper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Physical size and text width of each paper option. */
data class PaperSpec(
    val paperWidthMm: Float,
    /** Height for sheets; thermal receipts grow to fit their content. */
    val paperHeightMm: Float?,
    val marginMm: Float,
    /** Characters per line for thermal layouts. */
    val charsPerLine: Int,
) {
    val printableWidthMm: Float get() = paperWidthMm - marginMm * 2
}

val ReceiptPaper.spec: PaperSpec
    get() = when (this) {
        ReceiptPaper.THERMAL_58 -> PaperSpec(paperWidthMm = 58f, paperHeightMm = null, marginMm = 5f, charsPerLine = 32)
        ReceiptPaper.THERMAL_76 -> PaperSpec(paperWidthMm = 76f, paperHeightMm = null, marginMm = 6f, charsPerLine = 42)
        ReceiptPaper.THERMAL_80 -> PaperSpec(paperWidthMm = 80f, paperHeightMm = null, marginMm = 4f, charsPerLine = 48)
        ReceiptPaper.A5 -> PaperSpec(paperWidthMm = 148f, paperHeightMm = 210f, marginMm = 10f, charsPerLine = 0)
        ReceiptPaper.A4 -> PaperSpec(paperWidthMm = 210f, paperHeightMm = 297f, marginMm = 12f, charsPerLine = 0)
    }

val ReceiptPaper.isThermal: Boolean get() = spec.paperHeightMm == null

internal object ReceiptText {
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a", Locale.ENGLISH)

    fun dateTime(epochMillis: Long, zone: ZoneId): String =
        DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone)).replace("am", "AM").replace("pm", "PM")

    fun title(invoice: Invoice, labels: ReceiptLabels): String = when {
        invoice.type == InvoiceType.RETURN -> labels.creditNote
        invoice.gstRegistered -> labels.taxInvoice
        else -> labels.billOfSupply
    }

    fun method(method: PaymentMethod, labels: ReceiptLabels): String = when (method) {
        PaymentMethod.CASH -> labels.methodCash
        PaymentMethod.UPI -> labels.methodUpi
        PaymentMethod.CARD -> labels.methodCard
        PaymentMethod.CREDIT -> labels.methodAccount
    }

    /** "Paid by Cash", "On account", "Refund (UPI)" or "Credited to account". */
    fun paymentLabel(payment: PaymentRecord, labels: ReceiptLabels, sheet: Boolean = false): String {
        val method = method(payment.method, labels)
        val refund = payment.kind == PaymentKind.REFUND
        return when {
            payment.method == PaymentMethod.CREDIT && refund -> labels.creditedToAccount
            payment.method == PaymentMethod.CREDIT -> labels.onAccount
            refund && sheet -> fillIn(labels.refundedBy, method)
            refund -> fillIn(labels.refundParen, method)
            else -> fillIn(labels.paidBy, method)
        }
    }

    /**
     * The customer's phone number as printed on the invoice copy: the last six digits are
     * hidden (other characters such as the + and spaces are kept). With six digits or fewer,
     * all of them are hidden.
     */
    fun maskPhone(phone: String): String {
        var digitsLeft = phone.count { it.isDigit() }
        val hidden = minOf(6, digitsLeft)
        return buildString {
            for (c in phone) {
                if (c.isDigit()) {
                    append(if (digitsLeft <= hidden) 'X' else c)
                    digitsLeft--
                } else {
                    append(c)
                }
            }
        }
    }

    /** What the customer paid below the printed MRP on this line (MRP minus selling price, times quantity), or null if there is none. */
    fun mrpDiscount(line: InvoiceLine): Money? {
        val mrp = line.mrp ?: return null
        val perUnit = mrp - line.unitPrice
        return if (perUnit > Money.ZERO) perUnit * line.quantity else null
    }

    fun rate(basisPoints: Int): String = com.invision.pos.domain.model.formatRatePercent(basisPoints) + "%"

    /** Splits [text] into lines of at most [width] characters, breaking at spaces where possible. */
    fun wrap(text: String, width: Int): List<String> {
        if (width <= 0) return listOf(text)
        val result = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = StringBuilder()
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                var remaining = word
                while (remaining.length > width) {
                    if (line.isNotEmpty()) {
                        result += line.toString()
                        line = StringBuilder()
                    }
                    result += remaining.take(width)
                    remaining = remaining.drop(width)
                }
                when {
                    line.isEmpty() -> line.append(remaining)
                    line.length + 1 + remaining.length <= width -> line.append(' ').append(remaining)
                    else -> {
                        result += line.toString()
                        line = StringBuilder(remaining)
                    }
                }
            }
            if (line.isNotEmpty()) result += line.toString()
        }
        return result.ifEmpty { listOf("") }
    }
}
