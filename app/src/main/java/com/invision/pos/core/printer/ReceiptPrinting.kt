package com.invision.pos.core.printer

import android.content.Context
import android.graphics.Bitmap
import com.invision.pos.R
import com.invision.pos.core.locale.LocaleContext
import com.invision.pos.core.receipt.Align
import com.invision.pos.core.receipt.ReceiptFonts
import com.invision.pos.core.receipt.ReceiptLabels
import com.invision.pos.core.receipt.ReceiptRaster
import com.invision.pos.core.receipt.ReceiptRenderer
import com.invision.pos.core.receipt.ReceiptText
import com.invision.pos.core.receipt.ThermalElement
import com.invision.pos.core.receipt.ThermalReceipt
import com.invision.pos.core.receipt.spec
import com.invision.pos.domain.billing.BusinessTime
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.Language
import com.invision.pos.domain.model.ReceiptOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Prints receipts on the shop's Bluetooth printer. The screens only see this, never Bluetooth itself. */
interface ReceiptPrinting {
    /** The chosen printer, or null when none was chosen. */
    val printer: StateFlow<SavedPrinter?>

    /** Prints [invoice] with the layout of [options], on the paper the printer was set up for. */
    suspend fun printInvoice(invoice: Invoice, options: ReceiptOptions, logo: Bitmap? = null): PrintResult

    /** Prints a short test receipt so the person can see the width and that the connection works. */
    suspend fun printTest(shopName: String, language: Language): PrintResult
}

class ThermalReceiptPrinter(
    context: Context,
    private val preferences: PrinterPreferences,
    private val transport: PrinterTransport,
) : ReceiptPrinting {
    private val context = context.applicationContext

    override val printer: StateFlow<SavedPrinter?> get() = preferences.printer

    override suspend fun printInvoice(invoice: Invoice, options: ReceiptOptions, logo: Bitmap?): PrintResult {
        val printer = preferences.printer.value ?: return PrintResult.NO_PRINTER
        val bytes = withContext(Dispatchers.Default) {
            val withPaper = options.copy(paper = printer.paper)
            val pages = ReceiptRenderer.pages(
                invoice, withPaper, BusinessTime.ZONE, logo,
                ReceiptLabels.from(context, withPaper.language), ReceiptFonts.from(context),
            )
            job(pages.firstOrNull(), printer)
        } ?: return PrintResult.UNSUPPORTED_PAPER
        return transport.send(printer.address, bytes)
    }

    override suspend fun printTest(shopName: String, language: Language): PrintResult {
        val printer = preferences.printer.value ?: return PrintResult.NO_PRINTER
        val bytes = withContext(Dispatchers.Default) {
            val receipt = testReceipt(LocaleContext.wrap(context, language), shopName, printer)
            val page = ReceiptRenderer.thermalPages(receipt, printer.paper, ReceiptFonts.from(context)).firstOrNull()
            job(page, printer)
        } ?: return PrintResult.UNSUPPORTED_PAPER
        return transport.send(printer.address, bytes)
    }

    private fun job(page: com.invision.pos.core.receipt.ReceiptPage?, printer: SavedPrinter): ByteArray? {
        val image = page?.let { ReceiptRaster.render(it, printer.paper) } ?: return null
        return EscPos.job(image, cut = printer.cutPaper)
    }

    companion object {
        /** The test receipt: shop name, what it is, a ruler as wide as one line, and a sentence. */
        internal fun testReceipt(localized: Context, shopName: String, printer: SavedPrinter): ThermalReceipt {
            val width = printer.paper.spec.charsPerLine
            val resources = localized.resources
            val elements = buildList {
                if (shopName.isNotBlank()) add(ThermalElement.Line(shopName.take(width / 2), Align.CENTER, bold = true, large = true))
                add(ThermalElement.Line(resources.getString(R.string.printer_test_title), Align.CENTER, bold = true))
                add(ThermalElement.Line(printer.name.take(width), Align.CENTER))
                add(ThermalElement.Divider())
                add(ThermalElement.Line(ruler(width), Align.LEFT))
                add(ThermalElement.Line(resources.getString(R.string.printer_test_width, width), Align.LEFT))
                add(ThermalElement.Divider())
                ReceiptText.wrap(resources.getString(R.string.printer_test_body), width)
                    .forEach { add(ThermalElement.Line(it, Align.CENTER)) }
            }
            return ThermalReceipt(width, elements)
        }

        /** "1234567890123..." as wide as the line, so cut-off edges show at once. */
        internal fun ruler(width: Int): String = buildString { for (i in 1..width) append(i % 10) }
    }
}
