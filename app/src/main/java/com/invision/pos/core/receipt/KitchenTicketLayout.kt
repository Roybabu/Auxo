package com.invision.pos.core.receipt

import android.content.Context
import android.content.res.Resources
import com.invision.pos.R
import com.invision.pos.core.locale.LocaleContext
import com.invision.pos.domain.billing.KitchenTicket
import com.invision.pos.domain.billing.KotLine
import com.invision.pos.domain.billing.ServiceType
import com.invision.pos.domain.model.Language
import java.time.ZoneId

/** Fixed wording on a kitchen order ticket. Dish names are printed exactly as entered. */
data class KotLabels(
    val title: String = "KITCHEN ORDER",
    val addOn: String = "ADD-ON ITEMS",
    val number: String = "KOT: %1\$s",
    val table: String = "TABLE %1\$s",
    val takeaway: String = "TAKEAWAY",
    val date: String = "Date: %1\$s",
    val orderFor: String = "For: %1\$s",
    val newItems: String = "NEW ITEMS",
    val cancelled: String = "CANCELLED - DO NOT MAKE",
    val line: String = "%1\$d x %2\$s",
    val totalNew: String = "Total new items: %1\$d",
) {
    companion object {
        fun from(context: Context, language: Language): KotLabels =
            from(LocaleContext.wrap(context, language).resources)

        fun from(resources: Resources): KotLabels = KotLabels(
            title = resources.getString(R.string.kot_title),
            addOn = resources.getString(R.string.kot_add_on),
            number = resources.getString(R.string.kot_number),
            table = resources.getString(R.string.kot_table),
            takeaway = resources.getString(R.string.kot_takeaway),
            date = resources.getString(R.string.kot_date),
            orderFor = resources.getString(R.string.kot_order_for),
            newItems = resources.getString(R.string.kot_new_items),
            cancelled = resources.getString(R.string.kot_cancelled),
            line = resources.getString(R.string.kot_line),
            totalNew = resources.getString(R.string.kot_total_new),
        )

        /** Every resource the wording reads, in field order. */
        val resourceIds: List<Int> = listOf(
            R.string.kot_title, R.string.kot_add_on, R.string.kot_number, R.string.kot_table,
            R.string.kot_takeaway, R.string.kot_date, R.string.kot_order_for, R.string.kot_new_items,
            R.string.kot_cancelled, R.string.kot_line, R.string.kot_total_new,
        )
    }
}

/** Lays a kitchen ticket out for a thermal roll: big table and KOT number, then the items the kitchen must make. */
object KitchenTicketLayout {

    fun build(ticket: KitchenTicket, width: Int, zone: ZoneId, labels: KotLabels = KotLabels()): ThermalReceipt {
        val out = mutableListOf<ThermalElement>()
        fun center(text: String, bold: Boolean = false, large: Boolean = false) {
            val lineWidth = if (large) width / 2 else width
            ReceiptText.wrap(text, lineWidth).forEach { out += ThermalElement.Line(it, Align.CENTER, bold, large) }
        }
        fun left(text: String, bold: Boolean = false) {
            ReceiptText.wrap(text, width).forEach { out += ThermalElement.Line(it, Align.LEFT, bold) }
        }
        fun items(lines: List<KotLine>) {
            for (item in lines) {
                val name = listOf(item.name, item.variant).filter { it.isNotBlank() }.joinToString(" ")
                val text = fillIn(labels.line, item.quantity, name)
                val at = text.indexOf(name)
                val indent = if (at in 1..(width / 3) && text.endsWith(name)) at else 0
                // Wrapped lines hang under the name, not under the quantity, and never run past the roll.
                val rows = if (indent > 0) ReceiptText.wrap(name, width - indent) else ReceiptText.wrap(text, width)
                rows.forEachIndexed { i, row ->
                    val shown = if (indent > 0) (if (i == 0) text.take(indent) else " ".repeat(indent)) + row else row
                    out += ThermalElement.Line(shown, Align.LEFT, bold = true)
                }
            }
        }

        center(ticket.shopName, bold = true)
        center(labels.title, bold = true)
        if (ticket.isAddOn) center(labels.addOn, bold = true)
        out += ThermalElement.Divider()
        center(fillIn(labels.number, ticket.kotNumber), bold = true, large = true)
        when (ticket.serviceType) {
            ServiceType.DINE_IN -> ticket.table?.takeIf { it.isNotBlank() }?.let { center(fillIn(labels.table, it), bold = true, large = true) }
            ServiceType.TAKEAWAY -> center(labels.takeaway, bold = true, large = true)
        }
        left(fillIn(labels.date, ReceiptText.dateTime(ticket.createdAtMillis, zone)))
        if (ticket.label.isNotBlank()) left(fillIn(labels.orderFor, ticket.label))
        out += ThermalElement.Divider()

        if (ticket.delta.added.isNotEmpty()) {
            left(labels.newItems, bold = true)
            items(ticket.delta.added)
        }
        if (ticket.delta.cancelled.isNotEmpty()) {
            if (ticket.delta.added.isNotEmpty()) out += ThermalElement.Gap
            left(labels.cancelled, bold = true)
            items(ticket.delta.cancelled)
        }
        out += ThermalElement.Divider()
        left(fillIn(labels.totalNew, ticket.delta.added.sumOf { it.quantity }))
        return ThermalReceipt(width, out)
    }
}
