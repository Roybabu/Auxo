package com.invision.pos.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.invision.pos.R
import com.invision.pos.domain.billing.KitchenOrders
import com.invision.pos.domain.billing.ServiceType
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.BillingUiState

/**
 * Restaurant controls on the counter: dine-in or takeaway, the table, and sending the order to the kitchen.
 * Shown only in restaurant mode to someone who may hold bills.
 */
@Composable
fun ServiceCard(state: BillingUiState, actions: BillingActions, modifier: Modifier = Modifier) {
    val service = state.cart.serviceType
    val tableMissing = state.orderShowErrors && service == ServiceType.DINE_IN &&
        KitchenOrders.normalizeTable(state.cart.tableNumber).isEmpty()
    val serviceMissing = state.orderShowErrors && service == null
    InvisionCard(modifier = modifier.fillMaxWidth().testTag("service_card")) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.order_service_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = service == ServiceType.DINE_IN,
                    onClick = { actions.onServiceTypeChange(ServiceType.DINE_IN) },
                    label = { Text(stringResource(R.string.order_dine_in)) },
                    modifier = Modifier.testTag("service_dine_in"),
                )
                FilterChip(
                    selected = service == ServiceType.TAKEAWAY,
                    onClick = { actions.onServiceTypeChange(ServiceType.TAKEAWAY) },
                    label = { Text(stringResource(R.string.order_takeaway)) },
                    modifier = Modifier.testTag("service_takeaway"),
                )
            }
            if (serviceMissing) {
                Text(stringResource(R.string.order_service_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (service == ServiceType.DINE_IN) {
                InvisionTextField(
                    value = state.cart.tableNumber,
                    onValueChange = actions::onTableNumberChange,
                    label = stringResource(R.string.order_table),
                    error = if (tableMissing) stringResource(R.string.order_table_required) else null,
                    capitalization = KeyboardCapitalization.Characters,
                    modifier = Modifier.fillMaxWidth().testTag("service_table"),
                )
            }
            OutlinedButton(
                onClick = actions::onPlaceOrder,
                enabled = !state.isPlacingOrder && !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("order_place"),
            ) { Text(stringResource(R.string.order_place)) }
        }
    }
}

/** Shown after an order is sent: print or share the kitchen ticket now, since a ticket cannot be reprinted later. */
@Composable
fun OrderPlacedDialog(kotNumber: String, onPrint: () -> Unit, onShare: () -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.order_placed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.order_placed_body, kotNumber), modifier = Modifier.testTag("order_placed_body"))
                Text(stringResource(R.string.order_placed_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onPrint, modifier = Modifier.testTag("order_print")) { Text(stringResource(R.string.order_print)) }
                    OutlinedButton(onClick = onShare, modifier = Modifier.testTag("order_share")) { Text(stringResource(R.string.order_share)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone, modifier = Modifier.testTag("order_done")) { Text(stringResource(R.string.action_ok)) } },
    )
}
