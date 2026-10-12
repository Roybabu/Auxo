package com.invision.pos.core.receipt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.invision.pos.core.label.LabelRenderer
import com.invision.pos.core.label.LabelSheetLayout
import com.invision.pos.core.shop.LogoSizing
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.ReceiptOptions
import com.invision.pos.domain.model.ReceiptPaper
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max

/** One page of a rendered receipt, in PDF points (1/72 inch). */
class ReceiptPage(val widthPt: Float, val heightPt: Float, val draw: (Canvas) -> Unit)

/**
 * Turns an invoice into drawable pages for the chosen paper. The same pages are
 * used for the on-screen preview, the PDF file and printing.
 */
object ReceiptRenderer {

    /** [logo] is the shop's logo; when it is null no room is left for one. */
    fun pages(invoice: Invoice, options: ReceiptOptions, zone: ZoneId, logo: Bitmap? = null,
        labels: ReceiptLabels = ReceiptLabels(language = options.language),
        fonts: ReceiptFonts = ReceiptFonts.system,
    ): List<ReceiptPage> =
        if (options.paper.isThermal) {
            listOf(thermalPage(invoice, options, zone, logo, labels, fonts))
        } else {
            SheetRenderer(SheetInvoiceLayout.build(invoice, options, options.paper, zone, labels), options.paper, logo, fonts).pages()
        }

    /** Where a logo goes on a thermal roll: centred above the shop name, at most this big. */
    internal fun thermalLogoSize(logo: Bitmap, printableWidth: Float, lineHeight: Float): Pair<Float, Float> =
        LogoSizing.contain(logo.width, logo.height, printableWidth * 0.6f, lineHeight * 5f)

    // Thermal rolls

    private fun thermalPage(invoice: Invoice, options: ReceiptOptions, zone: ZoneId, logo: Bitmap?, labels: ReceiptLabels, fonts: ReceiptFonts): ReceiptPage {
        val spec = options.paper.spec
        val receipt = ThermalReceiptBuilder.build(invoice, options, spec.charsPerLine, zone, labels)
        val widthPt = LabelSheetLayout.mmToPoints(spec.paperWidthMm)
        val margin = LabelSheetLayout.mmToPoints(spec.marginMm)
        val printable = widthPt - margin * 2

        val paint = monoPaint()
        // Size the font so one line of the grid fills the printable width.
        paint.textSize = 10f
        val charWidthAt10 = paint.measureText("M")
        val fontSize = 10f * (printable / spec.charsPerLine) / charWidthAt10
        val lineHeight = fontSize * 1.3f
        val barcodeHeight = fontSize * 4f
        val regularStyle = ScriptPaint(fonts, bold = false, mono = true, size = fontSize)
        val boldStyle = ScriptPaint(fonts, bold = true, mono = true, size = fontSize)
        val logoSize = logo?.let { thermalLogoSize(it, printable, lineHeight) }
        val logoBlock = logoSize?.let { it.second + lineHeight / 2 } ?: 0f

        val contentHeight = logoBlock + receipt.elements.sumOf { element ->
            when (element) {
                is ThermalElement.Line -> (if (element.large) lineHeight * 2 else lineHeight).toDouble()
                is ThermalElement.Divider -> lineHeight.toDouble()
                is ThermalElement.Barcode -> (barcodeHeight + lineHeight).toDouble()
                ThermalElement.Gap -> (lineHeight / 2).toDouble()
            }
        }.toFloat()
        val heightPt = ceil(contentHeight + margin * 2 + lineHeight * 2)

        return ReceiptPage(widthPt, heightPt) { canvas ->
            canvas.drawColor(Color.WHITE)
            var y = margin
            if (logo != null && logoSize != null) {
                val target = RectF(margin + (printable - logoSize.first) / 2, y, margin + (printable + logoSize.first) / 2, y + logoSize.second)
                canvas.drawBitmap(logo, null, target, logoPaint)
                y += logoBlock
            }
            for (element in receipt.elements) {
                when (element) {
                    is ThermalElement.Line -> {
                        val size = if (element.large) fontSize * 2 else fontSize
                        val height = if (element.large) lineHeight * 2 else lineHeight
                        val style = if (element.bold) boldStyle else regularStyle
                        style.textSize = size
                        val baseline = y + height * 0.78f
                        if (element.label != null && element.value != null) {
                            // A label and its amount: the amount stays at the right edge whatever the font, and
                            // the label is cut short if there is not room for both.
                            val charWidth = charWidthAt10 / 10f * size
                            val indent = element.label.takeWhile { it == ' ' }.length * charWidth
                            val label = element.label.trimStart()
                            val valueWidth = style.measureText(element.value)
                            style.draw(canvas, element.value, margin + printable - valueWidth, baseline)
                            style.draw(canvas, style.fit(label, printable - valueWidth - indent - charWidth), margin + indent, baseline)
                        } else {
                            // A line in a wider script can be wider than the roll: shrink it a little rather than cut it.
                            var textWidth = style.measureText(element.text)
                            if (textWidth > printable) {
                                style.textSize = size * max(printable / textWidth, 0.7f)
                                textWidth = style.measureText(element.text)
                            }
                            val shown = if (textWidth > printable) style.fit(element.text, printable) else element.text
                            val shownWidth = style.measureText(shown)
                            val x = when (element.align) {
                                Align.LEFT -> margin
                                Align.CENTER -> margin + (printable - shownWidth) / 2
                                Align.RIGHT -> margin + printable - shownWidth
                            }
                            style.draw(canvas, shown, x, baseline)
                        }
                        y += height
                    }
                    is ThermalElement.Divider -> {
                        regularStyle.textSize = fontSize
                        regularStyle.draw(canvas, element.char.toString().repeat(receipt.width), margin, y + lineHeight * 0.78f)
                        y += lineHeight
                    }
                    is ThermalElement.Barcode -> {
                        LabelRenderer.drawBarcode(canvas, element.value, margin, y, printable, barcodeHeight)
                        y += barcodeHeight
                        regularStyle.textSize = fontSize
                        val w = regularStyle.measureText(element.value)
                        regularStyle.draw(canvas, element.value, margin + (printable - w) / 2, y + lineHeight * 0.78f)
                        y += lineHeight
                    }
                    ThermalElement.Gap -> y += lineHeight / 2
                }
            }
        }
    }

    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun monoPaint() = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.MONOSPACE
    }
}

/** Draws an A5 or A4 tax invoice, adding pages when the item table is long. */
private class SheetRenderer(private val doc: SheetInvoice, paper: ReceiptPaper, private val logo: Bitmap? = null, fonts: ReceiptFonts = ReceiptFonts.system) {
    private val spec = paper.spec
    private val pageWidth = LabelSheetLayout.mmToPoints(spec.paperWidthMm)
    private val pageHeight = LabelSheetLayout.mmToPoints(spec.paperHeightMm ?: 297f)
    private val margin = LabelSheetLayout.mmToPoints(spec.marginMm)
    private val contentWidth = pageWidth - margin * 2
    private val base = if (paper == ReceiptPaper.A4) 9f else 7.5f
    private val line = base * 1.45f
    private val cellPadding = base * 0.4f

    private val text = ScriptPaint(fonts, bold = false, size = base)
    private val bold = ScriptPaint(fonts, bold = true, size = base)
    private val rule = Paint().apply { color = Color.BLACK; strokeWidth = 0.6f }
    private val fill = Paint().apply { color = Color.rgb(235, 235, 235); style = Paint.Style.FILL }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** The logo's size on the page: top left, above the seller, in proportion to the text. */
    private val logoSize: Pair<Float, Float>? = logo?.let { LogoSizing.contain(it.width, it.height, contentWidth * 0.3f, line * 4f) }
    private val logoBlock: Float = logoSize?.let { it.second + line * 0.4f } ?: 0f

    fun pages(): List<ReceiptPage> {
        val rowHeights = doc.rows.indices.map { rowHeight(it) }
        val headerHeight = headerHeight()
        val tableHeaderHeight = line + cellPadding * 2
        val endHeight = endBlockHeight()

        // Split rows across pages: the first page carries the header, the last the totals.
        val pageRows = mutableListOf<IntRange>()
        var start = 0
        var available = pageHeight - margin * 2 - headerHeight - tableHeaderHeight - line
        while (start < doc.rows.size || pageRows.isEmpty()) {
            var end = start
            var used = 0f
            while (end < doc.rows.size && used + rowHeights[end] <= available) {
                used += rowHeights[end]
                end++
            }
            if (end == start && start < doc.rows.size) end = start + 1 // a single very tall row
            pageRows += start until end
            start = end
            available = pageHeight - margin * 2 - tableHeaderHeight - line * 2
            if (start >= doc.rows.size) break
        }
        val lastRowsHeight = pageRows.last().sumOf { rowHeights[it].toDouble() }.toFloat()
        val lastPageStart = if (pageRows.size == 1) headerHeight else line
        val needsExtraPage = lastPageStart + tableHeaderHeight + lastRowsHeight + endHeight > pageHeight - margin * 2
        val pageCount = pageRows.size + if (needsExtraPage) 1 else 0

        return (0 until pageCount).map { index ->
            ReceiptPage(pageWidth, pageHeight) { canvas ->
                canvas.drawColor(Color.WHITE)
                var y = margin
                if (index == 0) {
                    y = drawHeader(canvas, y)
                } else {
                    text.textSize = base * 0.9f
                    text.draw(canvas, "${doc.title} ${doc.detailLines.first().second} ${doc.continuedLabel}", margin, y + line * 0.8f)
                    text.textSize = base
                    y += line
                }
                if (index < pageRows.size) {
                    y = drawTable(canvas, y, pageRows[index], rowHeights)
                }
                if (index == pageCount - 1) drawEndBlock(canvas, y + line * 0.5f)
                drawPageNumber(canvas, index + 1, pageCount)
            }
        }
    }

    // Header: seller on the left, title and invoice details on the right, then the buyer.

    private fun headerHeight(): Float {
        val left = 1.6f * line + doc.sellerLines.size * line
        val right = 1.6f * line + doc.detailLines.size * line
        val buyer = if (doc.buyerLines.isEmpty()) 0f else line * (doc.buyerLines.size + 1) + line
        return logoBlock + max(left, right) + line + buyer
    }

    private fun drawHeader(canvas: Canvas, top: Float): Float {
        var y = top
        if (logo != null && logoSize != null) {
            canvas.drawBitmap(logo, null, RectF(margin, y, margin + logoSize.first, y + logoSize.second), logoPaint)
            y += logoBlock
        }
        bold.textSize = base * 1.6f
        bold.draw(canvas, fit(doc.sellerName, contentWidth * 0.55f, bold), margin, y + line * 1.2f)
        bold.textSize = base
        var leftY = y + line * 1.6f
        for (sellerLine in doc.sellerLines) {
            text.draw(canvas, fit(sellerLine, contentWidth * 0.55f, text), margin, leftY + line * 0.8f)
            leftY += line
        }

        bold.textSize = base * 1.3f
        val titleWidth = bold.measureText(doc.title)
        bold.draw(canvas, doc.title, margin + contentWidth - titleWidth, y + line * 1.2f)
        bold.textSize = base
        var rightY = y + line * 1.6f
        for ((label, value) in doc.detailLines) {
            val valueWidth = bold.measureText(value)
            bold.draw(canvas, value, margin + contentWidth - valueWidth, rightY + line * 0.8f)
            val labelText = "$label: "
            text.draw(canvas, labelText, margin + contentWidth - valueWidth - text.measureText(labelText), rightY + line * 0.8f)
            rightY += line
        }

        y = max(leftY, rightY) + line * 0.5f
        canvas.drawLine(margin, y, margin + contentWidth, y, rule)
        y += line * 0.5f

        if (doc.buyerLines.isNotEmpty()) {
            bold.draw(canvas, doc.buyerHeading, margin, y + line * 0.8f)
            y += line
            doc.buyerLines.forEachIndexed { i, buyerLine ->
                (if (i == 0) bold else text).draw(canvas, fit(buyerLine, contentWidth, if (i == 0) bold else text), margin, y + line * 0.8f)
                y += line
            }
            y += line * 0.5f
        }
        return y
    }

    // Item table

    private fun columnWidths(): List<Float> {
        val totalWeight = doc.columns.sumOf { it.weight.toDouble() }.toFloat()
        return doc.columns.map { contentWidth * it.weight / totalWeight }
    }

    private val itemColumn: Int get() = doc.columns.indexOfFirst { it.isItem }

    private fun itemLines(row: Int): List<String> {
        val width = columnWidths()[itemColumn] - cellPadding * 2
        val wrapped = wrapToWidth(doc.rows[row][itemColumn], width, text).take(2)
        return wrapped + listOfNotNull(doc.rowNotes[row])
    }

    private fun rowHeight(row: Int): Float = itemLines(row).size * line + cellPadding * 2

    private fun drawTable(canvas: Canvas, top: Float, rows: IntRange, heights: List<Float>): Float {
        val widths = columnWidths()
        var y = top
        val headerHeight = line + cellPadding * 2
        canvas.drawRect(margin, y, margin + contentWidth, y + headerHeight, fill)
        var x = margin
        doc.columns.forEachIndexed { i, column ->
            drawCell(canvas, column.title, x, y + cellPadding, widths[i], column.alignRight, bold)
            x += widths[i]
        }
        y += headerHeight
        canvas.drawLine(margin, y, margin + contentWidth, y, rule)

        for (row in rows) {
            x = margin
            doc.columns.forEachIndexed { i, column ->
                if (i == itemColumn) {
                    itemLines(row).forEachIndexed { lineIndex, itemLine ->
                        val paint = if (lineIndex >= 2 || itemLine == doc.rowNotes[row]) text.also { it.textSize = base * 0.85f } else text
                        drawCell(canvas, itemLine, x, y + cellPadding + lineIndex * line, widths[i], false, paint)
                        text.textSize = base
                    }
                } else {
                    drawCell(canvas, doc.rows[row][i], x, y + cellPadding, widths[i], column.alignRight, text)
                }
                x += widths[i]
            }
            y += heights[row]
            rule.strokeWidth = 0.3f
            canvas.drawLine(margin, y, margin + contentWidth, y, rule)
            rule.strokeWidth = 0.6f
        }
        return y
    }

    private fun drawCell(canvas: Canvas, value: String, left: Float, top: Float, width: Float, alignRight: Boolean, paint: ScriptPaint) {
        val shown = fit(value, width - cellPadding * 2, paint)
        val x = if (alignRight) left + width - cellPadding - paint.measureText(shown) else left + cellPadding
        paint.draw(canvas, shown, x, top + line * 0.75f)
    }

    // Totals, tax summary, payments, notes and signature

    private fun endBlockHeight(): Float {
        val totals = (doc.totals.size + 1) * line + line
        val words = wrapToWidth(doc.amountInWords, contentWidth, text).size * line + line * 0.5f
        val summary = if (doc.taxSummaryHeader.isEmpty()) 0f else (doc.taxSummaryRows.size + 2) * (line + cellPadding) + line
        val payments = doc.paymentLines.size * line + line * 0.5f
        val notes = doc.notes.size * line
        val footer = (if (doc.footer.isBlank()) 0f else line * 1.5f) + (if (doc.feedbackNote.isBlank()) 0f else line * 1.5f)
        val signature = line * 4f
        val barcode = if (doc.barcode != null) line * 3f else 0f
        return totals + words + summary + payments + notes + footer + max(signature, barcode)
    }

    private fun drawEndBlock(canvas: Canvas, top: Float) {
        var y = top
        val labelX = margin + contentWidth * 0.55f
        for ((label, value) in doc.totals) {
            text.draw(canvas, label, labelX, y + line * 0.8f)
            text.draw(canvas, value, margin + contentWidth - text.measureText(value), y + line * 0.8f)
            y += line
        }
        canvas.drawLine(labelX, y + 1f, margin + contentWidth, y + 1f, rule)
        bold.textSize = base * 1.2f
        val (grandLabel, grandValue) = doc.grandTotal
        bold.draw(canvas, grandLabel, labelX, y + line * 0.95f)
        bold.draw(canvas, grandValue, margin + contentWidth - bold.measureText(grandValue), y + line * 0.95f)
        bold.textSize = base
        y += line * 1.6f

        for (wordsLine in wrapToWidth(doc.amountInWords, contentWidth, text)) {
            text.draw(canvas, wordsLine, margin, y + line * 0.8f)
            y += line
        }
        y += line * 0.5f

        if (doc.taxSummaryHeader.isNotEmpty()) {
            val widths = doc.taxSummaryHeader.map { contentWidth / doc.taxSummaryHeader.size }
            val rowH = line + cellPadding
            canvas.drawRect(margin, y, margin + contentWidth, y + rowH, fill)
            var x = margin
            doc.taxSummaryHeader.forEachIndexed { i, title ->
                drawCell(canvas, title, x, y + cellPadding / 2, widths[i], i > 0, bold)
                x += widths[i]
            }
            y += rowH
            for (row in doc.taxSummaryRows) {
                x = margin
                row.forEachIndexed { i, value ->
                    drawCell(canvas, value, x, y + cellPadding / 2, widths[i], i > 0, text)
                    x += widths[i]
                }
                y += rowH
            }
            canvas.drawLine(margin, y, margin + contentWidth, y, rule)
            y += line
        }

        for (payment in doc.paymentLines) {
            text.draw(canvas, fit(payment, contentWidth, text), margin, y + line * 0.8f)
            y += line
        }
        y += line * 0.5f
        text.textSize = base * 0.9f
        for (note in doc.notes) {
            text.draw(canvas, fit(note, contentWidth, text), margin, y + line * 0.8f, text)
            y += line
        }
        text.textSize = base

        if (doc.footer.isNotBlank()) {
            val shown = fit(doc.footer, contentWidth)
            text.draw(canvas, shown, margin + (contentWidth - text.measureText(shown)) / 2, y + line, text)
            y += line * 1.5f
        }
        if (doc.feedbackNote.isNotBlank()) {
            val shown = fit(doc.feedbackNote, contentWidth)
            text.draw(canvas, shown, margin + (contentWidth - text.measureText(shown)) / 2, y + line)
            y += line * 1.5f
        }

        // Barcode bottom left, signature bottom right.
        doc.barcode?.let { value ->
            LabelRenderer.drawBarcode(canvas, value, margin, y + line * 0.3f, contentWidth * 0.35f, line * 2f)
        }
        val signTop = y + line * 0.5f
        val sign = fit(doc.signatureLabel, contentWidth * 0.4f, bold)
        bold.draw(canvas, sign, margin + contentWidth - bold.measureText(sign), signTop + line * 0.8f)
        val authorised = doc.signatoryCaption
        text.draw(canvas, authorised, margin + contentWidth - text.measureText(authorised), signTop + line * 3.3f)
    }

    private fun drawPageNumber(canvas: Canvas, page: Int, count: Int) {
        if (count <= 1) return
        text.textSize = base * 0.8f
        val label = fillIn(doc.pageNumberTemplate, page, count)
        text.draw(canvas, label, margin + contentWidth - text.measureText(label), pageHeight - margin / 2)
        text.textSize = base
    }

    private fun fit(value: String, width: Float, paint: ScriptPaint): String = paint.fit(value, width)

    private fun wrapToWidth(value: String, width: Float, paint: ScriptPaint): List<String> {
        val words = value.split(' ').filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= width || current.isEmpty()) {
                current = candidate
            } else {
                result += current
                current = word
            }
        }
        if (current.isNotEmpty()) result += current
        return result.ifEmpty { listOf("") }
    }
}
