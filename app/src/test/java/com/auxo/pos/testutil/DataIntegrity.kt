// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.testutil

import com.auxo.pos.data.local.AuxoDatabase
import org.junit.Assert.assertEquals

/**
 * Reconciles a real Room database, the way a shop owner's books would be checked.
 * Each rule returns the rows that break it, so an empty list means the books agree.
 */
object DataIntegrity {

    private fun rows(db: AuxoDatabase, sql: String): List<String> {
        val result = mutableListOf<String>()
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            while (cursor.moveToNext()) result += (0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "null" }
        }
        return result
    }

    /** Every variant's stock equals the sum of its stock movements. */
    fun stockVsMovements(db: AuxoDatabase) = rows(
        db,
        "SELECT v.id, v.stock_quantity, COALESCE((SELECT SUM(m.quantity_change) FROM stock_movements m WHERE m.variant_id = v.id), 0) AS moved " +
            "FROM product_variants v WHERE v.stock_quantity != moved",
    )

    /** Items kept in batches: the batches add up to the variant's stock. */
    fun stockVsBatches(db: AuxoDatabase) = rows(
        db,
        "SELECT v.id, v.stock_quantity, (SELECT SUM(b.quantity) FROM stock_batches b WHERE b.variant_id = v.id) AS batched " +
            "FROM product_variants v WHERE EXISTS (SELECT 1 FROM stock_batches b WHERE b.variant_id = v.id) AND v.stock_quantity != batched",
    )

    /** No variant and no batch is ever below zero. */
    fun negativeStock(db: AuxoDatabase) = rows(
        db,
        "SELECT 'variant', id, stock_quantity FROM product_variants WHERE stock_quantity < 0 " +
            "UNION ALL SELECT 'batch', id, quantity FROM stock_batches WHERE quantity < 0",
    )

    /** Every invoice and credit note is paid, or refunded, exactly. */
    fun paymentsVsTotals(db: AuxoDatabase) = rows(
        db,
        "SELECT i.number, i.grand_total_minor, COALESCE((SELECT SUM(p.amount_minor) FROM payments p WHERE p.invoice_id = i.id), 0) AS paid " +
            "FROM invoices i WHERE i.grand_total_minor != paid",
    )

    /** Every invoice's totals are its lines plus round-off. */
    fun invoiceTotalsVsLines(db: AuxoDatabase) = rows(
        db,
        "SELECT i.number, i.grand_total_minor, (SELECT SUM(l.total_minor) FROM invoice_lines l WHERE l.invoice_id = i.id) + i.round_off_minor AS from_lines " +
            "FROM invoices i WHERE i.grand_total_minor != from_lines",
    )

    /** Units marked returned on a sale line equal the units on the credit notes for that line. */
    fun returnedUnitsVsCreditNotes(db: AuxoDatabase) = rows(
        db,
        "SELECT l.id, l.returned_quantity, COALESCE((SELECT SUM(c.quantity) FROM invoice_lines c WHERE c.original_line_id = l.id), 0) AS credited " +
            "FROM invoice_lines l JOIN invoices i ON i.id = l.invoice_id WHERE i.type = 'SALE' AND l.returned_quantity != credited",
    )

    /** No sale line has had more units returned than were sold. */
    fun overReturned(db: AuxoDatabase) = rows(
        db,
        "SELECT id, quantity, returned_quantity FROM invoice_lines WHERE returned_quantity > quantity OR returned_quantity < 0",
    )

    /** Credit notes together never refund more than their sale. */
    fun refundsVsSales(db: AuxoDatabase) = rows(
        db,
        "SELECT s.number, s.grand_total_minor, (SELECT SUM(c.grand_total_minor) FROM invoices c WHERE c.original_invoice_id = s.id) AS refunded " +
            "FROM invoices s WHERE s.type = 'SALE' AND refunded > s.grand_total_minor",
    )

    /**
     * Customer accounts: what was put on account by sales, less what was taken off by returns,
     * equals the sale and return entries in the ledger.
     */
    fun accountSalesVsLedger(db: AuxoDatabase) = rows(
        db,
        "SELECT 'sales', (SELECT COALESCE(SUM(p.amount_minor), 0) FROM payments p JOIN invoices i ON i.id = p.invoice_id WHERE p.method = 'CREDIT' AND i.type = 'SALE') - " +
            "(SELECT COALESCE(SUM(p.amount_minor), 0) FROM payments p JOIN invoices i ON i.id = p.invoice_id WHERE p.method = 'CREDIT' AND i.type = 'RETURN') AS from_bills, " +
            "(SELECT COALESCE(SUM(amount_minor), 0) FROM customer_ledger WHERE type IN ('SALE', 'RETURN')) AS from_ledger " +
            "WHERE from_bills != from_ledger",
    )

    /** Invoice numbers are never reused. */
    fun duplicateNumbers(db: AuxoDatabase) = rows(db, "SELECT number, COUNT(*) FROM invoices GROUP BY number HAVING COUNT(*) > 1")

    /** Rows pointing at an invoice, product or customer that is not there. */
    fun orphans(db: AuxoDatabase) = rows(db, "PRAGMA foreign_key_check")

    /** Fails with the broken rows of every rule that does not hold. */
    fun assertConsistent(db: AuxoDatabase) {
        val broken = linkedMapOf(
            "stock vs movements" to stockVsMovements(db),
            "stock vs batches" to stockVsBatches(db),
            "negative stock" to negativeStock(db),
            "payments vs totals" to paymentsVsTotals(db),
            "invoice totals vs lines" to invoiceTotalsVsLines(db),
            "returned units vs credit notes" to returnedUnitsVsCreditNotes(db),
            "over-returned lines" to overReturned(db),
            "refunds vs sales" to refundsVsSales(db),
            "account sales vs ledger" to accountSalesVsLedger(db),
            "duplicate numbers" to duplicateNumbers(db),
            "orphans" to orphans(db),
        ).filterValues { it.isNotEmpty() }
        assertEquals("Books do not reconcile: $broken", emptyMap<String, List<String>>(), broken)
    }

    fun count(db: AuxoDatabase, table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
}
