// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.receipt

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.auxo.pos.domain.customers.DuesReminder
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.ReceiptOptions
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/**
 * Hands bills and reminders to WhatsApp through Android's own sharing. Nothing is ever sent for the user: WhatsApp
 * opens with the file or text ready, and the person chooses the chat and presses send. If WhatsApp is not
 * installed, the normal share sheet opens instead. No WhatsApp account, API or permission is involved.
 */
object WhatsAppShare {

    /** WhatsApp and WhatsApp Business, in the order they are tried. */
    val PACKAGES: List<String> = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** The invoice PDF as a share intent, aimed at WhatsApp package [packageName] (or any app when it is null). */
    fun invoiceIntent(context: Context, file: File, invoice: Invoice, caption: String, packageName: String?): Intent {
        val uri = FileProvider.getUriForFile(context, ReceiptOutput.fileProviderAuthority(context), file)
        return Intent(Intent.ACTION_SEND).apply {
            type = ReceiptOutput.PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            putExtra(Intent.EXTRA_SUBJECT, "${invoice.seller.name} ${invoice.number}")
            clipData = ClipData.newRawUri(invoice.number, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (packageName != null) setPackage(packageName)
        }
    }

    /**
     * A chat with [number] (country code and digits) with [message] typed in, or, without a number, WhatsApp's own
     * chat picker with the text ready. Aimed at [packageName], or any app when it is null.
     */
    fun textIntent(number: String?, message: String, packageName: String?): Intent {
        val intent = if (number != null) {
            Intent(Intent.ACTION_VIEW, DuesReminder.whatsAppLink(number, message).toUri())
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
        }
        if (packageName != null) intent.setPackage(packageName)
        return intent
    }

    /**
     * Opens WhatsApp with [message] ready for [number] (or for the user to pick a chat). Falls back to the share
     * sheet when WhatsApp is not installed. Returns false if nothing could open.
     */
    /** Starts [intent]; from a context that is not an Activity (a service, a test) Android needs a new task for it. */
    private fun start(context: Context, intent: Intent) {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openText(context: Context, number: String?, message: String, chooserTitle: String): Boolean {
        for (pkg in PACKAGES) {
            try {
                start(context, textIntent(number, message, pkg))
                return true
            } catch (e: ActivityNotFoundException) {
                // Try the next WhatsApp.
            }
        }
        return try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            start(context, Intent.createChooser(send, chooserTitle))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Writes the invoice PDF off the main thread and opens WhatsApp with it attached and [caption] typed in, or the
     * share sheet when WhatsApp is not installed. Returns false if the file could not be written or nothing could open.
     */
    suspend fun shareInvoice(
        context: Context,
        invoice: Invoice,
        options: ReceiptOptions,
        caption: String,
        chooserTitle: String,
        logo: Bitmap? = null,
    ): Boolean {
        val file = runCatching { withContext(Dispatchers.IO) { ReceiptOutput.writePdfFile(context, invoice, options, logo = logo) } }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return false
        return try {
            for (pkg in PACKAGES) {
                try {
                    start(context, invoiceIntent(context, file, invoice, caption, pkg))
                    return true
                } catch (e: ActivityNotFoundException) {
                    // Try the next WhatsApp.
                }
            }
            start(context, Intent.createChooser(invoiceIntent(context, file, invoice, caption, null), chooserTitle))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            // The file is outside the paths the FileProvider shares.
            false
        }
    }
}
