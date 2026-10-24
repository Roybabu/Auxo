// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.auxo.pos.AuxoApplication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.auxo.pos.domain.inventory.StockFilter
import com.auxo.pos.ui.screens.billing.BillingScreen
import com.auxo.pos.ui.screens.billing.InvoiceDetailScreen
import com.auxo.pos.ui.screens.billing.InvoiceHistoryScreen
import com.auxo.pos.ui.screens.billing.ReturnScreen
import com.auxo.pos.ui.screens.home.HomeScreen
import com.auxo.pos.ui.screens.inventory.InventoryListScreen
import com.auxo.pos.ui.screens.inventory.LabelPrintScreen
import com.auxo.pos.ui.screens.inventory.ProductDetailScreen
import com.auxo.pos.ui.screens.inventory.ProductEditorScreen
import com.auxo.pos.ui.screens.inventory.StockPage
import com.auxo.pos.ui.screens.customers.AccountPaymentScreen
import com.auxo.pos.ui.screens.customers.CustomerDetailScreen
import com.auxo.pos.ui.screens.customers.CustomerHistoryScreen
import com.auxo.pos.ui.screens.customers.CustomerEditorScreen
import com.auxo.pos.ui.screens.customers.CustomerListScreen
import com.auxo.pos.ui.screens.more.ActivityLogScreen
import com.auxo.pos.ui.screens.reports.GstReportScreen
import com.auxo.pos.ui.screens.more.DayClosingScreen
import com.auxo.pos.ui.screens.more.DayEndScreen
import com.auxo.pos.ui.screens.more.BackupScreen
import com.auxo.pos.ui.screens.more.MoreActions
import com.auxo.pos.ui.screens.more.AppearanceScreen
import com.auxo.pos.ui.screens.more.ShopSettingsScreen
import com.auxo.pos.ui.screens.more.StoreDetailsScreen
import com.auxo.pos.ui.screens.more.LoyaltySettingsScreen
import com.auxo.pos.ui.screens.more.QuickUnlockScreen
import com.auxo.pos.ui.screens.more.UserManagementScreen
import com.auxo.pos.ui.screens.auth.RecoveryCodeScreen
import com.auxo.pos.viewmodel.ShopViewModel
import com.auxo.pos.ui.screens.more.MoreScreen
import com.auxo.pos.ui.screens.purchasing.PurchaseListScreen
import com.auxo.pos.ui.screens.purchasing.PurchaseOrderDetailScreen
import com.auxo.pos.ui.screens.purchasing.PurchaseOrderEditorScreen
import com.auxo.pos.ui.screens.purchasing.ReceiptDetailScreen
import com.auxo.pos.ui.screens.purchasing.ReceiveGoodsScreen
import com.auxo.pos.ui.screens.purchasing.SupplierEditorScreen
import com.auxo.pos.ui.screens.purchasing.SupplierListScreen
import com.auxo.pos.ui.screens.reports.ReportsScreen
import com.auxo.pos.ui.screens.stock.StockAlertsScreen
import com.auxo.pos.ui.screens.stock.ProductImportScreen
import com.auxo.pos.ui.screens.stock.StockCountScreen
import com.auxo.pos.ui.screens.stock.StockMovementsScreen
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.ui.RequirePermission
import com.auxo.pos.ui.can
import com.auxo.pos.ui.screens.home.EmployeeHomeScreen
import com.auxo.pos.viewmodel.InvoiceNotice
import com.auxo.pos.viewmodel.PurchaseTab

@Composable
fun AuxoNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val authRepository = (LocalContext.current.applicationContext as? AuxoApplication)?.container?.authRepository
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier,
        // No animation at all: Navigation's default is a 700 ms cross-fade, and even a short one keeps the old screen drawn
        // while the new one is built. With none, the old screen is gone the moment the new one is ready.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
        // The back gesture on Android 14+ plays its own shrink-and-settle preview; none of it either.
        predictivePopEnterTransition = { EnterTransition.None },
        predictivePopExitTransition = { ExitTransition.None },
    ) {
        guarded(navController, TopLevelDestination.HOME.route) {
            if (can(Permission.VIEW_DASHBOARD)) {
                val canCloseDay = can(Permission.CLOSE_DAY)
                HomeScreen(
                    onNavigate = navController::navigateToTopLevel,
                    onAddProduct = { navController.navigateOnce(InventoryRoutes.newProduct()) },
                    onOpenProduct = { navController.navigateOnce(InventoryRoutes.product(it)) },
                    onOpenInventory = { filter -> navController.navigateToInventory(filter) },
                    onOpenAlerts = { navController.navigateOnce(StockRoutes.alerts()) },
                    // Cash today and UPI today lead to the day's figures: Day end when allowed, else the invoices.
                    onOpenBalances = { navController.navigateOnce(if (canCloseDay) MoreRoutes.DAY_END else BillingRoutes.HISTORY) },
                    onOpenBackup = { navController.navigateOnce(MoreRoutes.BACKUP) },
                    onOpenStoreDetails = { navController.navigateOnce(MoreRoutes.STORE_DETAILS) },
                )
            } else {
                EmployeeHomeScreen(
                    onNavigate = navController::navigateToTopLevel,
                    onOpenInvoices = { navController.navigateOnce(BillingRoutes.HISTORY) },
                )
            }
        }
        navigation(
            startDestination = BillingRoutes.POS,
            route = TopLevelDestination.BILLING.route,
        ) {
            guarded(navController, BillingRoutes.POS) {
                BillingScreen(
                    onSaleCompleted = { invoiceId, changeMinor ->
                        navController.navigateOnce(BillingRoutes.savedInvoice(invoiceId, InvoiceNotice.SALE_SAVED, changeMinor))
                    },
                    onOpenHistory = { navController.navigateOnce(BillingRoutes.HISTORY) },
                    onOpenSettings = { navController.navigateOnce(MoreRoutes.STORE_DETAILS) },
                    onAddProduct = { barcode -> navController.navigateOnce(InventoryRoutes.newProduct(barcode)) },
                )
            }
            guarded(navController,
                route = BillingRoutes.INVOICE,
                arguments = listOf(
                    navArgument(BillingRoutes.ARG_INVOICE_ID) { type = NavType.LongType },
                    navArgument(BillingRoutes.ARG_NOTICE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(BillingRoutes.ARG_CHANGE) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) {
                InvoiceDetailScreen(
                    onBack = { navController.popOnce() },
                    onReturn = { navController.navigateOnce(BillingRoutes.returnFor(it)) },
                    onOpenInvoice = { navController.navigateOnce(BillingRoutes.invoice(it)) },
                    onNewBill = {
                        navController.navigate(BillingRoutes.POS) {
                            popUpTo(BillingRoutes.POS) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            guarded(navController, BillingRoutes.HISTORY) {
                InvoiceHistoryScreen(
                    onBack = { navController.popOnce() },
                    onOpenInvoice = { navController.navigateOnce(BillingRoutes.invoice(it)) },
                )
            }
            guarded(navController,
                route = BillingRoutes.RETURN,
                arguments = listOf(navArgument(BillingRoutes.ARG_INVOICE_ID) { type = NavType.LongType }),
            ) {
                ReturnScreen(
                    onBack = { navController.popOnce() },
                    onCompleted = { creditNoteId ->
                        navController.navigate(BillingRoutes.savedInvoice(creditNoteId, InvoiceNotice.RETURN_SAVED)) {
                            popUpTo(BillingRoutes.RETURN) { inclusive = true }
                        }
                    },
                )
            }
        }
        navigation(
            startDestination = InventoryRoutes.LIST,
            route = TopLevelDestination.INVENTORY.route,
        ) {
            guarded(navController,
                route = InventoryRoutes.LIST,
                arguments = listOf(
                    navArgument(InventoryRoutes.ARG_STOCK_FILTER) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                InventoryListScreen(
                    onOpenProduct = { navController.navigateOnce(InventoryRoutes.product(it)) },
                    onAddProduct = { barcode -> navController.navigateOnce(InventoryRoutes.newProduct(barcode)) },
                    onOpenStockPage = { page ->
                        navController.navigate(
                            when (page) {
                                StockPage.ALERTS -> StockRoutes.alerts()
                                StockPage.RECEIVE -> StockRoutes.receive()
                                StockPage.PURCHASES -> StockRoutes.purchases()
                                StockPage.SUPPLIERS -> StockRoutes.suppliers()
                                StockPage.MOVEMENTS -> StockRoutes.movements()
                            },
                        )
                    },
                )
            }
            guarded(navController,
                route = InventoryRoutes.PRODUCT,
                arguments = listOf(navArgument(InventoryRoutes.ARG_PRODUCT_ID) { type = NavType.LongType }),
            ) {
                ProductDetailScreen(
                    onBack = { navController.popOnce() },
                    onEdit = { navController.navigateOnce(InventoryRoutes.editProduct(it)) },
                    onPrintLabels = { navController.navigateOnce(InventoryRoutes.labels(it)) },
                )
            }
            guarded(navController,
                route = InventoryRoutes.EDITOR,
                arguments = listOf(
                    navArgument(InventoryRoutes.ARG_PRODUCT_ID) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument(InventoryRoutes.ARG_BARCODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val editingId = entry.arguments?.getLong(InventoryRoutes.ARG_PRODUCT_ID) ?: 0L
                ProductEditorScreen(
                    onBack = { navController.popOnce() },
                    onSaved = { savedId ->
                        if (editingId != 0L) {
                            navController.popOnce()
                        } else {
                            // Replace the editor with the new product's page.
                            navController.navigate(InventoryRoutes.product(savedId)) {
                                popUpTo(InventoryRoutes.EDITOR) { inclusive = true }
                            }
                        }
                    },
                )
            }
            guarded(navController,
                route = InventoryRoutes.LABELS,
                arguments = listOf(navArgument(InventoryRoutes.ARG_PRODUCT_ID) { type = NavType.LongType }),
            ) {
                LabelPrintScreen(onBack = { navController.popOnce() })
            }
            stockGraph(navController)
        }
        navigation(
            startDestination = CustomerRoutes.LIST,
            route = TopLevelDestination.CUSTOMERS.route,
        ) {
            guarded(navController, CustomerRoutes.LIST) {
                CustomerListScreen(
                    onOpenCustomer = { navController.navigateOnce(CustomerRoutes.detail(it)) },
                    onAddCustomer = { navController.navigateOnce(CustomerRoutes.newCustomer()) },
                )
            }
            guarded(navController,
                route = CustomerRoutes.DETAIL,
                arguments = listOf(navArgument(CustomerRoutes.ARG_CUSTOMER_ID) { type = NavType.LongType }),
            ) {
                CustomerDetailScreen(
                    onBack = { navController.popOnce() },
                    onEdit = { navController.navigateOnce(CustomerRoutes.editCustomer(it)) },
                    onReceivePayment = { navController.navigateOnce(CustomerRoutes.payment(it)) },
                    onOpenHistory = { navController.navigateOnce(CustomerRoutes.history(it)) },
                    onOpenInvoice = { navController.navigateOnce(BillingRoutes.invoice(it)) },
                )
            }
            guarded(navController,
                route = CustomerRoutes.HISTORY,
                arguments = listOf(navArgument(CustomerRoutes.ARG_CUSTOMER_ID) { type = NavType.LongType }),
            ) {
                CustomerHistoryScreen(
                    onBack = { navController.popOnce() },
                    onOpenInvoice = { navController.navigateOnce(BillingRoutes.invoice(it)) },
                )
            }
            guarded(navController,
                route = CustomerRoutes.EDITOR,
                arguments = listOf(
                    navArgument(CustomerRoutes.ARG_CUSTOMER_ID) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                val editingId = entry.arguments?.getLong(CustomerRoutes.ARG_CUSTOMER_ID) ?: 0L
                CustomerEditorScreen(
                    onBack = { navController.popOnce() },
                    onSaved = { savedId ->
                        if (editingId != 0L) {
                            navController.popOnce()
                        } else {
                            navController.navigate(CustomerRoutes.detail(savedId)) {
                                popUpTo(CustomerRoutes.EDITOR) { inclusive = true }
                            }
                        }
                    },
                )
            }
            guarded(navController,
                route = CustomerRoutes.PAYMENT,
                arguments = listOf(navArgument(CustomerRoutes.ARG_CUSTOMER_ID) { type = NavType.LongType }),
            ) {
                AccountPaymentScreen(
                    onBack = { navController.popOnce() },
                    onSaved = { navController.popOnce() },
                )
            }
        }
        navigation(
            startDestination = MoreRoutes.HOME,
            route = TopLevelDestination.MORE.route,
        ) {
            guarded(navController, MoreRoutes.HOME) {
                val shopViewModel: ShopViewModel = viewModel(factory = ShopViewModel.Factory)
                val shop by shopViewModel.uiState.collectAsStateWithLifecycle()
                MoreScreen(
                    shop = shop,
                    actions = object : MoreActions {
                        override fun onOpenSuppliers() = navController.navigateOnce(StockRoutes.suppliers())
                        override fun onOpenAlerts() = navController.navigateOnce(StockRoutes.alerts())
                        override fun onOpenMovements() = navController.navigateOnce(StockRoutes.movements())
                        override fun onOpenCount() = navController.navigateOnce(StockRoutes.count())
                        override fun onOpenImport() = navController.navigateOnce(StockRoutes.import())
                        override fun onOpenReports() = navController.navigateOnce(MoreRoutes.REPORTS)
                        override fun onOpenHistory() = navController.navigateOnce(BillingRoutes.HISTORY)
                        override fun onOpenAppearance() = navController.navigateOnce(MoreRoutes.APPEARANCE)
                        override fun onOpenBackup() = navController.navigateOnce(MoreRoutes.BACKUP)
                        override fun onOpenShopSettings() = navController.navigateOnce(MoreRoutes.SHOP_SETTINGS)
                        override fun onOpenPrinter() = navController.navigateOnce(MoreRoutes.PRINTER)
                        override fun onOpenUsers() = navController.navigateOnce(MoreRoutes.USER_MANAGEMENT)
                        override fun onOpenRecoveryCode() = navController.navigateOnce(MoreRoutes.RECOVERY_CODE)
                        override fun onOpenQuickUnlock() = navController.navigateOnce(MoreRoutes.QUICK_UNLOCK)
                        override fun onOpenActivity() = navController.navigateOnce(MoreRoutes.ACTIVITY_LOG)
                        override fun onOpenDayEnd() = navController.navigateOnce(MoreRoutes.DAY_END)
                        override fun onOpenGstReport() = navController.navigateOnce(MoreRoutes.GST_REPORT)
                        override fun onOpenLoyalty() = navController.navigateOnce(MoreRoutes.LOYALTY)
                        override fun onSignOut() = authRepository?.signOut() ?: Unit
                    },
                )
            }
            guarded(navController, MoreRoutes.USER_MANAGEMENT) {
                UserManagementScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.RECOVERY_CODE) {
                RecoveryCodeScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.ACTIVITY_LOG) {
                ActivityLogScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.GST_REPORT) {
                GstReportScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.LOYALTY) {
                LoyaltySettingsScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.DAY_END) {
                DayEndScreen(
                    onBack = { navController.popOnce() },
                    onOpenClosing = { navController.navigateOnce(MoreRoutes.dayClosing(it)) },
                )
            }
            guarded(
                navController,
                route = MoreRoutes.DAY_CLOSING,
                arguments = listOf(navArgument(MoreRoutes.ARG_CLOSING_ID) { type = NavType.LongType }),
            ) {
                DayClosingScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.SHOP_SETTINGS) {
                ShopSettingsScreen(
                    onBack = { navController.popOnce() },
                    onOpenStoreDetails = { navController.navigateOnce(MoreRoutes.STORE_DETAILS) },
                    onOpenCategories = { navController.navigateOnce(MoreRoutes.CATEGORIES) },
                )
            }
            guarded(navController, MoreRoutes.CATEGORIES) {
                com.auxo.pos.ui.screens.more.CategoriesScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.PRINTER) {
                com.auxo.pos.ui.screens.more.PrinterSettingsScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.STORE_DETAILS) {
                StoreDetailsScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.APPEARANCE) {
                AppearanceScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.QUICK_UNLOCK) {
                QuickUnlockScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.BACKUP) {
                BackupScreen(onBack = { navController.popOnce() })
            }
            guarded(navController, MoreRoutes.REPORTS) {
                ReportsScreen(onBack = { navController.popOnce() })
            }
        }
    }
}

/**
 * A page that is only built for people allowed to see it (see [RouteAccess]). Someone who reaches it any other way
 * is sent Home, and the page's screen and view model are never created.
 */
private fun NavGraphBuilder.guarded(
    navController: NavHostController,
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments) { entry ->
        val needed = RouteAccess.permissionFor(route)
        if (needed == null) {
            content(entry)
        } else {
            RequirePermission(needed, onDenied = { navController.navigateToTopLevel(TopLevelDestination.HOME) }) { content(entry) }
        }
    }
}

/** Suppliers, purchase orders, goods receipts, stock alerts and movement history, inside the Inventory graph. */
private fun NavGraphBuilder.stockGraph(navController: NavHostController) {
    guarded(navController, StockRoutes.SUPPLIERS) {
        SupplierListScreen(
            onBack = { navController.popOnce() },
            onOpenSupplier = { navController.navigateOnce(StockRoutes.editSupplier(it)) },
            onAddSupplier = { navController.navigateOnce(StockRoutes.newSupplier()) },
            onOpenPurchases = { navController.navigateOnce(StockRoutes.purchases()) },
            onReceiveStock = { navController.navigateOnce(StockRoutes.receive()) },
        )
    }
    guarded(navController,
        route = StockRoutes.SUPPLIER_EDITOR,
        arguments = listOf(
            navArgument(StockRoutes.ARG_SUPPLIER_ID) {
                type = NavType.LongType
                defaultValue = 0L
            },
        ),
    ) {
        SupplierEditorScreen(
            onBack = { navController.popOnce() },
            onSaved = { navController.popOnce() },
        )
    }
    guarded(navController,
        route = StockRoutes.PURCHASES,
        arguments = listOf(
            navArgument(StockRoutes.ARG_PURCHASE_TAB) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        PurchaseListScreen(
            onBack = { navController.popOnce() },
            onOpenOrder = { navController.navigateOnce(StockRoutes.order(it)) },
            onOpenReceipt = { navController.navigateOnce(StockRoutes.receipt(it)) },
            onNewOrder = { navController.navigateOnce(StockRoutes.newOrder()) },
            onReceive = { navController.navigateOnce(StockRoutes.receive()) },
            onOpenSuppliers = { navController.navigateOnce(StockRoutes.suppliers()) },
        )
    }
    guarded(navController,
        route = StockRoutes.ORDER,
        arguments = listOf(navArgument(StockRoutes.ARG_ORDER_ID) { type = NavType.LongType }),
    ) {
        PurchaseOrderDetailScreen(
            onBack = { navController.popOnce() },
            onEdit = { navController.navigateOnce(StockRoutes.editOrder(it)) },
            onReceive = { navController.navigateOnce(StockRoutes.receive(it)) },
            onOpenReceipt = { navController.navigateOnce(StockRoutes.receipt(it)) },
        )
    }
    guarded(navController,
        route = StockRoutes.ORDER_EDITOR,
        arguments = listOf(
            navArgument(StockRoutes.ARG_ORDER_ID) {
                type = NavType.LongType
                defaultValue = 0L
            },
        ),
    ) { entry ->
        val editingId = entry.arguments?.getLong(StockRoutes.ARG_ORDER_ID) ?: 0L
        PurchaseOrderEditorScreen(
            onBack = { navController.popOnce() },
            onSaved = { orderId ->
                if (editingId != 0L) {
                    navController.popOnce()
                } else {
                    navController.navigate(StockRoutes.order(orderId)) {
                        popUpTo(StockRoutes.ORDER_EDITOR) { inclusive = true }
                    }
                }
            },
            onAddSupplier = { navController.navigateOnce(StockRoutes.newSupplier()) },
        )
    }
    guarded(navController,
        route = StockRoutes.RECEIVE,
        arguments = listOf(
            navArgument(StockRoutes.ARG_ORDER_ID) {
                type = NavType.LongType
                defaultValue = 0L
            },
        ),
    ) {
        ReceiveGoodsScreen(
            onBack = { navController.popOnce() },
            onSaved = { receiptId ->
                navController.navigate(StockRoutes.receipt(receiptId)) {
                    popUpTo(StockRoutes.RECEIVE) { inclusive = true }
                }
            },
            onAddSupplier = { navController.navigateOnce(StockRoutes.newSupplier()) },
        )
    }
    guarded(navController,
        route = StockRoutes.RECEIPT,
        arguments = listOf(navArgument(StockRoutes.ARG_RECEIPT_ID) { type = NavType.LongType }),
    ) {
        ReceiptDetailScreen(
            onBack = { navController.popOnce() },
            onOpenOrder = { navController.navigateOnce(StockRoutes.order(it)) },
        )
    }
    guarded(navController, StockRoutes.MOVEMENTS) {
        StockMovementsScreen(
            onBack = { navController.popOnce() },
            onOpenProduct = { navController.navigateOnce(InventoryRoutes.product(it)) },
        )
    }
    guarded(navController,
        route = StockRoutes.ALERTS,
        arguments = listOf(
            navArgument(StockRoutes.ARG_ALERTS_TAB) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        StockAlertsScreen(
            onBack = { navController.popOnce() },
            onOpenProduct = { navController.navigateOnce(InventoryRoutes.product(it)) },
            onOpenOrders = { navController.navigateOnce(StockRoutes.purchases(PurchaseTab.ORDERS)) },
        )
    }
    guarded(navController, StockRoutes.COUNT) {
        StockCountScreen(onBack = { navController.popOnce() })
    }
    guarded(navController, StockRoutes.IMPORT) {
        ProductImportScreen(onBack = { navController.popOnce() })
    }
}

/**
 * Navigates to [route], but only while the current screen is actually the one on top (its
 * lifecycle is RESUMED). A tap that lands while a navigation animation is still finishing — for
 * example a second, impatient tap on a card because the first tap hadn't visibly done anything
 * yet — is ignored instead of pushing a second copy of the destination onto the back stack. That
 * duplicate copy is what makes Back feel unresponsive: the first Back press only pops back to the
 * identical-looking duplicate, so it looks like nothing happened and a second press is needed.
 */
fun NavHostController.navigateOnce(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
        navigate(route) { launchSingleTop = true }
    }
}

/** Pops the back stack, but only once per settled screen — see [navigateOnce]. */
fun NavHostController.popOnce() {
    if (currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

/**
 * Switches tabs.
 *  - Tapping the tab you are already on goes back to that tab's main page, from any page
 *    inside it (an invoice, a product, a customer, a menu option).
 *  - Home always returns to the Home screen, whatever screen you were on. What you were
 *    doing is saved and comes back with its tab.
 *  - More always opens its own menu page, never the option you last used from it.
 *  - Billing, Inventory and Customers keep their place (a half-built bill, a scrolled list)
 *    when you leave for another tab and come back.
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    if (destination == TopLevelDestination.HOME) {
        val home = TopLevelDestination.HOME.route
        if (popBackStack(home, inclusive = false, saveState = true)) return
        if (currentDestination?.route != home) {
            navigate(home) {
                popUpTo(graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
        return
    }

    val current = currentDestination
    if (current?.hierarchy?.any { it.route == destination.route } == true) {
        // Already inside this tab: go back to its main page.
        if (current.route == destination.rootRoute) return
        if (popBackStack(destination.rootRoute, inclusive = false)) return
        // The page was opened from elsewhere (Home, More), so the tab's main page is not
        // underneath it. Open the tab fresh in its place.
        navigate(destination.route) {
            popUpTo(graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
        }
        return
    }

    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = destination != TopLevelDestination.MORE
    }
}

/** Opens the Inventory tab with a stock filter already applied. */
fun NavHostController.navigateToInventory(filter: StockFilter) {
    navigate(InventoryRoutes.list(filter)) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
    }
}
