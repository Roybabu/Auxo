// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auxo.pos.data.local.AuxoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A rich file made by the web app (26 tables filled) is restored into a real database and exported again with the
 * real queries. Every row must come back with the same id and the same values; each table is its own test so a
 * difference names the table.
 */
@RunWith(AndroidJUnit4::class)
class SharedRichRoundTripDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "shared-rich-round-trip.db"
    private val passphrase = "long shared phrase 91"

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun remove() { context.deleteDatabase(name) }

    private fun original(): SharedBackupDocument {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("shared/web-rich.auxoshared").use { it.readBytes() }
        return SharedBackupFile.open(bytes, passphrase)
    }

    private fun check(table: String) = runBlocking {
        val doc = original()
        val db = AuxoDatabase.builder(context, name).build()
        try {
            SharedRestoreApplier(db, photos = null, workDir = context.cacheDir).apply(SharedRestorePlanner.plan(doc))
            val exported = SharedExport(db.sharedExportDao(), photoFile = { null }).build(1L, "test")
            @Suppress("UNCHECKED_CAST")
            val rows = ((exported["tables"] as Map<String, List<Map<String, Any?>>>)[table]).orEmpty().associateBy { (it["id"] ?: it["name"]).toString() }
            val expected = doc.tables.getValue(table)
            val problems = ArrayList<String>()
            if (expected.size != rows.size) problems += "count ${expected.size} became ${rows.size}"
            for (row in expected) {
                val key = (row["id"] ?: row["name"]).toString()
                val got = rows[key]
                if (got == null) { problems += "row $key missing"; continue }
                for ((field, value) in row) {
                    if (got.containsKey(field) && got[field] != value && !(value is Long && got[field] == value.toInt())) {
                        problems += "$field: $value became ${got[field]}"
                    }
                }
            }
            assertTrue("$table: " + problems.joinToString("; ").take(900), problems.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun table_settings() = check("settings")
    @Test fun table_categories() = check("categories")
    @Test fun table_subcategories() = check("subcategories")
    @Test fun table_products() = check("products")
    @Test fun table_variants() = check("variants")
    @Test fun table_stockMovements() = check("stockMovements")
    @Test fun table_customers() = check("customers")
    @Test fun table_customerLedger() = check("customerLedger")
    @Test fun table_invoices() = check("invoices")
    @Test fun table_invoiceLines() = check("invoiceLines")
    @Test fun table_payments() = check("payments")
    @Test fun table_suppliers() = check("suppliers")
    @Test fun table_purchaseOrders() = check("purchaseOrders")
    @Test fun table_purchaseOrderLines() = check("purchaseOrderLines")
    @Test fun table_receipts() = check("receipts")
    @Test fun table_receiptLines() = check("receiptLines")
    @Test fun table_dayClosings() = check("dayClosings")
    @Test fun table_loyaltySettings() = check("loyaltySettings")
    @Test fun table_loyaltyLedger() = check("loyaltyLedger")
    @Test fun table_heldBills() = check("heldBills")
    @Test fun table_heldBillLines() = check("heldBillLines")
    @Test fun table_quotations() = check("quotations")
    @Test fun table_quotationLines() = check("quotationLines")
    @Test fun table_stockCounts() = check("stockCounts")
    @Test fun table_stockCountLines() = check("stockCountLines")
    @Test fun table_auditLog() = check("auditLog")
}
