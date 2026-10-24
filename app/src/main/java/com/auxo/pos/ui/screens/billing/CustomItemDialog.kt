// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.auxo.pos.R
import com.auxo.pos.domain.billing.CustomItem
import com.auxo.pos.domain.billing.CustomItemProblem
import com.auxo.pos.domain.billing.CustomItemResult
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.formatRatePercent
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.theme.Spacing

/**
 * Asks for the details of an item that is not in the shop's list yet, so it can still go on the bill.
 * [showGst] is false for a shop that does not charge GST. [onAdd] returns true when the item was added.
 */
@Composable
fun CustomItemDialog(
    showGst: Boolean,
    onAdd: (name: String, price: String, quantity: String, gstBasisPoints: Int) -> Boolean,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var gst by rememberSaveable { mutableIntStateOf(if (showGst) DEFAULT_GST else 0) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val problems = (CustomItem.validate(name, price, quantity, gst) as? CustomItemResult.Invalid)?.problems.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_item_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    stringResource(R.string.custom_item_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AuxoTextField(
                    value = name,
                    onValueChange = { name = it.take(CustomItem.NAME_MAX) },
                    label = stringResource(R.string.custom_item_name),
                    error = if (showErrors && CustomItemProblem.NAME_REQUIRED in problems) stringResource(R.string.error_required) else null,
                    modifier = Modifier.fillMaxWidth().testTag("custom_item_name"),
                )
                AuxoTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' }.take(13) },
                    label = stringResource(R.string.custom_item_price),
                    error = if (showErrors && CustomItemProblem.PRICE_INVALID in problems) stringResource(R.string.error_invalid_price) else null,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth().testTag("custom_item_price"),
                )
                AuxoTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() }.take(3) },
                    label = stringResource(R.string.custom_item_quantity),
                    error = if (showErrors && CustomItemProblem.QUANTITY_INVALID in problems) stringResource(R.string.custom_item_quantity_error, CustomItem.QUANTITY_MAX) else null,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth().testTag("custom_item_quantity"),
                )
                if (showGst) {
                    Text(stringResource(R.string.custom_item_gst), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        GstRate.STANDARD_BASIS_POINTS.forEach { basisPoints ->
                            FilterChip(
                                selected = gst == basisPoints,
                                onClick = { gst = basisPoints },
                                label = { Text("${formatRatePercent(basisPoints)}%") },
                                modifier = Modifier.testTag("custom_item_gst_$basisPoints"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showErrors = true
                    if (problems.isEmpty() && onAdd(name, price, quantity, gst)) onDismiss()
                },
                modifier = Modifier.testTag("custom_item_add"),
            ) { Text(stringResource(R.string.custom_item_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val DEFAULT_GST = 1_800
