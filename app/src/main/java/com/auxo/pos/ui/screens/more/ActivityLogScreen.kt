// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ActivityLogUiState
import com.auxo.pos.viewmodel.ActivityLogViewModel

/** The most recent changes, returns, deletions and sign-ins. Super Admin only. */
@Composable
fun ActivityLogScreen(
    onBack: () -> Unit,
    viewModel: ActivityLogViewModel = viewModel(factory = ActivityLogViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.activity_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        ActivityLogContent(state, viewModel::retry, Modifier.padding(padding))
    }
}

@Composable
fun ActivityLogContent(state: ActivityLogUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry, modifier)
        state.entries.isEmpty() -> EmptyState(
            iconRes = R.drawable.ic_history,
            title = stringResource(R.string.activity_empty_title),
            message = stringResource(R.string.activity_empty_body),
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize().testTag("activity_list"),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "note") {
                Text(
                    stringResource(R.string.activity_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.entries, key = { it.id }) { entry ->
                AuxoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.activity_by, entry.actorName, DateFormats.dateTime(entry.createdAtMillis)) +
                                entry.entityId?.let { " · ${entry.entityType} #$it" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
