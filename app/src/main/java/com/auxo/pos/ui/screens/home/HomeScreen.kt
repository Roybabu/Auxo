// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.ui.can
import com.auxo.pos.domain.inventory.InventorySummary
import com.auxo.pos.domain.inventory.StockAlert
import com.auxo.pos.domain.inventory.StockAlertType
import com.auxo.pos.domain.inventory.StockFilter
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.ui.LocalStoreLogo
import com.auxo.pos.ui.components.EmptyState
import com.auxo.pos.ui.components.ErrorState
import com.auxo.pos.ui.components.ExpiredStockBanner
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.LoadingState
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.components.StatTile
import com.auxo.pos.ui.components.StatusChip
import com.auxo.pos.ui.format.DateFormats
import com.auxo.pos.ui.format.LocalMoneyFormatter
import com.auxo.pos.ui.navigation.TopLevelDestination
import com.auxo.pos.ui.screens.home.insights.InsightsSection
import com.auxo.pos.ui.screens.inventory.tone
import com.auxo.pos.ui.theme.AuxoTheme
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.domain.insights.Collected
import com.auxo.pos.domain.insights.InsightsPeriod
import com.auxo.pos.viewmodel.HomeInsightsViewModel
import com.auxo.pos.viewmodel.HomeUiState
import com.auxo.pos.viewmodel.HomeViewModel
import com.auxo.pos.viewmodel.InsightsUiState

@Composable
fun HomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    onAddProduct: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenInventory: (StockFilter) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAlerts: () -> Unit = {},
    /** Where the Cash today and UPI today cards lead; the More tab when left null. */
    onOpenBalances: (() -> Unit)? = null,
    onOpenBackup: () -> Unit = {},
    onOpenStoreDetails: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    insightsViewModel: HomeInsightsViewModel = viewModel(factory = HomeInsightsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val insights by insightsViewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        onNavigate = onNavigate,
        onAddProduct = onAddProduct,
        onOpenProduct = onOpenProduct,
        onOpenInventory = onOpenInventory,
        onRetry = viewModel::retry,
        modifier = modifier,
        onOpenAlerts = onOpenAlerts,
        onOpenBalances = onOpenBalances,
        insights = insights,
        onInsightsPeriodChange = insightsViewModel::onPeriodChange,
        onInsightsRetry = insightsViewModel::retry,
        notice = {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(com.auxo.pos.ui.theme.Spacing.sm)) {
                BackupStatusNotice(onOpenBackup)
                FssaiHintNotice(onOpenStoreDetails)
            }
        },
    )
}

/**
 * The Home screen for a given [uiState]. [insights] holds the financial charts shown at the
 * bottom of the white panel; leave it null for a Home without them.
 */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddProduct: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenInventory: (StockFilter) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAlerts: () -> Unit = {},
    /** Where the Cash today and UPI today cards lead; the More tab when left null. */
    onOpenBalances: (() -> Unit)? = null,
    insights: InsightsUiState? = null,
    onInsightsPeriodChange: (InsightsPeriod) -> Unit = {},
    onInsightsRetry: () -> Unit = {},
    notice: @Composable () -> Unit = {},
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
            onOpenAlerts = onOpenAlerts,
            onOpenBalances = onOpenBalances,
            insights = insights,
            onInsightsPeriodChange = onInsightsPeriodChange,
            onInsightsRetry = onInsightsRetry,
            notice = notice,
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
    onOpenAlerts: () -> Unit = {},
    /** Where the Cash today and UPI today cards lead; the More tab when left null. */
    onOpenBalances: (() -> Unit)? = null,
    insights: InsightsUiState? = null,
    onInsightsPeriodChange: (InsightsPeriod) -> Unit = {},
    onInsightsRetry: () -> Unit = {},
    notice: @Composable () -> Unit = {},
) {
    // The header is a dark shade of the accent, so the status bar icons must be light while Home is showing.
    LightStatusBarIcons(darkIcons = false)
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(AuxoThemeExtras.home.header)) {
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val extents = rememberPanelExtents(widthDp = maxWidth.value, heightDp = maxHeight.value, statusBarDp = statusBar.value)
        HomeHeaderBlock(
            storeName = state.storeName,
            hasAlerts = state.inventory.alerts.isNotEmpty(),
            collectedToday = insights?.collectedToday,
            onOpenAlerts = onOpenAlerts,
            onOpenBalances = onOpenBalances ?: { onNavigate(TopLevelDestination.MORE) },
            onNewBill = { onNavigate(TopLevelDestination.BILLING) },
                onAddProduct = onAddProduct,
            onInventory = { onNavigate(TopLevelDestination.INVENTORY) },
            onMore = { onNavigate(TopLevelDestination.MORE) },
        )
        ExpandablePanel(extents = extents) {
            HomePanelContent(
                state = state,
                onNavigate = onNavigate,
                onAddProduct = onAddProduct,
                onOpenProduct = onOpenProduct,
                onOpenInventory = onOpenInventory,
                onOpenAlerts = onOpenAlerts,
                insights = insights,
                onInsightsPeriodChange = onInsightsPeriodChange,
                onInsightsRetry = onInsightsRetry,
                notice = notice,
            )
        }
    }
}

/** Shown on a header card until today's figure has loaded (or if it could not be loaded). */
private const val NOT_LOADED = "–"

@Composable
private fun HomeHeaderBlock(
    storeName: String,
    hasAlerts: Boolean,
    collectedToday: Collected?,
    onOpenAlerts: () -> Unit,
    onOpenBalances: () -> Unit,
    onNewBill: () -> Unit,
    onAddProduct: () -> Unit,
    onInventory: () -> Unit,
    onMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val logo = LocalStoreLogo.current
            if (logo != null) {
                // The shop's own logo in a plain circle: no border, ring or frame. Crop fills the circle
                // without stretching; the white behind it keeps transparent logos readable.
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("home_logo")
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = logo.asImageBitmap(),
                        contentDescription = stringResource(R.string.store_logo_description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AuxoThemeExtras.home.avatar)
                        .testTag("home_icon"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_home),
                        contentDescription = null,
                        tint = AuxoThemeExtras.home.avatarIcon,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable(onClick = onOpenAlerts),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_notifications),
                    contentDescription = stringResource(R.string.home_alerts),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                // Shown only when there is something to look at.
                if (hasAlerts) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 11.dp, end = 12.dp)
                            .size(8.dp)
                            .background(AuxoThemeExtras.home.badge, CircleShape),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        val money = LocalMoneyFormatter.current
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BalanceCard(
                label = stringResource(R.string.home_cash_today),
                value = collectedToday?.cash?.let { money.format(it) } ?: NOT_LOADED,
                onClick = onOpenBalances,
                modifier = Modifier.weight(1f),
            )
            BalanceCard(
                label = stringResource(R.string.home_upi_today),
                value = collectedToday?.upi?.let { money.format(it) } ?: NOT_LOADED,
                onClick = onOpenBalances,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))
        val canManageProducts = can(Permission.MANAGE_PRODUCTS)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            QuickAction(stringResource(R.string.home_action_new_bill), R.drawable.ic_nav_billing, onNewBill, "home_action_new_bill")
            if (canManageProducts) {
                QuickAction(stringResource(R.string.home_action_add_product), R.drawable.ic_add, onAddProduct, "home_action_add_product")
                QuickAction(stringResource(R.string.nav_inventory), R.drawable.ic_nav_inventory, onInventory, "home_action_inventory")
            }
            QuickAction(stringResource(R.string.nav_more), R.drawable.ic_apps_outline, onMore, "home_action_more")
        }
    }
}

@Composable
private fun BalanceCard(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = AuxoThemeExtras.home.card,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(180f),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun RowScope.QuickAction(label: String, iconRes: Int, onClick: () -> Unit, tag: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .testTag(tag)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(AuxoThemeExtras.home.actionCircle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = AuxoThemeExtras.home.actionIcon,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** What was on the Home dashboard before the redesign, kept inside the white panel. */
@Composable
private fun HomePanelContent(
    state: HomeUiState.Ready,
    onNavigate: (TopLevelDestination) -> Unit,
    onAddProduct: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenInventory: (StockFilter) -> Unit,
    onOpenAlerts: () -> Unit,
    insights: InsightsUiState?,
    onInsightsPeriodChange: (InsightsPeriod) -> Unit,
    onInsightsRetry: () -> Unit,
    notice: @Composable () -> Unit,
) {
    val status = AuxoThemeExtras.statusColors
    val inventory = state.inventory
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_panel_content")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        notice()

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
                AuxoCard {
                    EmptyState(
                        iconRes = R.drawable.ic_category,
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
                        iconRes = R.drawable.ic_category,
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
                    iconRes = R.drawable.ic_expiry,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenInventory(StockFilter.EXPIRING_SOON) },
                )
                if (inventory.expiredCount > 0) {
                    ExpiredStockBanner(
                        text = pluralStringResource(R.plurals.home_expired_banner, inventory.expiredCount, inventory.expiredCount),
                        modifier = Modifier.testTag("home_expired_banner"),
                        onClick = { onOpenInventory(StockFilter.EXPIRING_SOON) },
                    )
                }
                AlertsCard(alerts = inventory.alerts, onOpenProduct = onOpenProduct, onOpenAlerts = onOpenAlerts)
            }
        }

        // Financial charts, below everything that was already here so the collapsed panel looks the same.
        if (insights != null) {
            InsightsSection(
                state = insights,
                inventory = inventory,
                onPeriodChange = onInsightsPeriodChange,
                onRetry = onInsightsRetry,
            )
        }
    }
}

@Composable
private fun AlertsCard(alerts: List<StockAlert>, onOpenProduct: (Long) -> Unit, onOpenAlerts: () -> Unit) {
    AuxoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm)) {
                Text(
                    text = stringResource(R.string.home_alerts),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = Spacing.sm),
                )
                TextButton(onClick = onOpenAlerts) { Text(stringResource(R.string.home_alerts_open)) }
            }
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
        StockAlertType.LOW_STOCK -> if (alert.stockUnit == StockUnit.UNIT) {
            pluralStringResource(R.plurals.alert_low_stock, alert.stockQuantity, alert.stockQuantity)
        } else {
            stringResource(R.string.alert_low_stock_amount, alert.stockUnit.format(alert.stockQuantity))
        }
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

/** Switches the status bar icons between dark and light while the calling screen is showing. */
@Composable
private fun LightStatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, darkIcons) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = darkIcons
        onDispose {
            if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, widthDp = 400, heightDp = 1200)
@Composable
private fun HomeDashboardPreview() {
    AuxoTheme {
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
