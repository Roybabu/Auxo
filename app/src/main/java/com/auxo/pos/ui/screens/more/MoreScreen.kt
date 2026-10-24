// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.auxo.pos.R
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.Role
import com.auxo.pos.ui.LocalCapabilities
import com.auxo.pos.ui.can
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.ConfirmDialog
import com.auxo.pos.ui.LocalSuppliersEnabled
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.ShopUiState

/** Where the More tab leads. */
interface MoreActions {
    fun onOpenSuppliers() = Unit
    fun onOpenAlerts() = Unit
    fun onOpenMovements() = Unit
    fun onOpenCount() = Unit
    fun onOpenImport() = Unit
    fun onOpenReports() = Unit
    fun onOpenHistory() = Unit
    fun onOpenAppearance() = Unit
    fun onOpenBackup() = Unit
    fun onOpenShopSettings() = Unit
    fun onOpenPrinter() = Unit
    fun onOpenUsers() = Unit
    fun onOpenRecoveryCode() = Unit
    fun onOpenQuickUnlock() = Unit
    fun onOpenDebug() = Unit
    fun onOpenActivity() = Unit
    fun onOpenDayEnd() = Unit
    fun onOpenGstReport() = Unit
    fun onOpenLoyalty() = Unit
    fun onSignOut() = Unit
}

/** Purchasing, stock records, reports, settings and how the app looks. */
@Composable
fun MoreScreen(
    actions: MoreActions,
    shop: ShopUiState = ShopUiState(),
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.more_title)) },
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

            // Business: how the shop runs.
            if (can(Permission.MANAGE_SETTINGS) || can(Permission.MANAGE_LOYALTY) || can(Permission.MANAGE_USERS)) {
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
                MoreItem(
                    R.drawable.ic_print,
                    stringResource(R.string.printer_title),
                    stringResource(R.string.more_printer_body),
                    actions::onOpenPrinter,
                    modifier = Modifier.testTag("more_printer"),
                )
            }
            if (can(Permission.MANAGE_LOYALTY)) {
                MoreItem(
                    R.drawable.ic_star,
                    stringResource(R.string.loyalty_settings_title),
                    stringResource(R.string.more_loyalty_body),
                    actions::onOpenLoyalty,
                    modifier = Modifier.testTag("more_loyalty"),
                )
            }
            if (can(Permission.MANAGE_USERS)) {
                MoreItem(
                    R.drawable.ic_badge,
                    stringResource(R.string.users_title),
                    stringResource(R.string.more_users_body),
                    actions::onOpenUsers,
                    modifier = Modifier.testTag("more_user_management"),
                )
            }

            // Stock: buying, counting and moving goods.
            val showSuppliers = LocalSuppliersEnabled.current && can(Permission.MANAGE_SUPPLIERS)
            val showStockRecords = can(Permission.VIEW_STOCK_RECORDS)
            val showCount = can(Permission.ADJUST_STOCK)
            val showImport = can(Permission.MANAGE_PRODUCTS)
            if (showSuppliers || showStockRecords || showCount || showImport) {
                SectionHeader(stringResource(R.string.more_section_stock), modifier = Modifier.padding(top = Spacing.md))
                if (showSuppliers) {
                    MoreItem(R.drawable.ic_supplier, stringResource(R.string.suppliers_title), stringResource(R.string.more_suppliers_body), actions::onOpenSuppliers)
                }
                if (showStockRecords) {
                    MoreItem(R.drawable.ic_alert, stringResource(R.string.alerts_title), stringResource(R.string.more_alerts_body), actions::onOpenAlerts)
                    MoreItem(R.drawable.ic_swap, stringResource(R.string.movements_title), stringResource(R.string.more_movements_body), actions::onOpenMovements)
                }
                if (showCount) {
                    MoreItem(R.drawable.ic_fact_check, stringResource(R.string.count_title), stringResource(R.string.more_count_body), actions::onOpenCount, modifier = Modifier.testTag("more_stock_count"))
                }
                if (showImport) {
                    MoreItem(R.drawable.ic_download, stringResource(R.string.import_title), stringResource(R.string.more_import_body), actions::onOpenImport, modifier = Modifier.testTag("more_import_products"))
                }
            }

            // Records: sales, tax, the day's closing and who did what.
            SectionHeader(stringResource(R.string.more_section_records), modifier = Modifier.padding(top = Spacing.md))
            if (can(Permission.VIEW_FINANCIALS)) {
                MoreItem(R.drawable.ic_invoice, stringResource(R.string.billing_history), stringResource(R.string.more_history_body), actions::onOpenHistory)
            } else {
                // An Employee opens past invoices to view, print or share them; there are no payment records or totals.
                MoreItem(
                    R.drawable.ic_invoice,
                    stringResource(R.string.employee_home_invoices),
                    stringResource(R.string.more_invoices_only_body),
                    actions::onOpenHistory,
                    modifier = Modifier.testTag("more_invoices"),
                )
            }
            if (can(Permission.VIEW_REPORTS)) {
                MoreItem(R.drawable.ic_reports, stringResource(R.string.reports_title), stringResource(R.string.more_reports_body), actions::onOpenReports)
            }
            if (can(Permission.GST_REPORT)) {
                MoreItem(
                    R.drawable.ic_percent,
                    stringResource(R.string.gst_title),
                    stringResource(R.string.more_gst_body),
                    actions::onOpenGstReport,
                    modifier = Modifier.testTag("more_gst_report"),
                )
            }
            if (can(Permission.CLOSE_DAY)) {
                MoreItem(
                    R.drawable.ic_day_end,
                    stringResource(R.string.dayend_title),
                    stringResource(R.string.more_dayend_body),
                    actions::onOpenDayEnd,
                    modifier = Modifier.testTag("more_day_end"),
                )
            }
            if (can(Permission.VIEW_ACTIVITY_LOG)) {
                MoreItem(
                    R.drawable.ic_activity,
                    stringResource(R.string.activity_title),
                    stringResource(R.string.more_activity_body),
                    actions::onOpenActivity,
                    modifier = Modifier.testTag("more_activity_log"),
                )
            }

            SectionHeader(stringResource(R.string.more_section_appearance), modifier = Modifier.padding(top = Spacing.md))
            MoreItem(
                R.drawable.ic_palette,
                stringResource(R.string.appearance_title),
                stringResource(R.string.appearance_row_body),
                actions::onOpenAppearance,
                modifier = Modifier.testTag("more_appearance"),
            )
            UpdateCard()

            // Account: sign-in security and the shop's data, then sign out last.
            SectionHeader(stringResource(R.string.more_section_account), modifier = Modifier.padding(top = Spacing.md))
            MoreItem(
                R.drawable.ic_lock,
                stringResource(R.string.quick_title),
                stringResource(R.string.more_quick_body),
                actions::onOpenQuickUnlock,
                modifier = Modifier.testTag("more_quick_unlock"),
            )
            if (can(Permission.MANAGE_USERS)) {
                MoreItem(
                    R.drawable.ic_key,
                    stringResource(R.string.recovery_screen_title),
                    stringResource(R.string.more_recovery_body),
                    actions::onOpenRecoveryCode,
                    modifier = Modifier.testTag("more_recovery_code"),
                )
            }
            if (can(Permission.BACKUP_AND_RESTORE)) {
                MoreItem(R.drawable.ic_backup, stringResource(R.string.more_backup_title), stringResource(R.string.more_backup_body), actions::onOpenBackup)
            }
            run {
                val helpContext = androidx.compose.ui.platform.LocalContext.current
                val helpChooserTitle = stringResource(R.string.crash_prompt_send)
                val helpCopied = stringResource(R.string.crash_github_copied)
                MoreItem(
                    R.drawable.ic_activity,
                    stringResource(R.string.help_report_title),
                    stringResource(R.string.help_report_body),
                    {
                        com.auxo.pos.core.diagnostics.CrashReports.diagnosticShare(helpContext)?.let { send ->
                            runCatching { helpContext.startActivity(android.content.Intent.createChooser(send, helpChooserTitle)) }
                        }
                    },
                    modifier = Modifier.testTag("more_help_report"),
                )
                MoreItem(
                    R.drawable.ic_activity,
                    stringResource(R.string.help_github_title),
                    stringResource(R.string.help_github_body),
                    {
                        runCatching {
                            val open = com.auxo.pos.core.diagnostics.CrashReports.issueIntent(helpContext, com.auxo.pos.core.diagnostics.CrashReports.diagnosticFile(helpContext))
                            android.widget.Toast.makeText(helpContext, helpCopied, android.widget.Toast.LENGTH_LONG).show()
                            helpContext.startActivity(open)
                        }
                    },
                    modifier = Modifier.testTag("more_help_github"),
                )
            }
            if (com.auxo.pos.core.debuglog.DebugLog.isEnabled) {
                MoreItem(
                    R.drawable.ic_warning,
                    stringResource(R.string.debug_page_title),
                    stringResource(R.string.debug_page_body),
                    actions::onOpenDebug,
                    modifier = Modifier.testTag("more_debug"),
                )
            }
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
    AuxoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            AuxoIcon(icon, contentDescription = null, size = AuxoIconSize.Medium)
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.lg)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
