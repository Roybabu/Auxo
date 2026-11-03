// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.LedgerEntryEntity
import com.auxo.pos.data.local.entity.LoyaltySettingsEntity
import com.auxo.pos.data.local.entity.NumberSequenceEntity
import com.auxo.pos.data.local.entity.StockCountEntity
import com.auxo.pos.domain.inventory.CountLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFileTest {
    private fun allTables() = SharedFile.TABLES.associateWith { emptyList<Map<String, Any?>>() }

    @Test
    fun assembles_all_29_tables_with_the_format_fields() {
        val file = SharedFile.assemble(allTables(), shopName = "Shop", createdAt = 1, appVersion = "1.0")
        assertEquals(29, SharedFile.TABLES.size)
        assertEquals("auxo-backup", file["format"])
        assertEquals(SharedFile.TABLES, (file["tables"] as Map<*, *>).keys.toList())
        assertTrue(SharedFile.toJson(file).startsWith("""{"format":"auxo-backup","version":1,"sharedModelVersion":1"""))
    }

    @Test
    fun refuses_a_backup_with_a_missing_table() {
        val tables = allTables() - "payments"
        val error = assertThrows(IllegalArgumentException::class.java) {
            SharedFile.assemble(tables, shopName = "Shop", createdAt = 1, appVersion = "1.0")
        }
        assertTrue(error.message!!.contains("payments"))
    }

    @Test
    fun loyalty_points_are_earned_or_redeemed_by_sign_and_zero_is_skipped() {
        val earned = SharedMapping.loyaltyEntry(
            LedgerEntryEntity(id = 1, customerId = 5, type = "SALE", amountMinor = 0, invoiceId = 4, method = null,
                reference = null, note = null, createdAt = 10, points = 20),
        )
        assertEquals("EARNED", earned?.get("type"))
        val redeemed = SharedMapping.loyaltyEntry(
            LedgerEntryEntity(id = 2, customerId = 5, type = "PAYMENT", amountMinor = 0, invoiceId = null, method = null,
                reference = null, note = null, createdAt = 11, points = -5),
        )
        assertEquals("REDEEMED", redeemed?.get("type"))
        assertNull(SharedMapping.loyaltyEntry(
            LedgerEntryEntity(id = 3, customerId = 5, type = "PAYMENT", amountMinor = 100, invoiceId = null, method = "CASH",
                reference = null, note = null, createdAt = 12),
        ))
    }

    @Test
    fun loyalty_settings_and_number_sequences_map_to_spec_names() {
        val rules = SharedMapping.loyaltySettings(LoyaltySettingsEntity(enabled = true, spendPerPointMinor = 500, pointValueMinor = 100))
        assertEquals("rules", rules["id"])
        assertEquals("500", rules["spendPerPointPaise"])
        assertEquals(mapOf("name" to "INV/26-27", "nextValue" to 7L), SharedMapping.numberSequence(NumberSequenceEntity("INV/26-27", 7)))
    }

    @Test
    fun stock_count_counts_matched_lines_and_links_its_lines() {
        val lines = listOf(CountLine(variantId = 11, expected = 12, counted = 12), CountLine(variantId = 12, expected = 5, counted = 4))
        val count = SharedMapping.stockCount(
            StockCountEntity(id = 9, startedAt = 1, status = "POSTED", payload = "", finishedAt = 2, reason = "ROUTINE",
                note = null, adjustedCount = 1),
            lines,
        )
        assertEquals(1, count["matchedCount"])
        assertEquals(1, count["adjustedCount"])
        val row = SharedMapping.stockCountLine(9, 1, lines[1])
        assertEquals(SharedIds.derive("stockCount", "9"), row["stockCountId"])
        assertEquals(4, row["countedQuantity"])
        assertFalse(row.containsKey("note"))
    }
}
