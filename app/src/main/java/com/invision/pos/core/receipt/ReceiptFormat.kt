package com.invision.pos.core.receipt

import com.invision.pos.domain.billing.IndianStates
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.PaymentMethod
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

/** Fixed wording printed on receipts. */
data class ReceiptLabels(
    val taxInvoice: String = "TAX INVOICE",
    val billOfSupply: String = "BILL OF SUPPLY",
    val creditNote: String = "CREDIT NOTE",
    val walkInCustomer: String = "Walk-in Customer",
    val thankYou: String = "",
)

internal object ReceiptText {
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a", Locale.ENGLISH)

    fun dateTime(epochMillis: Long, zone: ZoneId): String =
        DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone)).replace("am", "AM").replace("pm", "PM")

    fun title(invoice: Invoice, labels: ReceiptLabels): String = when {
        invoice.type == InvoiceType.RETURN -> labels.creditNote
        invoice.gstRegistered -> labels.taxInvoice
        else -> labels.billOfSupply
    }

    fun method(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.UPI -> "UPI"
        PaymentMethod.CARD -> "Card"
    }

    fun placeOfSupply(code: String): String = IndianStates.byCode(code)?.let { "${it.name} (${it.code})" } ?: code

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
