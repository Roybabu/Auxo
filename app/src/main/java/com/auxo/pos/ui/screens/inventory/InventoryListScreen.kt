// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.auxo.pos.R
import com.auxo.pos.domain.inventory.StockFilter
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.StockStatus
import com.auxo.pos.ui.components.rememberIsShortScreen
import com.auxo.pos.ui.components.ActionIcon
import com.auxo.pos.ui.screens.more.label
import com.auxo.pos.ui.selectableCategories
import com.auxo.pos.ui.LocalExpiryWarningDays
import com.auxo.pos.ui.LocalSuppliersEnabled
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.ProductPhoto
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.ScanOutcome
import com.auxo.pos.ui.components.StatusChip
import com.auxo.pos.ui.components.rememberBarcodeScanner
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.InventoryEvent
import com.auxo.pos.viewmodel.InventoryUiState
import com.auxo.pos.viewmodel.InventoryViewModel
import java.time.LocalDate

/** Stock pages reached from the Inventory tab's menu. */
enum class StockPage { ALERTS, RECEIVE, PURCHASES, SUPPLIERS, MOVEMENTS }

@Composable
fun InventoryListScreen(
    onOpenProduct: (Long) -> Unit,
    onAddProduct: (barcode: String?) -> Unit,
    onOpenStockPage: (StockPage) -> Unit = {},
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory),
) {
    var menuOpen by remember { mutableStateOf(false) }
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
            AuxoTopAppBar(
                title = stringResource(R.string.inventory_title),
                actions = {
                    ActionIcon(
                        iconRes = R.drawable.ic_scan,
                        contentDescription = stringResource(R.string.action_scan),
                        onClick = scan,
                    )
                    Box {
                        ActionIcon(
                            iconRes = R.drawable.ic_nav_more,
                            contentDescription = stringResource(R.string.inventory_more_options),
                            onClick = { menuOpen = true },
                            modifier = Modifier.testTag("inventory_menu"),
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val suppliersOn = LocalSuppliersEnabled.current
                            listOf(
                                StockPage.ALERTS to R.string.alerts_title,
                                StockPage.RECEIVE to R.string.purchases_receive,
                                StockPage.PURCHASES to R.string.purchases_title,
                                StockPage.SUPPLIERS to R.string.suppliers_title,
                                StockPage.MOVEMENTS to R.string.movements_title,
                            ).filter { (page, _) -> suppliersOn || (page != StockPage.RECEIVE && page != StockPage.PURCHASES && page != StockPage.SUPPLIERS) }.forEach { (page, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenStockPage(page)
                                    },
                                )
                            }
                        }
                    }
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
            onCategorySelected = viewModel::onCategoryIdSelected,
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
    onCategorySelected: (Long?) -> Unit,
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
            // On a short screen (a phone turned on its side) the filters scroll with the list instead of taking the page.
            val short = rememberIsShortScreen()
            val filters: @Composable (Modifier) -> Unit = { filtersModifier ->
                InventoryFilters(
                    uiState = uiState,
                    onSearchTextChange = onSearchTextChange,
                    onSearchSubmitted = onSearchSubmitted,
                    onCategorySelected = onCategorySelected,
                    onStockFilterSelected = onStockFilterSelected,
                    modifier = filtersModifier,
                )
            }
            if (!short || uiState.products.isEmpty()) filters(Modifier.padding(horizontal = Spacing.screen))
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
                    if (short) item(key = "filters") { filters(Modifier) }
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
    onCategorySelected: (Long?) -> Unit,
    onStockFilterSelected: (StockFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AuxoTextField(
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
                selected = uiState.query.category == null && uiState.query.categoryId == null,
                onClick = { onCategorySelected(null) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
            val rows = selectableCategories()
            rows.forEach { row ->
                FilterChip(
                    selected = uiState.query.categoryId == row.id,
                    onClick = { onCategorySelected(row.id) },
                    label = { Text(row.label()) },
                    leadingIcon = {
                        Icon(painterResource(row.profile.iconRes()), contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("filter_category_${row.id}"),
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

/** Size of a product photo in the product list. */
private val PHOTO_SIZE = 44.dp

/** Size of each small colour photo under a product's name in the list, and how many are shown before "+N". */
private val ROW_PHOTO_SIZE = 24.dp
private const val ROW_PHOTO_LIMIT = 5

@Composable
fun ProductRow(product: Product, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formatter = LocalMoneyFormatter.current
    val status = AuxoThemeExtras.statusColors
    val hasExpired = product.variants.any { it.isExpired(today) }
    val warningDays = LocalExpiryWarningDays.current.toLong()
    val expiringSoon = product.variants.any { it.expiresWithin(today, warningDays) }
    // Same priority as Home alerts: expired, out of stock, expiring soon, then stock level.
    val (chipText, chipTone) = when {
        // An item sold without a stock count has no stock level; it is only flagged while switched off.
        !product.tracksStock -> (if (product.available) null else stringResource(R.string.menu_unavailable)) to status.danger
        hasExpired -> stringResource(R.string.status_expired) to status.danger
        product.stockStatus == StockStatus.OUT_OF_STOCK -> stringResource(R.string.stock_status_out) to status.danger
        expiringSoon -> stringResource(R.string.status_expiring) to status.warning
        else -> stringResource(product.stockStatus.labelRes()) to product.stockStatus.tone()
    }
    val low = product.lowestPrice
    val high = product.highestPrice
    val priceText = when {
        low == null || high == null -> ""
        low == high -> formatter.format(low)
        else -> stringResource(R.string.price_range, formatter.format(low), formatter.format(high))
    }

    AuxoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ProductPhoto(
                photoFile = product.displayPhotoFile,
                contentDescription = product.name,
                modifier = Modifier
                    .size(PHOTO_SIZE)
                    .clip(RoundedCornerShape(10.dp)),
                maxSidePx = 160,
            ) {
                AuxoIcon(product.category.iconRes(), product.categoryLabel(), size = AuxoIconSize.Medium)
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
                // A product with photos for several colours shows a small thumbnail of each.
                val photos = product.allPhotoFiles
                if (photos.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("product_row_photos_${product.id}"),
                    ) {
                        photos.take(ROW_PHOTO_LIMIT).forEach { file ->
                            ProductPhoto(
                                photoFile = file,
                                contentDescription = null,
                                modifier = Modifier.size(ROW_PHOTO_SIZE).clip(RoundedCornerShape(5.dp)),
                                maxSidePx = 96,
                            ) {}
                        }
                        if (photos.size > ROW_PHOTO_LIMIT) {
                            Text(
                                text = "+${photos.size - ROW_PHOTO_LIMIT}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (chipText != null) StatusChip(text = chipText, tone = chipTone)
                if (product.tracksStock) {
                    Text(
                        text = stockText(product.stockUnit, product.totalStock),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
