package com.invision.pos.ui.screens.more

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.invision.pos.R
import com.invision.pos.ui.LocalSuppliersEnabled
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.ShopUiState

/** Where the More tab leads. */
interface MoreActions {
    fun onOpenSuppliers() = Unit
    fun onOpenPurchases() = Unit
    fun onReceiveStock() = Unit
    fun onOpenAlerts() = Unit
    fun onOpenMovements() = Unit
    fun onOpenMenu() = Unit
    fun onOpenReports() = Unit
    fun onOpenHistory() = Unit
    fun onOpenAppearance() = Unit
    fun onOpenBackup() = Unit
    fun onOpenShopSettings() = Unit
}

/** Purchasing, stock records, the restaurant menu, reports, settings and how the app looks. */
@Composable
fun MoreScreen(
    actions: MoreActions,
    shop: ShopUiState = ShopUiState(),
) {
    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.more_title)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .testTag("more_list"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MoreItem(
                R.drawable.ic_settings,
                shop.shopName ?: stringResource(R.string.shop_name_fallback),
                stringResource(R.string.shop_settings_title),
                actions::onOpenShopSettings,
                modifier = Modifier.testTag("more_shop_settings"),
            )

            SectionHeader(stringResource(R.string.more_section_stock), modifier = Modifier.padding(top = Spacing.md))
            if (LocalSuppliersEnabled.current) {
                MoreItem(R.drawable.ic_receive, stringResource(R.string.purchases_receive), stringResource(R.string.more_receive_body), actions::onReceiveStock)
                MoreItem(R.drawable.ic_purchase_order, stringResource(R.string.purchases_title), stringResource(R.string.more_purchases_body), actions::onOpenPurchases)
                MoreItem(R.drawable.ic_supplier, stringResource(R.string.suppliers_title), stringResource(R.string.more_suppliers_body), actions::onOpenSuppliers)
            }
            MoreItem(R.drawable.ic_alert, stringResource(R.string.alerts_title), stringResource(R.string.more_alerts_body), actions::onOpenAlerts)
            MoreItem(R.drawable.ic_history, stringResource(R.string.movements_title), stringResource(R.string.more_movements_body), actions::onOpenMovements)

            if (shop.restaurantMenu) {
                SectionHeader(stringResource(R.string.more_section_restaurant), modifier = Modifier.padding(top = Spacing.md))
                MoreItem(R.drawable.ic_menu_book, stringResource(R.string.menu_title), stringResource(R.string.more_menu_body), actions::onOpenMenu)
            }

            SectionHeader(stringResource(R.string.more_section_records), modifier = Modifier.padding(top = Spacing.md))
            MoreItem(R.drawable.ic_reports, stringResource(R.string.reports_title), stringResource(R.string.more_reports_body), actions::onOpenReports)
            MoreItem(R.drawable.ic_history, stringResource(R.string.billing_history), stringResource(R.string.more_history_body), actions::onOpenHistory)

            SectionHeader(stringResource(R.string.more_section_data), modifier = Modifier.padding(top = Spacing.md))
            MoreItem(R.drawable.ic_backup, stringResource(R.string.more_backup_title), stringResource(R.string.more_backup_body), actions::onOpenBackup)

            SectionHeader(stringResource(R.string.more_section_appearance), modifier = Modifier.padding(top = Spacing.md))
            MoreItem(
                R.drawable.ic_palette,
                stringResource(R.string.appearance_title),
                stringResource(R.string.appearance_row_body),
                actions::onOpenAppearance,
                modifier = Modifier.testTag("more_appearance"),
            )
        }
    }
}

@Composable
private fun MoreItem(@DrawableRes icon: Int, title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    InvisionCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.lg)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
