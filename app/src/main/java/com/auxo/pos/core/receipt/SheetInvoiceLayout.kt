package com.auxo.pos.core.receipt

import com.auxo.pos.domain.billing.AmountInWords
import com.auxo.pos.domain.billing.IndianNumberFormat
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.InvoiceLine
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.ReceiptOptions
import com.auxo.pos.domain.model.upiLinkFor
import com.auxo.pos.domain.model.ReceiptPaper
import java.time.ZoneId

/** A column of the item table on A5/A4 invoices. */
data class SheetColumn(
    val title: String,
    /** Share of the table width. */
    val weight: Float,
    val alignRight: Boolean,
    /** The column that holds the item name; it wraps onto more lines instead of being cut short. */
    val isItem: Boolean = false,
    val value: (index: Int, line: InvoiceLine) -> String,
)

/** Everything printed on an A5/A4 invoice, as text, ready to be drawn. */
data class SheetInvoice(
    val title: String,
    val sellerLines: List<String>,
    val sellerName: String,
    val detailLines: List<Pair<String, String>>,
    val buyerHeading: String,
    val buyerLines: List<String>,
    val columns: List<SheetColumn>,
    val rows: List<List<String>>,
    /** Discount note shown under an item name, if any. */
    val rowNotes: List<String?>,
    val totals: List<Pair<String, String>>,
    val grandTotal: Pair<String, String>,
    val amountInWords: String,
    val taxSummaryHeader: List<String>,
    val taxSummaryRows: List<List<String>>,
    val paymentLines: List<String>,
    val notes: List<String>,
    val footer: String,
    /** Last line of the invoice, asking the customer for feedback. */
    val feedbackNote: String,
    val signatureLabel: String,
    /** The caption under the signature line. */
    val signatoryCaption: String,
    /** Page number wording with %1\$d (page) and %2\$d (pages). */
    val pageNumberTemplate: String,
    /** Added after the title on pages after the first. */
    val continuedLabel: String,
    val barcode: String?,
    /** UPI payment link to draw as a QR, and the line printed under it; null when no QR is printed. */
    val upiQr: String? = null,
    val upiCaption: String = "",
)

object SheetInvoiceLayout {

    fun build(
        invoice: Invoice,
        options: ReceiptOptions,
        paper: ReceiptPaper,
        zone: ZoneId,
        labels: ReceiptLabels = ReceiptLabels(),
    ): SheetInvoice {
        val seller = invoice.seller
        val gst = invoice.gstRegistered
        val isReturn = invoice.type == InvoiceType.RETURN

        val sellerLines = buildList {
            if (seller.address.isNotBlank()) addAll(seller.address.lines().filter { it.isNotBlank() })
            if (seller.phone.isNotBlank()) add(fillIn(labels.phone, seller.phone))
            if (seller.gstin.isNotBlank()) add(fillIn(labels.gstin, seller.gstin))
        }
        val details = buildList {
            add((if (invoice.isQuotation) labels.quotationNo else if (isReturn) labels.creditNoteNo else labels.invoiceNo) to invoice.number)
            add(labels.dateHeading to ReceiptText.dateTime(invoice.createdAtMillis, zone))
            invoice.quotationValidUntil?.let { add(labels.validUntilHeading to ReceiptText.date(it)) }
            ReceiptText.serviceLine(invoice, labels)?.let { add(labels.serviceHeading to it) }
            invoice.originalInvoiceNumber?.let { add(labels.againstInvoice to it) }
        }
        val buyerLines = if (options.showCustomer) {
            buildList {
                add(invoice.buyer.name ?: labels.walkInCustomer)
                invoice.buyer.phone?.let { add(fillIn(labels.phone, ReceiptText.maskPhone(it))) }
                invoice.buyer.gstin?.let { add(fillIn(labels.gstin, it)) }
            }
        } else {
            emptyList()
        }

        val showHsn = gst && options.showHsn
        val wide = paper == ReceiptPaper.A4
        val columns = buildList {
            add(SheetColumn("#", 0.05f, alignRight = false) { i, _ -> (i + 1).toString() })
            add(SheetColumn(labels.item, if (wide) 0.30f else 0.34f, alignRight = false, isItem = true) { _, l ->
                listOf(l.productName, l.variantLabel).filter { it.isNotBlank() }.joinToString(" · ")
            })
            if (showHsn) add(SheetColumn(labels.hsnHeading, 0.10f, alignRight = false) { _, l -> l.hsnCode.orEmpty() })
            add(SheetColumn(labels.qtyHeading, 0.07f, alignRight = true) { _, l -> l.quantity.toString() })
            add(SheetColumn(labels.rateHeading, 0.12f, alignRight = true) { _, l -> IndianNumberFormat.amount(l.unitPrice) })
            if (wide) add(SheetColumn(labels.discHeading, 0.10f, alignRight = true) { _, l -> if (l.discount.isZero) "" else IndianNumberFormat.amount(l.discount) })
            if (gst && wide) add(SheetColumn(labels.taxableHeading, 0.12f, alignRight = true) { _, l -> IndianNumberFormat.amount(l.taxable) })
            if (gst) add(SheetColumn(labels.gstHeading, 0.07f, alignRight = true) { _, l -> ReceiptText.rate(l.gstRateBasisPoints) })
            add(SheetColumn(labels.amount, 0.14f, alignRight = true) { _, l -> IndianNumberFormat.amount(l.total) })
        }
        val rows = invoice.lines.mapIndexed { i, line -> columns.map { it.value(i, line) } }
        val rowNotes = invoice.lines.map { line ->
            listOfNotNull(
                ReceiptText.mrpDiscount(line)?.let { fillIn(labels.mrpDiscountNote, IndianNumberFormat.amount(line.mrp!!), IndianNumberFormat.amount(it)) },
                if (!wide && !line.discount.isZero) {
                    fillIn(labels.discountNote, IndianNumberFormat.amount(line.discount)) + (line.discountReason?.let { " ($it)" } ?: "")
                } else {
                    null
                },
            ).joinToString(" · ").ifEmpty { null }
        }

        val totals = buildList {
            add(labels.subtotal to IndianNumberFormat.amount(invoice.subtotal))
            if (!invoice.discountTotal.isZero) add(labels.discount to "-" + IndianNumberFormat.amount(invoice.discountTotal))
            if (gst) {
                add(labels.taxableValue to IndianNumberFormat.amount(invoice.taxableTotal))
                if (invoice.interState) {
                    add(labels.igst to IndianNumberFormat.amount(invoice.igstTotal))
                } else {
                    add(labels.cgst to IndianNumberFormat.amount(invoice.cgstTotal))
                    add(labels.sgst to IndianNumberFormat.amount(invoice.sgstTotal))
                }
            }
            if (!invoice.roundOff.isZero) {
                val sign = if (invoice.roundOff > Money.ZERO) "+" else ""
                add(labels.roundOff to sign + IndianNumberFormat.amount(invoice.roundOff))
            }
        }
        val grandLabel = if (isReturn) labels.totalRefund else labels.grandTotal

        val (summaryHeader, summaryRows) = if (gst && options.showTaxBreakup) {
            val header = if (invoice.interState) {
                listOf(labels.gstRateHeading, labels.taxableValue, labels.igst, labels.totalTaxHeading)
            } else {
                listOf(labels.gstRateHeading, labels.taxableValue, labels.cgst, labels.sgst, labels.totalTaxHeading)
            }
            val rowsByRate = invoice.lines.groupBy { it.gstRateBasisPoints }.toSortedMap().map { (rate, lines) ->
                val taxable = lines.fold(Money.ZERO) { a, l -> a + l.taxable }
                val cgst = lines.fold(Money.ZERO) { a, l -> a + l.cgst }
                val sgst = lines.fold(Money.ZERO) { a, l -> a + l.sgst }
                val igst = lines.fold(Money.ZERO) { a, l -> a + l.igst }
                if (invoice.interState) {
                    listOf(ReceiptText.rate(rate), IndianNumberFormat.amount(taxable), IndianNumberFormat.amount(igst), IndianNumberFormat.amount(igst))
                } else {
                    listOf(
                        ReceiptText.rate(rate), IndianNumberFormat.amount(taxable), IndianNumberFormat.amount(cgst),
                        IndianNumberFormat.amount(sgst), IndianNumberFormat.amount(cgst + sgst),
                    )
                }
            }
            header to rowsByRate
        } else {
            emptyList<String>() to emptyList()
        }

        val payments = invoice.payments.map { p ->
            buildString {
                append(ReceiptText.paymentLabel(p, labels, sheet = true) + ": ")
                append(IndianNumberFormat.rupees(p.amount))
                p.reference?.let { append(" " + fillIn(labels.refNote, it)) }
                if (p.tendered != null && p.change != null) {
                    append(" · " + fillIn(labels.receivedChange, IndianNumberFormat.rupees(p.tendered), IndianNumberFormat.rupees(p.change)))
                }
            }
        }

        val notes = buildList {
            if (invoice.isQuotation) add(labels.notTaxInvoice)
            invoice.note?.let { add(fillIn(labels.returnReason, it)) }
            invoice.discountReason?.let { add(fillIn(labels.discountReason, it)) }
            if (wide) {
                invoice.lines.mapNotNull { it.discountReason }.distinct().forEach { add(fillIn(labels.discountReason, it)) }
            }
            if (gst && invoice.pricesIncludeTax) add(labels.pricesIncludeGst)
            if (!gst) add(labels.noTaxCharged)
            if (options.showSavings && !isReturn && invoice.savings > Money.ZERO) {
                add(fillIn(labels.savedOnMrp, IndianNumberFormat.rupees(invoice.savings)))
            }
        }

        return SheetInvoice(
            title = ReceiptText.title(invoice, labels),
            sellerName = seller.name,
            sellerLines = sellerLines,
            detailLines = details,
            buyerHeading = if (isReturn) labels.customerHeading else labels.billTo,
            buyerLines = buyerLines,
            columns = columns,
            rows = rows,
            rowNotes = rowNotes,
            totals = totals,
            grandTotal = grandLabel to IndianNumberFormat.rupees(invoice.grandTotal),
            amountInWords = AmountInWords.rupees(invoice.grandTotal, labels.language),
            taxSummaryHeader = summaryHeader,
            taxSummaryRows = summaryRows,
            paymentLines = payments,
            notes = notes,
            footer = options.footer,
            feedbackNote = labels.feedbackNote,
            signatureLabel = fillIn(labels.signatureFor, seller.name),
            signatoryCaption = labels.authorisedSignatory,
            pageNumberTemplate = labels.pageOf,
            continuedLabel = labels.continued,
            barcode = invoice.number.takeIf { options.showInvoiceBarcode && it.isNotEmpty() && !invoice.isQuotation },
            upiQr = options.upiLinkFor(invoice),
            upiCaption = fillIn(labels.scanToPay, IndianNumberFormat.rupees(invoice.grandTotal)),
        )
    }
}
