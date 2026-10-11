package com.invision.pos.ui.screens.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.invision.pos.core.appearance.AppAccent
import com.invision.pos.core.appearance.AppearanceSettings
import com.invision.pos.ui.theme.AccentPalette
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
    fun onOpenBillingSettings() = Unit
    fun onOpenBackup() = Unit
    fun onOpenShopSettings() = Unit
}

/** Purchasing, stock records, the restaurant menu, reports, settings and how the app looks. */
@Composable
fun MoreScreen(
    actions: MoreActions,
    appearance: AppearanceSettings = AppearanceSettings(),
    shop: ShopUiState = ShopUiState(),
    onAccentChange: (AppAccent) -> Unit = {},
    onDarkModeChange: (Boolean) -> Unit = {},
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
            MoreItem(R.drawable.ic_settings, stringResource(R.string.billing_settings), stringResource(R.string.more_billing_settings_body), actions::onOpenBillingSettings)

            SectionHeader(stringResource(R.string.more_section_data), modifier = Modifier.padding(top = Spacing.md))
            MoreItem(R.drawable.ic_backup, stringResource(R.string.more_backup_title), stringResource(R.string.more_backup_body), actions::onOpenBackup)

            SectionHeader(stringResource(R.string.more_section_appearance), modifier = Modifier.padding(top = Spacing.md))
            AppearanceCard(appearance, onAccentChange, onDarkModeChange)
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

/** Dark mode switch and the eight app colours. */
@Composable
private fun AppearanceCard(
    appearance: AppearanceSettings,
    onAccentChange: (AppAccent) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
) {
    InvisionCard(modifier = Modifier.fillMaxWidth().testTag("appearance")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance_dark_mode), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.appearance_dark_mode_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = appearance.darkMode,
                    onCheckedChange = onDarkModeChange,
                    modifier = Modifier.testTag("dark_mode_switch"),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Column {
                    Text(stringResource(R.string.appearance_colour), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.appearance_colour_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppAccent.entries.chunked(4).forEach { rowAccents ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        rowAccents.forEach { accent ->
                            AccentChoice(
                                accent = accent,
                                selected = accent == appearance.accent,
                                onClick = { onAccentChange(accent) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@StringRes
private fun AppAccent.nameRes(): Int = when (this) {
    AppAccent.EMERALD -> R.string.accent_emerald
    AppAccent.TEAL -> R.string.accent_teal
    AppAccent.OCEAN -> R.string.accent_ocean
    AppAccent.VIOLET -> R.string.accent_violet
    AppAccent.ORCHID -> R.string.accent_orchid
    AppAccent.ROSE -> R.string.accent_rose
    AppAccent.SUNSET -> R.string.accent_sunset
    AppAccent.SLATE -> R.string.accent_slate
}

/** One colour on the picker: a circle with a tick when chosen, and its name underneath. */
@Composable
private fun AccentChoice(accent: AppAccent, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(accent.nameRes())
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag("accent_${accent.id}")
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AccentPalette.swatch(accent))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
