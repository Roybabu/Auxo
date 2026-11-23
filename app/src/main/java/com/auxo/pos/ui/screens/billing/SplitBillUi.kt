// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.domain.billing.BillSplit
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.SplitError
import com.auxo.pos.domain.billing.SplitOutcome
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.ui.components.rememberDialogListMaxHeight
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.Spacing

private enum class SplitMode { BY_ITEM, EQUAL }

/**
 * Splitting the bill on the counter between people. By item: the chosen items (and how many of each) become their
 * own parked bill, to be paid separately. Equally: shows what each person pays; the shares are then collected one by
 * one on the payment screen, which takes several payments.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SplitBillDialog(
    cart: Cart,
    total: Money,
    onSplitOff: (Map<Long, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = LocalMoneyFormatter.current
    var mode by rememberSaveable { mutableStateOf(SplitMode.BY_ITEM) }
    var people by rememberSaveable { mutableIntStateOf(BillSplit.MIN_PEOPLE) }
    val moving = remember { mutableStateMapOf<Long, Int>() }
    val outcome = BillSplit.splitOff(cart, moving.toMap())
    val blocked = (outcome as? SplitOutcome.Failed)?.error?.takeIf { it == SplitError.BILL_DISCOUNT || it == SplitError.POINTS }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = mode == SplitMode.BY_ITEM,
                        onClick = { mode = SplitMode.BY_ITEM },
                        label = { Text(stringResource(R.string.split_by_item)) },
                        modifier = Modifier.testTag("split_mode_item"),
                    )
                    FilterChip(
                        selected = mode == SplitMode.EQUAL,
                        onClick = { mode = SplitMode.EQUAL },
                        label = { Text(stringResource(R.string.split_equally)) },
                        modifier = Modifier.testTag("split_mode_equal"),
                    )
                }
                if (mode == SplitMode.BY_ITEM) {
                    if (blocked != null) {
                        Text(
                            stringResource(if (blocked == SplitError.BILL_DISCOUNT) R.string.split_blocked_discount else R.string.split_blocked_points),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("split_blocked"),
                        )
                    } else {
                        Text(
                            stringResource(R.string.split_item_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = rememberDialogListMaxHeight(300.dp)).testTag("split_lines")) {
                            itemsIndexed(cart.lines, key = { _, line -> line.variantId }) { _, line ->
                                val chosen = moving[line.variantId] ?: 0
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.productName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                        Text(
                                            line.quantityText + " · " + formatter.format(line.gross),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (line.stockUnit == StockUnit.UNIT) {
                                        TextButton(
                                            onClick = { moving[line.variantId] = (chosen - 1).coerceAtLeast(0) },
                                            enabled = chosen > 0,
                                            modifier = Modifier.testTag("split_minus_${line.variantId}"),
                                        ) { Text("−") }
                                        Text(chosen.toString(), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("split_count_${line.variantId}"))
                                        TextButton(
                                            onClick = { moving[line.variantId] = (chosen + 1).coerceAtMost(line.quantity) },
                                            enabled = chosen < line.quantity,
                                            modifier = Modifier.testTag("split_plus_${line.variantId}"),
                                        ) { Text("+") }
                                    } else {
                                        Switch(
                                            checked = chosen > 0,
                                            onCheckedChange = { moving[line.variantId] = if (it) line.quantity else 0 },
                                            modifier = Modifier.testTag("split_switch_${line.variantId}"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.split_people), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(
                            onClick = { people = (people - 1).coerceAtLeast(BillSplit.MIN_PEOPLE) },
                            enabled = people > BillSplit.MIN_PEOPLE,
                            modifier = Modifier.testTag("split_people_minus"),
                        ) { Text("−") }
                        Text(people.toString(), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("split_people_count"))
                        TextButton(
                            onClick = { people = (people + 1).coerceAtMost(BillSplit.MAX_PEOPLE) },
                            enabled = people < BillSplit.MAX_PEOPLE,
                            modifier = Modifier.testTag("split_people_plus"),
                        ) { Text("+") }
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = rememberDialogListMaxHeight(240.dp)).testTag("split_shares")) {
                        itemsIndexed(BillSplit.equalShares(total, people)) { index, share ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                                Text(stringResource(R.string.split_person, index + 1), modifier = Modifier.weight(1f))
                                Text(formatter.format(share), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("split_share_$index"))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.split_equal_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (mode == SplitMode.BY_ITEM) {
                TextButton(
                    onClick = { onSplitOff(moving.toMap()) },
                    enabled = outcome is SplitOutcome.Ok,
                    modifier = Modifier.testTag("split_confirm"),
                ) { Text(stringResource(R.string.split_confirm)) }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("split_close")) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (mode == SplitMode.BY_ITEM) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
