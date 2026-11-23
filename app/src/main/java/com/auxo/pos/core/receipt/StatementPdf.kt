// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.receipt

import android.content.Context
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

/** A line of a statement: the date, what happened, the amount (with + or −) and the balance after it. */
data class StatementRow(val date: String, val entry: String, val amount: String, val balance: String)

/** Everything a customer statement shows, already written out as text in the chosen language. */
data class StatementContent(
    val shopName: String,
    val title: String,
    val customer: String,
    val details: String,
    val summary: List<Pair<String, String>>,
    val headers: List<String>,
    val rows: List<StatementRow>,
    val footer: String?,
)

/** A customer's statement as A4 pages, drawn with the same fonts as receipts so Hindi and Bengali names print properly. */
object StatementPdf {
    private const val PAGE_WIDTH = 595f
    private const val PAGE_HEIGHT = 842f
    private const val MARGIN = 36f
    private const val ROW_HEIGHT = 20f
    private const val FIRST_PAGE_TOP = 190f
    private const val NEXT_PAGE_TOP = 70f
    private const val BOTTOM = 60f

    fun pages(content: StatementContent, fonts: ReceiptFonts): List<ReceiptPage> {
        val perFirst = ((PAGE_HEIGHT - BOTTOM - FIRST_PAGE_TOP) / ROW_HEIGHT).toInt().coerceAtLeast(1)
        val perNext = ((PAGE_HEIGHT - BOTTOM - NEXT_PAGE_TOP) / ROW_HEIGHT).toInt().coerceAtLeast(1)
        val chunks = mutableListOf<List<StatementRow>>()
        var rest = content.rows
        chunks += rest.take(perFirst)
        rest = rest.drop(perFirst)
        while (rest.isNotEmpty()) {
            chunks += rest.take(perNext)
            rest = rest.drop(perNext)
        }
        return chunks.mapIndexed { index, rows ->
            ReceiptPage(PAGE_WIDTH, PAGE_HEIGHT) { canvas ->
                val title = ScriptPaint(fonts, bold = true, size = 18f)
                val heading = ScriptPaint(fonts, bold = true, size = 11f)
                val body = ScriptPaint(fonts, size = 10f)
                val small = ScriptPaint(fonts, size = 9f)
                val line = Paint().apply { strokeWidth = 0.6f; color = 0xFF999999.toInt() }
                val right = PAGE_WIDTH - MARGIN
                // Columns: date, entry, amount (right aligned), balance (right aligned).
                val dateX = MARGIN
                val entryX = MARGIN + 105f
                val amountRight = right - 110f
                val balanceRight = right
                var top = NEXT_PAGE_TOP
                if (index == 0) {
                    title.draw(canvas, content.shopName, MARGIN, 52f)
                    heading.draw(canvas, content.title, MARGIN, 72f)
                    body.draw(canvas, content.customer, MARGIN, 96f)
                    if (content.details.isNotEmpty()) small.draw(canvas, content.details, MARGIN, 111f)
                    content.summary.forEachIndexed { i, (label, value) ->
                        val y = 134f + i * 14f
                        body.draw(canvas, label, MARGIN, y)
                        heading.draw(canvas, value, right - heading.measureText(value), y)
                    }
                    top = FIRST_PAGE_TOP
                }
                // Header row.
                val headerY = top - 8f
                content.headers.getOrNull(0)?.let { heading.draw(canvas, it, dateX, headerY) }
                content.headers.getOrNull(1)?.let { heading.draw(canvas, it, entryX, headerY) }
                content.headers.getOrNull(2)?.let { heading.draw(canvas, it, amountRight - heading.measureText(it), headerY) }
                content.headers.getOrNull(3)?.let { heading.draw(canvas, it, balanceRight - heading.measureText(it), headerY) }
                canvas.drawLine(MARGIN, top - 3f, right, top - 3f, line)
                rows.forEachIndexed { i, row ->
                    val y = top + 14f + i * ROW_HEIGHT
                    body.draw(canvas, body.fit(row.date, 98f), dateX, y)
                    body.draw(canvas, body.fit(row.entry, amountRight - entryX - 100f), entryX, y)
                    body.draw(canvas, row.amount, amountRight - body.measureText(row.amount), y)
                    body.draw(canvas, row.balance, balanceRight - body.measureText(row.balance), y)
                }
                if (index == chunks.lastIndex && content.footer != null) {
                    small.draw(canvas, content.footer, MARGIN, PAGE_HEIGHT - 36f)
                }
            }
        }
    }

    /** Writes the statement to the cache folder that files are shared from and returns the file. */
    fun writeFile(context: Context, content: StatementContent, fileName: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { ReceiptOutput.writePdf(pages(content, ReceiptFonts.from(context)), it) }
        return file
    }
}
