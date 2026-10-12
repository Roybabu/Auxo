package com.auxo.pos.core.printer

import android.content.Context
import android.graphics.Bitmap
import com.auxo.pos.R
import com.auxo.pos.core.locale.LocaleContext
import com.auxo.pos.core.receipt.Align
import com.auxo.pos.core.receipt.ReceiptFonts
import com.auxo.pos.core.receipt.ReceiptLabels
import com.auxo.pos.core.receipt.ReceiptPage
import com.auxo.pos.core.receipt.ReceiptRaster
import com.auxo.pos.core.receipt.ReceiptRenderer
import com.auxo.pos.core.receipt.ReceiptText
import com.auxo.pos.core.receipt.ThermalElement
import com.auxo.pos.core.receipt.ThermalReceipt
import com.auxo.pos.core.receipt.spec
import com.auxo.pos.domain.billing.BusinessTime
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.Language
import com.auxo.pos.domain.model.ReceiptOptions
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
    /** Turns a drawn page into the bytes sent to the printer; null when the paper cannot be printed. Replaced in tests. */
    private val makeJob: (ReceiptPage?, SavedPrinter) -> ByteArray? = Companion::job,
) : ReceiptPrinting {
    private val context = context.applicationContext

    override val printer: StateFlow<SavedPrinter?> get() = preferences.printer

    override suspend fun printInvoice(invoice: Invoice, options: ReceiptOptions, logo: Bitmap?): PrintResult {
        val printer = preferences.printer.value ?: return PrintResult.NO_PRINTER
        val bytes = prepare {
            val withPaper = options.copy(paper = printer.paper)
            val pages = ReceiptRenderer.pages(
                invoice, withPaper, BusinessTime.ZONE, logo,
                ReceiptLabels.from(context, withPaper.language), ReceiptFonts.from(context),
            )
            makeJob(pages.firstOrNull(), printer)
        } ?: return PrintResult.UNSUPPORTED_PAPER
        if (bytes === Failed) return PrintResult.RENDER_FAILED
        return transport.send(printer.address, bytes)
    }

    override suspend fun printTest(shopName: String, language: Language): PrintResult {
        val printer = preferences.printer.value ?: return PrintResult.NO_PRINTER
        val bytes = prepare {
            val receipt = testReceipt(LocaleContext.wrap(context, language), shopName, printer)
            val page = ReceiptRenderer.thermalPages(receipt, printer.paper, ReceiptFonts.from(context)).firstOrNull()
            makeJob(page, printer)
        } ?: return PrintResult.UNSUPPORTED_PAPER
        if (bytes === Failed) return PrintResult.RENDER_FAILED
        return transport.send(printer.address, bytes)
    }

    /** Runs [block] off the main thread. A drawing error (or no memory left for a long receipt) gives [Failed], not a crash. */
    private suspend fun prepare(block: () -> ByteArray?): ByteArray? = withContext(Dispatchers.Default) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            Failed
        } catch (_: OutOfMemoryError) {
            Failed
        }
    }

    companion object {
        /** Marks that the receipt could not be drawn. Compared by identity, never sent. */
        private val Failed = ByteArray(0)

        /** Draws [page] in dots for [printer]'s roll and wraps it in the printer's commands; null for paper that is not a roll. */
        internal fun job(page: ReceiptPage?, printer: SavedPrinter): ByteArray? {
            val image = page?.let { ReceiptRaster.render(it, printer.paper) } ?: return null
            return EscPos.job(image, cut = printer.cutPaper)
        }

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
