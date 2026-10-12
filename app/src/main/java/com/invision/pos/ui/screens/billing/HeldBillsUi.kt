package com.invision.pos.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invision.pos.R
import com.invision.pos.domain.billing.BusinessTime
import com.invision.pos.domain.billing.HeldBillSummary
import com.invision.pos.domain.billing.HeldBills
import com.invision.pos.domain.billing.ServiceType
import com.invision.pos.ui.components.ConfirmDialog
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.Spacing
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
        InvisionTextField(
            value = label,
            onValueChange = { onLabelChange(it.take(HeldBills.LABEL_MAX)) },
            label = stringResource(R.string.hold_label),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hold_label"),
        )
    }
}

/** The parked bills, each with Resume and Discard. Discard asks for confirmation first. */
@Composable
fun HeldBillsDialog(
    bills: List<HeldBillSummary>,
    counterIsEmpty: Boolean,
    resumedId: Long?,
    onResume: (Long) -> Unit,
    onDiscard: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = LocalMoneyFormatter.current
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
                            if (bill.isOpenOrder) {
                                val where = if (bill.serviceType == ServiceType.DINE_IN && !bill.tableNumber.isNullOrBlank()) {
                                    stringResource(R.string.order_dine_in_table, bill.tableNumber)
                                } else {
                                    stringResource(R.string.order_takeaway)
                                }
                                Text(
                                    where + " · " + pluralStringResource(R.plurals.order_kots, bill.kotCount, bill.kotCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("held_order_${bill.id}"),
                                )
                            }
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
                                TextButton(onClick = { confirming = bill }, modifier = Modifier.testTag("held_discard_${bill.id}")) {
                                    Text(stringResource(R.string.held_discard), color = MaterialTheme.colorScheme.error)
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
    confirming?.let { bill ->
        ConfirmDialog(
            title = stringResource(R.string.held_discard_title),
            message = stringResource(R.string.held_discard_body, bill.label.ifBlank { stringResource(R.string.held_unnamed) }),
            confirmLabel = stringResource(R.string.held_discard),
            onConfirm = {
                confirming = null
                onDiscard(bill.id)
            },
            onDismiss = { confirming = null },
            destructive = true,
        )
    }
}
