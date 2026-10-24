// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.auxo.pos.R
import com.auxo.pos.domain.inventory.PickableItem
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ItemPickerState

/**
 * Search box that lists matching stock items under it; tapping one adds it.
 * [detail] adds a line under each item, such as its stock.
 */
@Composable
fun ItemPickerField(
    state: ItemPickerState,
    onQueryChange: (String) -> Unit,
    onPick: (PickableItem) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    detail: @Composable (PickableItem) -> String? = { null },
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AuxoTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = label,
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                { ActionIcon(R.drawable.ic_close, stringResource(R.string.action_clear), onClick = { onQueryChange("") }) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("item_picker_search"),
        )
        if (state.query.isNotBlank()) {
            AuxoCard(modifier = Modifier.fillMaxWidth()) {
                if (state.results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.picker_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                } else {
                    Column(modifier = Modifier.testTag("item_picker_results")) {
                        state.results.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(item) }
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    detail(item)?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(
                                    painterResource(R.drawable.ic_add),
                                    contentDescription = stringResource(R.string.action_add),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
