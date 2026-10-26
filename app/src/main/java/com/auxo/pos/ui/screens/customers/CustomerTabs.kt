// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.auxo.pos.R
import com.auxo.pos.domain.billing.BusinessTime
import com.auxo.pos.domain.customers.BillStatus
import com.auxo.pos.domain.customers.Ledger
import com.auxo.pos.domain.customers.LedgerLine
import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoIconStyle
import com.auxo.pos.ui.components.StatusChip
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.screens.billing.labelRes
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.CustomerDetailUiState
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Which bills "All bills" lists. */
enum class BillFilter { ALL, UNPAID, RETURNS }

/** Which entries the "Credit book" lists. */
enum class BookFilter { ALL, PAYMENTS }

private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

private fun monthOf(millis: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(millis).atZone(BusinessTime.ZONE))

/** Green when the customer owes the shop, red when the shop owes the customer, as in the balance at the top of the page. */
@Composable
private fun owedColour(owesShop: Boolean): Color =
    if (owesShop) AuxoThemeExtras.statusColors.success.content else AuxoThemeExtras.statusColors.danger.content

@Composable
private fun TabIntro(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TabEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.md),
    )
}

@Composable
private fun MonthHeader(title: String, subtotal: String, colour: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(subtotal, style = MaterialTheme.typography.labelLarge, color = colour)
    }
}

/** "−₹500.00" for money that takes the balance down and "+₹500.00" for money that adds to it. */
private fun signed(amount: Money, text: (Money) -> String): String =
    if (amount < Money.ZERO) "−" + text(Money(-amount.minorUnits)) else "+" + text(amount)

/** The "Credit book" tab: what was put on credit and what was paid back, newest month first. */
fun LazyListScope.creditBookTab(
    state: CustomerDetailUiState.Ready,
    filter: BookFilter,
    onFilter: (BookFilter) -> Unit,
    onOpenInvoice: (Long) -> Unit,
) {
    item(key = "book_hint") { TabIntro(stringResource(R.string.customer_book_hint)) }
    if (state.statement.isEmpty()) {
        item(key = "no_entries") { TabEmpty(stringResource(R.string.customer_statement_empty)) }
        return
    }
    item(key = "book_filters") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(selected = filter == BookFilter.ALL, onClick = { onFilter(BookFilter.ALL) }, label = { Text(stringResource(R.string.customer_filter_all)) })
            FilterChip(selected = filter == BookFilter.PAYMENTS, onClick = { onFilter(BookFilter.PAYMENTS) }, label = { Text(stringResource(R.string.customer_filter_payments)) })
        }
    }
    val lines = state.statement.filter { filter == BookFilter.ALL || it.entry.type == LedgerEntryType.PAYMENT }
    if (lines.isEmpty()) {
        item(key = "book_none") { TabEmpty(stringResource(R.string.customer_filter_empty)) }
        return
    }
    lines.groupBy { monthOf(it.entry.createdAtMillis) }.forEach { (month, inMonth) ->
        item(key = "book-head-$month") {
            val formatter = LocalMoneyFormatter.current
            val net = inMonth.fold(Money.ZERO) { acc, line -> acc + line.entry.amount }
            MonthHeader(month.format(MONTH_LABEL), signed(net, formatter::format), owedColour(net >= Money.ZERO))
        }
        item(key = "book-$month") {
            AuxoCard(modifier = Modifier.fillMaxWidth().testTag("customer_statement_$month")) {
                Column {
                    inMonth.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CreditBookRow(line, onOpenInvoice)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditBookRow(line: LedgerLine, onOpenInvoice: (Long) -> Unit) {
    val formatter = LocalMoneyFormatter.current
    val entry = line.entry
    val owesMore = entry.amount > Money.ZERO
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(entry.invoiceId?.let { id -> Modifier.clickable { onOpenInvoice(id) } } ?: Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(entry.type.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(
                    DateFormats.dateTime(entry.createdAtMillis),
                    entry.invoiceNumber,
                    entry.method?.let { stringResource(it.labelRes()) },
                    entry.reference,
                    entry.note,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = signed(entry.amount, formatter::format),
                style = MaterialTheme.typography.titleSmall,
                color = owedColour(owesMore),
            )
            Text(
                text = stringResource(R.string.customer_entry_balance, formatter.format(line.balanceAfter)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.invoiceId != null) {
            AuxoIcon(R.drawable.ic_arrow_forward, contentDescription = null, size = AuxoIconSize.Mini, style = AuxoIconStyle.Flat, modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

/** The "All bills" tab: every bill and credit note for the customer, with whether it is paid, newest month first. */
fun LazyListScope.allBillsTab(
    state: CustomerDetailUiState.Ready,
    filter: BillFilter,
    onFilter: (BillFilter) -> Unit,
    onOpenInvoice: (Long) -> Unit,
) {
    item(key = "bills_hint") { TabIntro(stringResource(R.string.customer_bills_hint)) }
    if (state.invoices.isEmpty()) {
        item(key = "no_bills") { TabEmpty(stringResource(R.string.customer_bills_empty)) }
        return
    }
    item(key = "bill_filters") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(selected = filter == BillFilter.ALL, onClick = { onFilter(BillFilter.ALL) }, label = { Text(stringResource(R.string.customer_filter_all)) })
            FilterChip(selected = filter == BillFilter.UNPAID, onClick = { onFilter(BillFilter.UNPAID) }, label = { Text(stringResource(R.string.customer_filter_unpaid)) })
            FilterChip(selected = filter == BillFilter.RETURNS, onClick = { onFilter(BillFilter.RETURNS) }, label = { Text(stringResource(R.string.customer_filter_returns)) })
        }
    }
    fun statusOf(bill: InvoiceSummary) = Ledger.billStatus(bill.type, bill.grandTotal, state.billOwed[bill.id] ?: Money.ZERO)
    val bills = state.invoices.filter { bill ->
        when (filter) {
            BillFilter.ALL -> true
            BillFilter.UNPAID -> statusOf(bill).let { it == BillStatus.UNPAID || it == BillStatus.PART_PAID }
            BillFilter.RETURNS -> bill.type == InvoiceType.RETURN
        }
    }
    if (bills.isEmpty()) {
        item(key = "bills_none") { TabEmpty(stringResource(R.string.customer_filter_empty)) }
        return
    }
    bills.groupBy { monthOf(it.createdAtMillis) }.forEach { (month, inMonth) ->
        item(key = "bills-head-$month") {
            val formatter = LocalMoneyFormatter.current
            val total = inMonth.fold(Money.ZERO) { acc, bill -> if (bill.type == InvoiceType.RETURN) acc - bill.grandTotal else acc + bill.grandTotal }
            MonthHeader(month.format(MONTH_LABEL), formatter.format(total), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        inMonth.forEach { bill ->
            item(key = "invoice-${bill.id}") {
                BillRow(bill, statusOf(bill), state.billOwed[bill.id] ?: Money.ZERO, onClick = { onOpenInvoice(bill.id) })
            }
        }
    }
}

@Composable
private fun BillRow(bill: InvoiceSummary, status: BillStatus, owed: Money, onClick: () -> Unit) {
    val formatter = LocalMoneyFormatter.current
    val colors = AuxoThemeExtras.statusColors
    val isReturn = bill.type == InvoiceType.RETURN
    AuxoCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(bill.number, style = MaterialTheme.typography.titleSmall)
                Text(DateFormats.dateTime(bill.createdAtMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val (text, tone) = when (status) {
                    BillStatus.PAID -> stringResource(R.string.bill_status_paid) to colors.success
                    BillStatus.PART_PAID -> stringResource(R.string.bill_status_part, formatter.format(owed)) to colors.warning
                    BillStatus.UNPAID -> stringResource(R.string.bill_status_unpaid, formatter.format(owed)) to colors.danger
                    BillStatus.RETURN -> stringResource(R.string.history_return_badge) to colors.neutral
                }
                StatusChip(text = text, tone = tone, modifier = Modifier.testTag("bill_status_${bill.id}"))
            }
            Text(
                text = (if (isReturn) "−" else "") + formatter.format(bill.grandTotal),
                style = MaterialTheme.typography.titleSmall,
                color = if (isReturn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            AuxoIcon(R.drawable.ic_arrow_forward, contentDescription = null, size = AuxoIconSize.Mini, style = AuxoIconStyle.Flat, modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}
