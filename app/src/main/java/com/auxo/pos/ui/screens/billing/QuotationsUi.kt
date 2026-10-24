// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.domain.billing.QuotationStatus
import com.auxo.pos.domain.billing.QuotationSummary
import com.auxo.pos.domain.billing.Quotations
import com.auxo.pos.ui.components.ConfirmDialog
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val QUOTE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

/**
 * Quotations: save the bill on the counter as one (when there is a bill), and a list of the saved ones with
 * Convert to bill, Share and Delete. Convert needs an empty counter. A quotation is never a tax invoice.
 */
@Composable
fun QuotationsDialog(
    quotations: List<QuotationSummary>,
    canSave: Boolean,
    counterIsEmpty: Boolean,
    convertingId: Long?,
    today: LocalDate,
    onSave: (validityDays: Int) -> Unit,
    onConvert: (Long) -> Unit,
    onShare: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = LocalMoneyFormatter.current
    var days by rememberSaveable { mutableIntStateOf(Quotations.DEFAULT_VALIDITY_DAYS) }
    var confirming by remember { mutableStateOf<QuotationSummary?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quotes_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.quotes_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (canSave) {
                    Text(stringResource(R.string.quotes_save_heading), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.quotes_valid_for), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Quotations.VALIDITY_DAYS.forEach { option ->
                            FilterChip(
                                selected = days == option,
                                onClick = { days = option },
                                label = { Text(pluralStringResource(R.plurals.quotes_days, option, option)) },
                                modifier = Modifier.testTag("quote_days_$option"),
                            )
                        }
                    }
                    Button(onClick = { onSave(days) }, modifier = Modifier.fillMaxWidth().testTag("quote_save")) {
                        Text(stringResource(R.string.quotes_save))
                    }
                    HorizontalDivider()
                }
                if (quotations.isEmpty()) {
                    Text(stringResource(R.string.quotes_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("quote_empty"))
                } else if (!counterIsEmpty) {
                    Text(stringResource(R.string.quotes_counter_busy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp).testTag("quote_list")) {
                    items(quotations, key = { it.id }) { quote ->
                        Column(modifier = Modifier.fillMaxWidth().testTag("quote_${quote.id}")) {
                            Text(quote.number, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOf(quote.customerName.ifBlank { null }, formatter.format(quote.total)).filterNotNull().joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val expired = quote.isExpired(today)
                            Text(
                                when {
                                    quote.status == QuotationStatus.CONVERTED -> stringResource(R.string.quotes_converted)
                                    quote.id == convertingId -> stringResource(R.string.quotes_on_counter)
                                    expired -> stringResource(R.string.quotes_expired, QUOTE_DATE.format(quote.validUntil))
                                    else -> stringResource(R.string.quotes_valid_until, QUOTE_DATE.format(quote.validUntil))
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("quote_status_${quote.id}"),
                            )
                            Row {
                                if (quote.status == QuotationStatus.OPEN && quote.id != convertingId) {
                                    TextButton(
                                        onClick = { onConvert(quote.id) },
                                        enabled = counterIsEmpty,
                                        modifier = Modifier.testTag("quote_convert_${quote.id}"),
                                    ) { Text(stringResource(R.string.quotes_convert)) }
                                }
                                TextButton(onClick = { onShare(quote.id) }, modifier = Modifier.testTag("quote_share_${quote.id}")) {
                                    Text(stringResource(R.string.quotes_share))
                                }
                                TextButton(onClick = { confirming = quote }, modifier = Modifier.testTag("quote_delete_${quote.id}")) {
                                    Text(stringResource(R.string.quotes_delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
    confirming?.let { quote ->
        ConfirmDialog(
            title = stringResource(R.string.quotes_delete_title),
            message = stringResource(R.string.quotes_delete_body, quote.number),
            confirmLabel = stringResource(R.string.quotes_delete),
            onConfirm = {
                confirming = null
                onDelete(quote.id)
            },
            onDismiss = { confirming = null },
            destructive = true,
        )
    }
}
