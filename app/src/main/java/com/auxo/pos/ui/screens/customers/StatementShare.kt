// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.customers

import android.content.Context
import com.auxo.pos.R
import com.auxo.pos.core.format.MoneyFormatter
import com.auxo.pos.core.locale.LocaleContext
import com.auxo.pos.core.receipt.StatementContent
import com.auxo.pos.core.receipt.StatementPdf
import com.auxo.pos.core.receipt.StatementRow
import com.auxo.pos.core.receipt.WhatsAppShare
import com.auxo.pos.domain.billing.BusinessTime
import com.auxo.pos.domain.customers.ReminderShop
import com.auxo.pos.domain.model.Money
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.viewmodel.CustomerDetailUiState
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The statement text for [state], written in the shop's bill language. */
internal fun statementContent(context: Context, state: CustomerDetailUiState.Ready, shop: ReminderShop, formatter: MoneyFormatter): StatementContent {
    val resources = LocaleContext.wrap(context, shop.language).resources
    val customer = state.account.customer
    val balance = state.account.balance
    val owes = balance >= Money.ZERO
    fun day(millis: Long) = DateFormats.date(Instant.ofEpochMilli(millis).atZone(BusinessTime.ZONE).toLocalDate())
    return StatementContent(
        shopName = shop.shopName,
        title = resources.getString(R.string.statement_pdf_title),
        customer = customer.name,
        details = listOfNotNull(customer.phone, resources.getString(R.string.statement_pdf_on, DateFormats.date(LocalDate.now(BusinessTime.ZONE)))).joinToString(" · "),
        summary = listOf(
            resources.getString(if (owes) R.string.customer_detail_balance else R.string.customer_detail_advance) to
                formatter.format(if (owes) balance else Money(-balance.minorUnits)),
            resources.getString(R.string.customer_bought) to formatter.format(state.bought),
            resources.getString(R.string.customer_paid) to formatter.format(state.paid),
        ),
        headers = listOf(
            resources.getString(R.string.statement_col_date),
            resources.getString(R.string.statement_col_entry),
            resources.getString(R.string.statement_col_amount),
            resources.getString(R.string.statement_col_balance),
        ),
        rows = state.statement.asReversed().map { line ->
            val entry = line.entry
            val sign = if (entry.amount > Money.ZERO) "+" else "−"
            val shown = if (entry.amount > Money.ZERO) entry.amount else Money(-entry.amount.minorUnits)
            StatementRow(
                date = day(entry.createdAtMillis),
                entry = listOfNotNull(resources.getString(entry.type.labelRes()), entry.invoiceNumber).joinToString(" · "),
                amount = sign + formatter.format(shown),
                balance = formatter.format(line.balanceAfter),
            )
        },
        footer = shop.upiId?.let { resources.getString(R.string.reminder_upi, it) },
    )
}

/** Writes the statement PDF off the main thread and opens WhatsApp with it. Nothing is sent; the person presses send. */
internal suspend fun shareStatement(context: Context, state: CustomerDetailUiState.Ready, shop: ReminderShop, formatter: MoneyFormatter): Boolean {
    val resources = LocaleContext.wrap(context, shop.language).resources
    val name = state.account.customer.name.filter { it.isLetterOrDigit() }.ifEmpty { "customer" }
    val file = runCatching {
        withContext(Dispatchers.IO) {
            StatementPdf.writeFile(context, statementContent(context, state, shop, formatter), "statement-$name-${LocalDate.now(BusinessTime.ZONE)}.pdf")
        }
    }.getOrNull() ?: return false
    return WhatsAppShare.shareFile(
        context,
        file,
        caption = resources.getString(R.string.statement_share_caption, shop.shopName),
        chooserTitle = context.getString(R.string.statement_share_title),
    )
}
