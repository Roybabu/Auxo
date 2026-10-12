package com.invision.pos.core.receipt

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.invision.pos.domain.billing.BusinessTime
import com.invision.pos.domain.billing.KitchenTicket
import com.invision.pos.domain.model.ReceiptPaper
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prints and shares kitchen order tickets. The ticket always goes on a roll, even when bills use A4 or A5. */
object KitchenTicketOutput {

    private const val DIR = "receipts"
    private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000

    /** The roll a ticket is printed on: the receipt roll if bills use one, otherwise 80 mm. */
    fun ticketPaper(receiptPaper: ReceiptPaper): ReceiptPaper =
        if (receiptPaper.isThermal) receiptPaper else ReceiptPaper.THERMAL_80

    fun pages(ticket: KitchenTicket, receiptPaper: ReceiptPaper, labels: KotLabels, zone: ZoneId = BusinessTime.ZONE, fonts: ReceiptFonts = ReceiptFonts.system): List<ReceiptPage> {
        val paper = ticketPaper(receiptPaper)
        return ReceiptRenderer.thermalPages(KitchenTicketLayout.build(ticket, paper.spec.charsPerLine, zone, labels), paper, fonts)
    }

    /** File name such as "KOT-001.pdf". */
    fun fileName(ticket: KitchenTicket): String = ticket.kotNumber.ifEmpty { "kot" }.replace(Regex("[^A-Za-z0-9-]"), "-") + ".pdf"

    /** Sends the ticket to the Android print system. Needs an Activity context. Returns false if printing could not start. */
    fun print(context: Context, ticket: KitchenTicket, receiptPaper: ReceiptPaper, labels: KotLabels): Boolean {
        val printManager = context.getSystemService(PrintManager::class.java) ?: return false
        val paper = ticketPaper(receiptPaper)
        val pages = pages(ticket, receiptPaper, labels, fonts = ReceiptFonts.from(context))
        val first = pages.first()
        val attributes = PrintAttributes.Builder()
            .setMediaSize(
                PrintAttributes.MediaSize(
                    "invision_kot_${paper.name.lowercase()}",
                    "${paper.spec.paperWidthMm.roundToInt()} mm roll",
                    (first.widthPt / 72f * 1000f).roundToInt(),
                    (first.heightPt / 72f * 1000f).roundToInt(),
                ),
            )
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        return try {
            printManager.print(ticket.kotNumber.ifEmpty { "KOT" }, ReceiptPrintAdapter(pages, fileName(ticket)), attributes)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun writeFile(context: Context, ticket: KitchenTicket, receiptPaper: ReceiptPaper, labels: KotLabels): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - STALE_AFTER_MILLIS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val file = File(dir, fileName(ticket))
        FileOutputStream(file).use { ReceiptOutput.writePdf(pages(ticket, receiptPaper, labels, fonts = ReceiptFonts.from(context)), it) }
        return file
    }

    /** Writes the ticket PDF off the main thread and opens the share sheet. Returns false if that failed. */
    suspend fun share(context: Context, ticket: KitchenTicket, receiptPaper: ReceiptPaper, labels: KotLabels, chooserTitle: String): Boolean {
        val file = runCatching { withContext(Dispatchers.IO) { writeFile(context, ticket, receiptPaper, labels) } }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return false
        return try {
            val uri = FileProvider.getUriForFile(context, ReceiptOutput.fileProviderAuthority(context), file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = ReceiptOutput.PDF_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${ticket.shopName} ${ticket.kotNumber}")
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
