// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.auth

import com.auxo.pos.ui.components.GlassButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.screens.more.label
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.CategorySetupUiState
import com.auxo.pos.viewmodel.CategorySetupViewModel

/** Shown to the admin once, before the app opens: which categories does the shop sell in? */
@Composable
fun CategorySetupScreen(viewModel: CategorySetupViewModel = viewModel(factory = CategorySetupViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategorySetupContent(state, viewModel::onToggle, viewModel::onConfirm)
}

@Composable
fun CategorySetupContent(state: CategorySetupUiState, onToggle: (Long) -> Unit, onConfirm: () -> Unit) {
    AuthLayout(backdropImage = R.drawable.auth_signup_bg, logoWidth = 220.dp, glass = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("category_setup")) {
            Text(stringResource(R.string.category_setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.category_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.categories.forEach { category ->
                AuxoCard(onClick = { onToggle(category.id) }, modifier = Modifier.fillMaxWidth().testTag("setup_category_${category.id}")) {
                    Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = category.id in state.selected, onCheckedChange = { onToggle(category.id) })
                        Text(category.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
            if (state.needsOne) {
                Text(
                    stringResource(R.string.categories_error_last),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("category_setup_needs_one"),
                )
            }
            if (state.failed) {
                Text(
                    stringResource(R.string.categories_error_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("category_setup_failed"),
                )
            }
            GlassButton(
                onClick = onConfirm,
                enabled = state.loaded && !state.saving,
                modifier = Modifier.testTag("category_setup_continue"),
            ) {
                Text(stringResource(R.string.category_setup_action))
            }
            Text(
                stringResource(R.string.category_setup_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
