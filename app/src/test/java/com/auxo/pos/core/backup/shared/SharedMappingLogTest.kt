// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.AuditLogEntity
import com.auxo.pos.data.local.entity.DayClosingEntity
import com.auxo.pos.data.local.entity.StockMovementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedMappingLogTest {
    @Test
    fun day_closing_uses_spec_names_and_paise_text() {
        val row = SharedMapping.dayClosing(
            DayClosingEntity(id = 2, businessDate = "2026-11-02", bills = 4, returns = 1, netSalesMinor = 150000,
                cashReceivedMinor = 100000, cashRefundedMinor = 0, cashFromAccountsMinor = 0, upiNetMinor = 50000,
                cardNetMinor = 0, onAccountNetMinor = 0, countedCashMinor = 100000, note = null, closedAt = 6000, closedBy = "Owner"),
        )
        assertEquals("2026-11-02", row["businessDate"])
        assertEquals("150000", row["netSalesPaise"])
        assertEquals("Owner", row["closedBy"])
    }

    @Test
    fun audit_entry_keeps_actor_as_history_text() {
        val row = SharedMapping.auditLog(
            AuditLogEntity(id = 3, createdAt = 10, actorId = 1, actorName = "Owner", actorRole = "SUPER_ADMIN",
                action = "SALE_RECORDED", entityType = "invoice", entityId = 4, summary = "Sold 2 items"),
        )
        assertEquals("1", row["actorId"])
        assertEquals("4", row["entityId"])
    }

    @Test
    fun stock_movement_links_batch_and_invoice_by_shared_id() {
        val row = SharedMapping.stockMovement(
            StockMovementEntity(id = 5, variantId = 11, quantityChange = -2, reason = "SOLD", note = null, createdAt = 500,
                invoiceId = 4, batchId = 21, receiptId = null),
        )
        assertEquals(SharedIds.derive("batch", "21"), row["batchId"])
        assertEquals(SharedIds.derive("invoice", "4"), row["invoiceId"])
        assertEquals(-2, row["quantityChange"])
        assertNull(row["sourceInvoiceId"])
    }
}
