package com.auxo.pos.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.domain.billing.BusinessTime
import com.auxo.pos.domain.billing.HeldBill
import com.auxo.pos.domain.billing.HeldBillSummary
import com.auxo.pos.domain.billing.HeldBills
import com.auxo.pos.ui.components.ConfirmDialog
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.Spacing
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HELD_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.ENGLISH)

/** Asks what to call the bill being parked (a customer name, a table). The name is optional. */
@Composable
fun HoldBillDialog(suggestion: String, onHold: (String) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf(suggestion) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hold_title)) },
        text = { HoldBillForm(label = label, onLabelChange = { label = it }) },
        confirmButton = {
            TextButton(onClick = { onHold(label) }, modifier = Modifier.testTag("hold_confirm")) { Text(stringResource(R.string.hold_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** What the hold dialog asks for. Kept apart from the dialog window so it can be tested on its own. */
@Composable
fun HoldBillForm(label: String, onLabelChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.hold_body), style = MaterialTheme.typography.bodyMedium)
        AuxoTextField(
            value = label,
            onValueChange = { onLabelChange(it.take(HeldBills.LABEL_MAX)) },
            label = stringResource(R.string.hold_label),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hold_label"),
        )
    }
}

/**
 * The parked bills, each with Resume and Review. A held bill cannot be discarded straight from the list: Review opens it
 * (its items and total) and Discard is there, behind an "are you sure".
 */
@Composable
fun HeldBillsDialog(
    bills: List<HeldBillSummary>,
    counterIsEmpty: Boolean,
    resumedId: Long?,
    onResume: (Long) -> Unit,
    onDiscard: (Long) -> Unit,
    onDismiss: () -> Unit,
    /** Reads one held bill for the review; the default shows it as unreadable. */
    onLoad: suspend (Long) -> HeldBill? = { null },
) {
    val formatter = LocalMoneyFormatter.current
    var reviewing by remember { mutableStateOf<HeldBillSummary?>(null) }
    var confirming by remember { mutableStateOf<HeldBillSummary?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.held_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (bills.isEmpty()) {
                    Text(stringResource(R.string.held_empty), style = MaterialTheme.typography.bodyMedium)
                } else if (!counterIsEmpty) {
                    Text(
                        stringResource(R.string.held_counter_busy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp).testTag("held_list")) {
                    items(bills, key = { it.id }) { bill ->
                        Column(modifier = Modifier.fillMaxWidth().testTag("held_${bill.id}")) {
                            Text(
                                bill.label.ifBlank { stringResource(R.string.held_unnamed) },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                pluralStringResource(R.plurals.held_items, bill.itemCount, bill.itemCount) + " · " +
                                    formatter.format(bill.total) + " · " +
                                    HELD_TIME.format(Instant.ofEpochMilli(bill.updatedAtMillis).atZone(BusinessTime.ZONE)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (bill.id == resumedId) {
                                    Text(stringResource(R.string.held_on_counter), style = MaterialTheme.typography.labelMedium)
                                } else {
                                    TextButton(
                                        onClick = { onResume(bill.id) },
                                        enabled = counterIsEmpty,
                                        modifier = Modifier.testTag("held_resume_${bill.id}"),
                                    ) { Text(stringResource(R.string.held_resume)) }
                                }
                                TextButton(onClick = { reviewing = bill }, modifier = Modifier.testTag("held_review_open_${bill.id}")) {
                                    Text(stringResource(R.string.held_review))
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
    reviewing?.let { bill ->
        HeldBillReviewDialog(
            summary = bill,
            onLoad = onLoad,
            canResume = counterIsEmpty && bill.id != resumedId,
            onResume = {
                reviewing = null
                onResume(bill.id)
            },
            onDiscard = { confirming = bill },
            onClose = { reviewing = null },
        )
    }
    confirming?.let { bill ->
        ConfirmDialog(
            title = stringResource(R.string.held_discard_title),
            message = stringResource(R.string.held_discard_body, bill.label.ifBlank { stringResource(R.string.held_unnamed) }),
            confirmLabel = stringResource(R.string.held_discard),
            onConfirm = {
                confirming = null
                reviewing = null
                onDiscard(bill.id)
            },
            onDismiss = { confirming = null },
            destructive = true,
        )
    }
}

/** What the review shows while the bill is read: nothing yet, the bill, or "cannot be read". */
private sealed interface ReviewLoad {
    data object Loading : ReviewLoad
    data class Loaded(val bill: HeldBill?) : ReviewLoad
}

/**
 * A look inside a held bill before deciding what to do with it. A held bill cannot be thrown away straight from the
 * list: Discard lives here, and asks "are you sure" first, so one stray tap in the list never removes a bill.
 */
@Composable
private fun HeldBillReviewDialog(
    summary: HeldBillSummary,
    onLoad: suspend (Long) -> HeldBill?,
    canResume: Boolean,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onClose: () -> Unit,
) {
    val formatter = LocalMoneyFormatter.current
    val loaded by produceState<ReviewLoad>(ReviewLoad.Loading, summary.id) { value = ReviewLoad.Loaded(onLoad(summary.id)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(summary.label.ifBlank { stringResource(R.string.held_unnamed) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.testTag("held_review_${summary.id}")) {
                Text(
                    pluralStringResource(R.plurals.held_items, summary.itemCount, summary.itemCount) + " · " +
                        formatter.format(summary.total) + " · " +
                        HELD_TIME.format(Instant.ofEpochMilli(summary.updatedAtMillis).atZone(BusinessTime.ZONE)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (val state = loaded) {
                    ReviewLoad.Loading -> Unit
                    is ReviewLoad.Loaded -> {
                        val bill = state.bill
                        if (bill == null) {
                            Text(
                                stringResource(R.string.held_review_unreadable),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("held_review_unreadable"),
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(bill.cart.lines, key = { it.variantId }) { line ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(line.productName, style = MaterialTheme.typography.bodyMedium)
                                            if (line.variantLabel.isNotBlank()) {
                                                Text(
                                                    line.variantLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(
                                            if (line.stockUnit == com.auxo.pos.domain.model.StockUnit.UNIT) {
                                                "${line.quantity} × ${formatter.format(line.unitPrice)}"
                                            } else {
                                                "${line.quantityText} × ${formatter.format(line.unitPrice)}/${line.stockUnit.entrySymbol}"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (canResume) {
                    TextButton(onClick = onResume, modifier = Modifier.testTag("held_review_resume")) {
                        Text(stringResource(R.string.held_resume))
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.testTag("held_review_close")) {
                    Text(stringResource(R.string.held_review_close))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("held_review_discard")) {
                Text(stringResource(R.string.held_discard), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
