package com.invision.pos.ui.screens.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.inventory.InventoryFilter
import com.invision.pos.domain.inventory.StockFilter
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.StockStatus
import com.invision.pos.ui.components.ActionIcon
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.ScanOutcome
import com.invision.pos.ui.components.StatusChip
import com.invision.pos.ui.components.rememberBarcodeScanner
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.InventoryEvent
import com.invision.pos.viewmodel.InventoryUiState
import com.invision.pos.viewmodel.InventoryViewModel
import java.time.LocalDate

@Composable
fun InventoryListScreen(
    onOpenProduct: (Long) -> Unit,
    onAddProduct: (barcode: String?) -> Unit,
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnOpenProduct by rememberUpdatedState(onOpenProduct)
    val currentOnAddProduct by rememberUpdatedState(onAddProduct)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InventoryEvent.OpenProduct -> currentOnOpenProduct(event.productId)
                is InventoryEvent.BarcodeNotFound -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.scan_not_found, event.barcode),
                        actionLabel = resources.getString(R.string.inventory_add_product),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) currentOnAddProduct(event.barcode)
                }
                InventoryEvent.ScannerUnavailable ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.scan_unavailable))
            }
        }
    }

    val scan = rememberBarcodeScanner { outcome ->
        when (outcome) {
            is ScanOutcome.Scanned -> viewModel.onBarcodeScanned(outcome.value)
            ScanOutcome.Unavailable -> viewModel.onScannerUnavailable()
            ScanOutcome.Cancelled -> Unit
        }
    }

    Scaffold(
        topBar = {
            InvisionTopAppBar(
                title = stringResource(R.string.inventory_title),
                actions = {
                    ActionIcon(
                        iconRes = R.drawable.ic_scan,
                        contentDescription = stringResource(R.string.action_scan),
                        onClick = scan,
                    )
                },
            )
        },
        floatingActionButton = {
            if (uiState.totalProductCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { onAddProduct(null) },
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.inventory_add_product)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        InventoryListContent(
            uiState = uiState,
            onSearchTextChange = viewModel::onSearchTextChange,
            onSearchSubmitted = viewModel::onSearchSubmitted,
            onCategorySelected = viewModel::onCategorySelected,
            onStockFilterSelected = viewModel::onStockFilterSelected,
            onClearFilters = viewModel::clearFilters,
            onRetry = viewModel::retry,
            onOpenProduct = onOpenProduct,
            onAddProduct = { onAddProduct(null) },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun InventoryListContent(
    uiState: InventoryUiState,
    onSearchTextChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onCategorySelected: (ProductCategory?) -> Unit,
    onStockFilterSelected: (StockFilter) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onAddProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> LoadingState(modifier)
        uiState.loadFailed -> ErrorState(
            message = stringResource(R.string.error_generic),
            onRetry = onRetry,
            modifier = modifier,
        )
        uiState.totalProductCount == 0 -> EmptyState(
            iconRes = R.drawable.ic_nav_inventory,
            title = stringResource(R.string.inventory_empty_title),
            message = stringResource(R.string.inventory_empty_body),
            actionLabel = stringResource(R.string.inventory_add_product),
            onAction = onAddProduct,
            modifier = modifier,
        )
        else -> Column(modifier = modifier.fillMaxSize()) {
            InventoryFilters(
                uiState = uiState,
                onSearchTextChange = onSearchTextChange,
                onSearchSubmitted = onSearchSubmitted,
                onCategorySelected = onCategorySelected,
                onStockFilterSelected = onStockFilterSelected,
            )
            if (uiState.products.isEmpty()) {
                EmptyState(
                    iconRes = R.drawable.ic_search,
                    title = stringResource(R.string.inventory_no_results_title),
                    message = stringResource(R.string.inventory_no_results_body),
                    actionLabel = stringResource(R.string.action_clear_filters),
                    onAction = onClearFilters,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("inventory_list"),
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.sm,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(uiState.products, key = { it.id }) { product ->
                        ProductRow(product = product, today = uiState.today, onClick = { onOpenProduct(product.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryFilters(
    uiState: InventoryUiState,
    onSearchTextChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onCategorySelected: (ProductCategory?) -> Unit,
    onStockFilterSelected: (StockFilter) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        InvisionTextField(
            value = uiState.query.text,
            onValueChange = onSearchTextChange,
            label = stringResource(R.string.inventory_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("inventory_search"),
            imeAction = ImeAction.Search,
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmitted() }),
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (uiState.query.text.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchTextChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
                    }
                }
            } else {
                null
            },
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(
                selected = uiState.query.category == null,
                onClick = { onCategorySelected(null) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
            ProductCategory.entries.forEach { category ->
                FilterChip(
                    selected = uiState.query.category == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(stringResource(category.filterLabelRes())) },
                    leadingIcon = {
                        Icon(painterResource(category.iconRes()), contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StockFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.query.stockFilter == filter,
                    onClick = { onStockFilterSelected(filter) },
                    label = { Text(stringResource(filter.labelRes())) },
                )
            }
        }
    }
}

@Composable
fun ProductRow(product: Product, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    val status = InvisionThemeExtras.statusColors
    val hasExpired = product.variants.any { it.isExpired(today) }
    val expiringSoon = product.variants.any { it.expiresWithin(today, InventoryFilter.EXPIRY_WARNING_DAYS) }
    // Same priority as Home alerts: expired, out of stock, expiring soon, then stock level.
    val (chipText, chipTone) = when {
        hasExpired -> stringResource(R.string.status_expired) to status.danger
        product.stockStatus == StockStatus.OUT_OF_STOCK -> stringResource(R.string.stock_status_out) to status.danger
        expiringSoon -> stringResource(R.string.status_expiring) to status.warning
        else -> stringResource(product.stockStatus.labelRes()) to product.stockStatus.tone()
    }
    val low = product.lowestPrice
    val high = product.highestPrice
    val priceText = when {
        !product.category.isSold -> ""
        low == null || high == null -> ""
        low == high -> formatter.format(low)
        else -> stringResource(R.string.price_range, formatter.format(low), formatter.format(high))
    }

    InvisionCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    painter = painterResource(product.category.iconRes()),
                    contentDescription = stringResource(product.category.labelRes()),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(Spacing.sm)
                        .size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    product.brand,
                    pluralStringResource(R.plurals.variant_count, product.variants.size, product.variants.size),
                    priceText.ifEmpty { null },
                ).joinToString(" · ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StatusChip(text = chipText, tone = chipTone)
                Text(
                    text = stockText(product.stockUnit, product.totalStock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
