package com.invision.pos.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.inventory.InventorySummary
import com.invision.pos.domain.inventory.StockAlert
import com.invision.pos.domain.inventory.StockAlertType
import com.invision.pos.domain.inventory.StockFilter
import com.invision.pos.domain.model.Money
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.ErrorState
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.LoadingState
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.components.StatTile
import com.invision.pos.ui.components.StatusChip
import com.invision.pos.ui.format.DateFormats
import com.invision.pos.ui.navigation.TopLevelDestination
import com.invision.pos.ui.screens.inventory.tone
import com.invision.pos.ui.theme.InvisionTheme
import com.invision.pos.ui.theme.InvisionThemeExtras
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.HomeUiState
import com.invision.pos.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddProduct: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenInventory: (StockFilter) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        onNavigate = onNavigate,
        onAddProduct = onAddProduct,
        onOpenProduct = onOpenProduct,
        onOpenInventory = onOpenInventory,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddProduct: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenInventory: (StockFilter) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> LoadingState(modifier)
        HomeUiState.Failed -> ErrorState(
            message = stringResource(R.string.error_generic),
            onRetry = onRetry,
            modifier = modifier,
        )
        is HomeUiState.Ready -> HomeDashboard(
            state = uiState,
            onNavigate = onNavigate,
            onAddProduct = onAddProduct,
            onOpenProduct = onOpenProduct,
            onOpenInventory = onOpenInventory,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeDashboard(
    state: HomeUiState.Ready,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddProduct: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenInventory: (StockFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = InvisionThemeExtras.statusColors
    val inventory = state.inventory
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = state.storeName, style = MaterialTheme.typography.headlineMedium)
        }

        // Today
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionHeader(title = stringResource(R.string.home_today))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = stringResource(R.string.home_sales_today),
                    value = state.salesToday,
                    tone = null,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.home_bills_today),
                    value = state.billsToday.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Inventory
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionHeader(
                title = stringResource(R.string.home_inventory),
                action = {
                    TextButton(onClick = { onNavigate(TopLevelDestination.INVENTORY) }) {
                        Text(stringResource(R.string.home_view_inventory))
                    }
                },
            )
            if (!inventory.hasProducts) {
                InvisionCard {
                    EmptyState(
                        iconRes = R.drawable.ic_nav_inventory,
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_body),
                        actionLabel = stringResource(R.string.home_action_add_product),
                        onAction = onAddProduct,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        label = stringResource(R.string.home_products),
                        value = inventory.productCount.toString(),
                        iconRes = R.drawable.ic_nav_inventory,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenInventory(StockFilter.ALL) },
                    )
                    StatTile(
                        label = stringResource(R.string.home_units),
                        value = inventory.unitsInStock.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
                StatTile(
                    label = stringResource(R.string.home_stock_value),
                    value = state.stockValueAtCost,
                    modifier = Modifier.fillMaxWidth(),
                )
                StatTile(
                    label = stringResource(R.string.home_retail_value),
                    value = state.stockValueAtPrice,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(
                        label = stringResource(R.string.home_low_stock),
                        value = inventory.lowStockCount.toString(),
                        tone = status.warning,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenInventory(StockFilter.LOW_STOCK) },
                    )
                    StatTile(
                        label = stringResource(R.string.home_out_of_stock),
                        value = inventory.outOfStockCount.toString(),
                        tone = status.danger,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenInventory(StockFilter.OUT_OF_STOCK) },
                    )
                }
                StatTile(
                    label = stringResource(R.string.home_expiring),
                    value = (inventory.expiringSoonCount + inventory.expiredCount).toString(),
                    tone = status.warning,
                    iconRes = R.drawable.ic_warning,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenInventory(StockFilter.EXPIRING_SOON) },
                )
                AlertsCard(alerts = inventory.alerts, onOpenProduct = onOpenProduct)
            }
        }

        // Quick actions
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionHeader(title = stringResource(R.string.home_quick_actions))
            QuickAction(
                label = stringResource(R.string.home_action_new_bill),
                iconRes = R.drawable.ic_nav_billing,
                onClick = { onNavigate(TopLevelDestination.BILLING) },
            )
            QuickAction(
                label = stringResource(R.string.home_action_add_product),
                iconRes = R.drawable.ic_add,
                onClick = onAddProduct,
            )
            QuickAction(
                label = stringResource(R.string.home_action_add_customer),
                iconRes = R.drawable.ic_nav_customers,
                onClick = { onNavigate(TopLevelDestination.CUSTOMERS) },
            )
        }
    }
}

@Composable
private fun AlertsCard(alerts: List<StockAlert>, onOpenProduct: (Long) -> Unit) {
    InvisionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Text(
                text = stringResource(R.string.home_alerts),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            if (alerts.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_no_alerts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
            alerts.forEachIndexed { index, alert ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AlertRow(alert = alert, onClick = { onOpenProduct(alert.productId) })
            }
        }
    }
}

@Composable
private fun AlertRow(alert: StockAlert, onClick: () -> Unit) {
    val description = when (alert.type) {
        StockAlertType.EXPIRED -> stringResource(R.string.alert_expired, alert.expiryDate?.let(DateFormats::date).orEmpty())
        StockAlertType.EXPIRING_SOON -> stringResource(R.string.alert_expiring, alert.expiryDate?.let(DateFormats::date).orEmpty())
        StockAlertType.OUT_OF_STOCK -> stringResource(R.string.alert_out_of_stock)
        StockAlertType.LOW_STOCK -> pluralStringResource(R.plurals.alert_low_stock, alert.stockQuantity, alert.stockQuantity)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = alert.productName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (alert.variantLabel.isNotEmpty()) {
                Text(
                    text = alert.variantLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        StatusChip(text = description, tone = alert.type.tone())
    }
}

@Composable
private fun QuickAction(label: String, iconRes: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text = label, modifier = Modifier.padding(start = Spacing.sm))
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 1200)
@Composable
private fun HomeDashboardPreview() {
    InvisionTheme {
        HomeContent(
            uiState = HomeUiState.Ready(
                storeName = "My Store",
                salesToday = "₹0.00",
                billsToday = 0,
                inventory = InventorySummary.EMPTY.copy(
                    productCount = 12,
                    variantCount = 40,
                    unitsInStock = 310,
                    stockValueAtCost = Money(4_500_000),
                    lowStockCount = 3,
                    outOfStockCount = 1,
                    expiringSoonCount = 2,
                ),
                stockValueAtCost = "₹45,000.00",
                stockValueAtPrice = "₹72,300.00",
            ),
            onNavigate = {},
            onAddProduct = {},
            onOpenProduct = {},
            onOpenInventory = {},
            onRetry = {},
        )
    }
}
