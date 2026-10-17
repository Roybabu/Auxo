package com.auxo.pos.ui.screens.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import com.auxo.pos.domain.customers.OwingSort
import kotlinx.coroutines.launch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.model.CustomerAccount
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
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
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.CustomerFilter
import com.auxo.pos.viewmodel.CustomerListUiState
import com.auxo.pos.viewmodel.CustomerListViewModel

@Composable
fun CustomerListScreen(
    onOpenCustomer: (Long) -> Unit,
    onAddCustomer: () -> Unit,
    viewModel: CustomerListViewModel = viewModel(factory = CustomerListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val formatter = LocalMoneyFormatter.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AuxoTopAppBar(title = stringResource(R.string.customers_title)) },
        floatingActionButton = {
            if (uiState.customerCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddCustomer,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.customers_add)) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        CustomerListContent(
            state = uiState,
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onRetry = viewModel::retry,
            onOpenCustomer = onOpenCustomer,
            onAddCustomer = onAddCustomer,
            onSortChange = viewModel::onSortChange,
            onRemind = { account ->
                val shop = uiState.reminderShop
                if (shop != null && !openReminder(context, account, shop, formatter)) {
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.reminder_failed)) }
                }
            },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun CustomerListContent(
    state: CustomerListUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CustomerFilter) -> Unit,
    onRetry: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onAddCustomer: () -> Unit,
    modifier: Modifier = Modifier,
    onSortChange: (OwingSort) -> Unit = {},
    onRemind: (CustomerAccount) -> Unit = {},
) {
    val formatter = LocalMoneyFormatter.current
    when {
        state.isLoading -> LoadingState(modifier)
        state.loadFailed -> ErrorState(stringResource(R.string.error_generic), onRetry, modifier)
        state.customerCount == 0 -> EmptyState(
            iconRes = R.drawable.ic_nav_customers,
            title = stringResource(R.string.customers_empty_title),
            message = stringResource(R.string.customers_empty_body),
            actionLabel = stringResource(R.string.customers_add),
            onAction = onAddCustomer,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("customer_list"),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "tiles") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        label = stringResource(R.string.customers_count),
                        value = state.customerCount.toString(),
                        iconRes = R.drawable.ic_nav_customers,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.customers_total_due),
                        value = formatter.format(state.totalDue),
                        iconRes = R.drawable.ic_wallet,
                        tone = if (state.owingCount > 0) AuxoThemeExtras.statusColors.warning else null,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterChange(CustomerFilter.OWING) },
                    )
                }
            }
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AuxoTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        label = stringResource(R.string.customers_search),
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_search"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = state.filter == CustomerFilter.ALL,
                            onClick = { onFilterChange(CustomerFilter.ALL) },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        FilterChip(
                            selected = state.filter == CustomerFilter.OWING,
                            onClick = { onFilterChange(CustomerFilter.OWING) },
                            label = { Text(stringResource(R.string.customers_filter_owing, state.owingCount)) },
                        )
                    }
                    if (state.filter == CustomerFilter.OWING && state.owingCount > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FilterChip(
                                selected = state.sort == OwingSort.AMOUNT,
                                onClick = { onSortChange(OwingSort.AMOUNT) },
                                label = { Text(stringResource(R.string.owing_sort_amount)) },
                                modifier = Modifier.testTag("owing_sort_amount"),
                            )
                            FilterChip(
                                selected = state.sort == OwingSort.AGE,
                                onClick = { onSortChange(OwingSort.AGE) },
                                label = { Text(stringResource(R.string.owing_sort_age)) },
                                modifier = Modifier.testTag("owing_sort_age"),
                            )
                        }
                    }
                }
            }
            if (state.accounts.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(
                            if (state.filter == CustomerFilter.OWING && state.query.isBlank()) R.string.customers_none_owing else R.string.customers_no_results,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.lg),
                    )
                }
            }
            items(state.accounts, key = { it.customer.id }) { account ->
                CustomerRow(
                    account,
                    onClick = { onOpenCustomer(account.customer.id) },
                    ageDays = state.ageDays[account.customer.id].takeIf { state.filter == CustomerFilter.OWING },
                    onRemind = if (state.filter == CustomerFilter.OWING && state.reminderShop != null) {
                        { onRemind(account) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomerRow(account: CustomerAccount, onClick: () -> Unit, ageDays: Int? = null, onRemind: (() -> Unit)? = null) {
    val customer = account.customer
    AuxoCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    customer.phone,
                    account.lastActivityMillis?.let { stringResource(R.string.customer_last_activity, DateFormats.dateTime(it)) },
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ageDays != null) {
                    Text(
                        if (ageDays == 0) stringResource(R.string.owing_age_today) else pluralStringResource(R.plurals.owing_age_days, ageDays, ageDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ageDays >= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("owing_age_${account.customer.id}"),
                    )
                }
            }
            StatusChip(text = balanceText(account.balance), tone = balanceTone(account.balance))
            if (onRemind != null) {
                IconButton(onClick = onRemind, modifier = Modifier.testTag("remind_${account.customer.id}")) {
                    AuxoIcon(R.drawable.ic_chat, stringResource(R.string.reminder_button), size = AuxoIconSize.Compact)
                }
            }
        }
    }
}
