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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.invision.pos.R
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.Role
import com.invision.pos.ui.LocalCapabilities
import com.invision.pos.ui.can
import com.invision.pos.ui.components.ConfirmDialog
import com.invision.pos.ui.LocalSuppliersEnabled
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.components.SectionHeader
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.ShopUiState

/** Where the More tab leads. */
interface MoreActions {
    fun onOpenSuppliers() = Unit
    fun onOpenAlerts() = Unit
    fun onOpenMovements() = Unit
    fun onOpenMenu() = Unit
    fun onOpenReports() = Unit
    fun onOpenHistory() = Unit
    fun onOpenAppearance() = Unit
    fun onOpenBackup() = Unit
    fun onOpenShopSettings() = Unit
    fun onOpenUsers() = Unit
    fun onOpenRecoveryCode() = Unit
    fun onOpenQuickUnlock() = Unit
    fun onOpenActivity() = Unit
    fun onOpenDayEnd() = Unit
    fun onOpenGstReport() = Unit
    fun onSignOut() = Unit
}

/** Purchasing, stock records, the restaurant menu, reports, settings and how the app looks. */
@Composable
fun MoreScreen(
    actions: MoreActions,
    shop: ShopUiState = ShopUiState(),
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
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
            val capabilities = LocalCapabilities.current
            Text(
                stringResource(R.string.more_signed_in_as, capabilities.userName, stringResource(capabilities.role.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("more_signed_in"),
            )

            if (can(Permission.MANAGE_SETTINGS) || can(Permission.MANAGE_USERS) || can(Permission.VIEW_ACTIVITY_LOG) || can(Permission.CLOSE_DAY) || can(Permission.GST_REPORT)) {
                SectionHeader(stringResource(R.string.more_section_business), modifier = Modifier.padding(top = Spacing.sm))
            }
            if (can(Permission.MANAGE_SETTINGS)) {
                MoreItem(
                    R.drawable.ic_settings,
                    shop.shopName ?: stringResource(R.string.shop_name_fallback),
                    stringResource(R.string.shop_settings_title),
                    actions::onOpenShopSettings,
                    modifier = Modifier.testTag("more_shop_settings"),
                )
            }
            if (can(Permission.GST_REPORT)) {
                MoreItem(
                    R.drawable.ic_reports,
                    stringResource(R.string.gst_title),
                    stringResource(R.string.more_gst_body),
                    actions::onOpenGstReport,
                    modifier = Modifier.testTag("more_gst_report"),
                )
            }
            if (can(Permission.CLOSE_DAY)) {
                MoreItem(
                    R.drawable.ic_wallet,
                    stringResource(R.string.dayend_title),
                    stringResource(R.string.more_dayend_body),
                    actions::onOpenDayEnd,
                    modifier = Modifier.testTag("more_day_end"),
                )
            }
            if (can(Permission.MANAGE_USERS)) {
                MoreItem(
                    R.drawable.ic_person,
                    stringResource(R.string.users_title),
                    stringResource(R.string.more_users_body),
                    actions::onOpenUsers,
                    modifier = Modifier.testTag("more_user_management"),
                )
            }
            if (can(Permission.MANAGE_USERS)) {
                MoreItem(
                    R.drawable.ic_person,
                    stringResource(R.string.recovery_screen_title),
                    stringResource(R.string.more_recovery_body),
                    actions::onOpenRecoveryCode,
                    modifier = Modifier.testTag("more_recovery_code"),
                )
            }
            if (can(Permission.VIEW_ACTIVITY_LOG)) {
                MoreItem(
                    R.drawable.ic_history,
                    stringResource(R.string.activity_title),
                    stringResource(R.string.more_activity_body),
                    actions::onOpenActivity,
                    modifier = Modifier.testTag("more_activity_log"),
                )
            }

            val showSuppliers = LocalSuppliersEnabled.current && can(Permission.MANAGE_SUPPLIERS)
            val showStockRecords = can(Permission.VIEW_STOCK_RECORDS)
            if (showSuppliers || showStockRecords) {
                SectionHeader(stringResource(R.string.more_section_stock), modifier = Modifier.padding(top = Spacing.md))
                if (showSuppliers) {
                    MoreItem(R.drawable.ic_supplier, stringResource(R.string.suppliers_title), stringResource(R.string.more_suppliers_body), actions::onOpenSuppliers)
                }
                if (showStockRecords) {
                    MoreItem(R.drawable.ic_alert, stringResource(R.string.alerts_title), stringResource(R.string.more_alerts_body), actions::onOpenAlerts)
                    MoreItem(R.drawable.ic_history, stringResource(R.string.movements_title), stringResource(R.string.more_movements_body), actions::onOpenMovements)
                }
            }

            if (shop.restaurantMenu && can(Permission.MANAGE_MENU)) {
                SectionHeader(stringResource(R.string.more_section_restaurant), modifier = Modifier.padding(top = Spacing.md))
                MoreItem(R.drawable.ic_menu_book, stringResource(R.string.menu_title), stringResource(R.string.more_menu_body), actions::onOpenMenu)
            }

            SectionHeader(stringResource(R.string.more_section_records), modifier = Modifier.padding(top = Spacing.md))
            if (can(Permission.VIEW_REPORTS)) {
                MoreItem(R.drawable.ic_reports, stringResource(R.string.reports_title), stringResource(R.string.more_reports_body), actions::onOpenReports)
            }
            if (can(Permission.VIEW_FINANCIALS)) {
                MoreItem(R.drawable.ic_history, stringResource(R.string.billing_history), stringResource(R.string.more_history_body), actions::onOpenHistory)
            } else {
                // An Employee opens past invoices to view, print or share them; there are no payment records or totals.
                MoreItem(
                    R.drawable.ic_history,
                    stringResource(R.string.employee_home_invoices),
                    stringResource(R.string.more_invoices_only_body),
                    actions::onOpenHistory,
                    modifier = Modifier.testTag("more_invoices"),
                )
            }

            if (can(Permission.BACKUP_AND_RESTORE)) {
                SectionHeader(stringResource(R.string.more_section_data), modifier = Modifier.padding(top = Spacing.md))
                MoreItem(R.drawable.ic_backup, stringResource(R.string.more_backup_title), stringResource(R.string.more_backup_body), actions::onOpenBackup)
            }

            SectionHeader(stringResource(R.string.more_section_appearance), modifier = Modifier.padding(top = Spacing.md))
            MoreItem(
                R.drawable.ic_palette,
                stringResource(R.string.appearance_title),
                stringResource(R.string.appearance_row_body),
                actions::onOpenAppearance,
                modifier = Modifier.testTag("more_appearance"),
            )

            SectionHeader(stringResource(R.string.more_section_account), modifier = Modifier.padding(top = Spacing.md))
            MoreItem(
                R.drawable.ic_person,
                stringResource(R.string.quick_title),
                stringResource(R.string.more_quick_body),
                actions::onOpenQuickUnlock,
                modifier = Modifier.testTag("more_quick_unlock"),
            )
            MoreItem(
                R.drawable.ic_logout,
                stringResource(R.string.sign_out_title),
                stringResource(R.string.sign_out_body),
                { confirmSignOut = true },
                modifier = Modifier.testTag("more_sign_out"),
            )
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.sign_out_title),
            message = stringResource(R.string.sign_out_confirm),
            confirmLabel = stringResource(R.string.sign_out_title),
            onConfirm = {
                confirmSignOut = false
                actions.onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

fun Role.labelRes(): Int = when (this) {
    Role.SUPER_ADMIN -> R.string.role_super_admin
    Role.EMPLOYEE -> R.string.role_employee
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
