package com.auxo.pos.ui.screens.stock

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.export.FileExport
import com.auxo.pos.domain.model.StockMovementRecord
import com.auxo.pos.ui.components.ActionIcon
import com.auxo.pos.ui.selectableCategories
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.StatTile
import com.auxo.pos.ui.components.StatusChip
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.screens.billing.labelRes
import com.auxo.pos.ui.screens.more.label
import com.auxo.pos.ui.screens.inventory.labelRes
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.HistoryRange
import com.auxo.pos.viewmodel.MovementGroup
import com.auxo.pos.viewmodel.StockMovementsEvent
import com.auxo.pos.viewmodel.StockMovementsUiState
import com.auxo.pos.viewmodel.StockMovementsViewModel

@StringRes
fun MovementGroup.labelRes(): Int = when (this) {
    MovementGroup.ALL -> R.string.filter_all
    MovementGroup.STOCK_IN -> R.string.movement_group_in
    MovementGroup.SOLD -> R.string.reason_sold
    MovementGroup.USED -> R.string.movement_group_used
    MovementGroup.WASTAGE -> R.string.movement_group_wastage
    MovementGroup.CORRECTIONS -> R.string.movement_group_corrections
}

@Composable
fun StockMovementsScreen(
    onBack: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    viewModel: StockMovementsViewModel = viewModel(factory = StockMovementsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StockMovementsEvent.Export ->
                    if (!FileExport.share(context, event.file, resources.getString(R.string.export_chooser))) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.export_failed))
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            AuxoTopAppBar(
                title = stringResource(R.string.movements_title),
                onBack = onBack,
                actions = {
                    if (uiState.movements.isNotEmpty()) {
                        ActionIcon(R.drawable.ic_download, stringResource(R.string.action_export_csv), viewModel::export)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        StockMovementsContent(
            state = uiState,
            onRangeChange = viewModel::onRangeChange,
            onGroupChange = viewModel::onGroupChange,
            onCategoryChange = viewModel::onCategoryChange,
            onQueryChange = viewModel::onQueryChange,
            onRetry = viewModel::retry,
            onOpenProduct = onOpenProduct,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun StockMovementsContent(
    state: StockMovementsUiState,
    onRangeChange: (HistoryRange) -> Unit,
    onGroupChange: (MovementGroup) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalMoneyFormatter.current
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AuxoTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.movements_search),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("movements_search"),
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HistoryRange.entries.forEach { range ->
                    FilterChip(selected = state.range == range, onClick = { onRangeChange(range) }, label = { Text(stringResource(range.labelRes())) })
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MovementGroup.entries.forEach { group ->
                    FilterChip(selected = state.group == group, onClick = { onGroupChange(group) }, label = { Text(stringResource(group.labelRes())) })
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(selected = state.categoryId == null, onClick = { onCategoryChange(null) }, label = { Text(stringResource(R.string.movements_all_items)) })
                selectableCategories(alsoShow = state.categoryId).forEach { row ->
                    FilterChip(
                        selected = state.categoryId == row.id,
                        onClick = { onCategoryChange(row.id) },
                        label = { Text(row.label()) },
                        modifier = Modifier.testTag("movement_category_${row.id}"),
                    )
                }
            }
        }
        when {
            state.isLoading -> LoadingState()
            state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry)
            state.movements.isEmpty() -> EmptyState(
                iconRes = R.drawable.ic_swap,
                title = stringResource(R.string.movements_empty_title),
                message = stringResource(R.string.movements_empty_body),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.testTag("movement_list"),
            ) {
                item(key = "tiles") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTile(stringResource(R.string.movements_in), state.receivedCount.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.movements_out), state.outCount.toString(), Modifier.weight(1f))
                        StatTile(
                            stringResource(R.string.movements_wastage_value),
                            formatter.format(state.wastageValue),
                            Modifier.weight(1f),
                            tone = if (state.wastageValue.isZero) null else AuxoThemeExtras.statusColors.danger,
                        )
                    }
                }
                items(state.movements, key = { it.movement.id }) { record ->
                    MovementCard(record, onClick = { onOpenProduct(record.productId) })
                }
            }
        }
    }
}

@Composable
private fun MovementCard(record: StockMovementRecord, onClick: () -> Unit) {
    val status = AuxoThemeExtras.statusColors
    val movement = record.movement
    val change = movement.quantityChange
    val unit = record.stockUnit
    AuxoCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    listOf(record.productName, record.variantLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusChip(
                        stringResource(movement.reason.labelRes()),
                        when {
                            movement.reason.isWastage -> status.danger
                            change > 0 -> status.success
                            else -> status.neutral
                        },
                    )
                    Text(
                        text = listOfNotNull(
                            DateFormats.dateTime(movement.createdAtMillis),
                            record.reference,
                            record.batchNumber?.let { stringResource(R.string.detail_batch, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                movement.note?.takeIf { it != record.reference }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = if (change > 0) "+" + unit.format(change) else unit.format(change),
                style = MaterialTheme.typography.titleMedium,
                color = if (change > 0) status.success.accent else status.danger.accent,
            )
        }
    }
}
