// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.receipt

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.billing.SampleSales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WhatsAppShareTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun invoiceIntent_attachesThePdf_withACaption_aimedAtWhatsApp() {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "INV-1.pdf").apply { writeText("%PDF") }
        val invoice = SampleSales.shirtsAndBiscuits()
        val intent = WhatsAppShare.invoiceIntent(context, file, invoice, "Invoice INV from Shop", "com.whatsapp")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals("Invoice INV from Shop", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java).toString().endsWith("INV-1.pdf"))
        assertNull(WhatsAppShare.invoiceIntent(context, file, invoice, "x", null).`package`)
    }

    @Test
    fun textIntent_withANumber_opensThatChatWithTheMessageTypedIn() {
        val intent = WhatsAppShare.textIntent("919830012345", "Hello there", "com.whatsapp")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://wa.me/919830012345?text=Hello%20there", intent.data.toString())
        assertEquals("com.whatsapp", intent.`package`)
    }

    @Test
    fun textIntent_withoutANumber_letsThePersonPickTheChat() {
        val intent = WhatsAppShare.textIntent(null, "Hello there", "com.whatsapp.w4b")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("Hello there", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("com.whatsapp.w4b", intent.`package`)
    }

    @Test
    fun bothWhatsAppAppsAreTried() {
        assertEquals(listOf("com.whatsapp", "com.whatsapp.w4b"), WhatsAppShare.PACKAGES)
    }

    @Test
    fun openText_startsTheChat_withTheMessageOnlyTypedIn() {
        // Opening a chat never sends: the intent is a plain view of the wa.me link, and the person presses send.
        val shadow = org.robolectric.Shadows.shadowOf(context as android.app.Application)
        WhatsAppShare.openText(context, "919830012345", "Hello", "Send reminder")
        val started = shadow.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://wa.me/919830012345?text=Hello", started.data.toString())
    }
}
