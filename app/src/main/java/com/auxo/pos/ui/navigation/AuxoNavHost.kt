package com.auxo.pos.ui.navigation

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
    ) {
        guarded(navController, TopLevelDestination.HOME.route) {
            if (can(Permission.VIEW_DASHBOARD)) {
                HomeScreen(
                    onNavigate = navController::navigateToTopLevel,
                    onAddProduct = { navController.navigate(InventoryRoutes.newProduct()) },
                    onOpenProduct = { navController.navigate(InventoryRoutes.product(it)) },
                    onOpenInventory = { filter -> navController.navigateToInventory(filter) },
                    onOpenAlerts = { navController.navigate(StockRoutes.alerts()) },
                    onOpenBackup = { navController.navigate(MoreRoutes.BACKUP) },
                )
            } else {
                EmployeeHomeScreen(
                    onNavigate = navController::navigateToTopLevel,
                    onOpenInvoices = { navController.navigate(BillingRoutes.HISTORY) },
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
                        navController.navigate(BillingRoutes.savedInvoice(invoiceId, InvoiceNotice.SALE_SAVED, changeMinor))
                    },
                    onOpenHistory = { navController.navigate(BillingRoutes.HISTORY) },
                    onOpenSettings = { navController.navigate(MoreRoutes.STORE_DETAILS) },
                    onAddProduct = { barcode -> navController.navigate(InventoryRoutes.newProduct(barcode)) },
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
                    onBack = { navController.popBackStack() },
                    onReturn = { navController.navigate(BillingRoutes.returnFor(it)) },
                    onOpenInvoice = { navController.navigate(BillingRoutes.invoice(it)) },
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
                    onBack = { navController.popBackStack() },
                    onOpenInvoice = { navController.navigate(BillingRoutes.invoice(it)) },
                )
            }
            guarded(navController,
                route = BillingRoutes.RETURN,
                arguments = listOf(navArgument(BillingRoutes.ARG_INVOICE_ID) { type = NavType.LongType }),
            ) {
                ReturnScreen(
                    onBack = { navController.popBackStack() },
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
                    onOpenProduct = { navController.navigate(InventoryRoutes.product(it)) },
                    onAddProduct = { barcode -> navController.navigate(InventoryRoutes.newProduct(barcode)) },
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
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(InventoryRoutes.editProduct(it)) },
                    onPrintLabels = { navController.navigate(InventoryRoutes.labels(it)) },
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
                    onBack = { navController.popBackStack() },
                    onSaved = { savedId ->
                        if (editingId != 0L) {
                            navController.popBackStack()
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
                LabelPrintScreen(onBack = { navController.popBackStack() })
            }
            stockGraph(navController)
        }
        navigation(
            startDestination = CustomerRoutes.LIST,
            route = TopLevelDestination.CUSTOMERS.route,
        ) {
            guarded(navController, CustomerRoutes.LIST) {
                CustomerListScreen(
                    onOpenCustomer = { navController.navigate(CustomerRoutes.detail(it)) },
                    onAddCustomer = { navController.navigate(CustomerRoutes.newCustomer()) },
                )
            }
            guarded(navController,
                route = CustomerRoutes.DETAIL,
                arguments = listOf(navArgument(CustomerRoutes.ARG_CUSTOMER_ID) { type = NavType.LongType }),
            ) {
                CustomerDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(CustomerRoutes.editCustomer(it)) },
                    onReceivePayment = { navController.navigate(CustomerRoutes.payment(it)) },
                    onOpenHistory = { navController.navigate(CustomerRoutes.history(it)) },
                    onOpenInvoice = { navController.navigate(BillingRoutes.invoice(it)) },
                )
            }
            guarded(navController,
                route = CustomerRoutes.HISTORY,
                arguments = listOf(navArgument(CustomerRoutes.ARG_CUSTOMER_ID) { type = NavType.LongType }),
            ) {
                CustomerHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenInvoice = { navController.navigate(BillingRoutes.invoice(it)) },
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
                    onBack = { navController.popBackStack() },
                    onSaved = { savedId ->
                        if (editingId != 0L) {
                            navController.popBackStack()
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
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
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
                        override fun onOpenSuppliers() = navController.navigate(StockRoutes.suppliers())
                        override fun onOpenAlerts() = navController.navigate(StockRoutes.alerts())
                        override fun onOpenMovements() = navController.navigate(StockRoutes.movements())
                        override fun onOpenCount() = navController.navigate(StockRoutes.count())
                        override fun onOpenImport() = navController.navigate(StockRoutes.import())
                        override fun onOpenReports() = navController.navigate(MoreRoutes.REPORTS)
                        override fun onOpenHistory() = navController.navigate(BillingRoutes.HISTORY)
                        override fun onOpenAppearance() = navController.navigate(MoreRoutes.APPEARANCE)
                        override fun onOpenBackup() = navController.navigate(MoreRoutes.BACKUP)
                        override fun onOpenShopSettings() = navController.navigate(MoreRoutes.SHOP_SETTINGS)
                        override fun onOpenPrinter() = navController.navigate(MoreRoutes.PRINTER)
                        override fun onOpenUsers() = navController.navigate(MoreRoutes.USER_MANAGEMENT)
                        override fun onOpenRecoveryCode() = navController.navigate(MoreRoutes.RECOVERY_CODE)
                        override fun onOpenQuickUnlock() = navController.navigate(MoreRoutes.QUICK_UNLOCK)
                        override fun onOpenActivity() = navController.navigate(MoreRoutes.ACTIVITY_LOG)
                        override fun onOpenDayEnd() = navController.navigate(MoreRoutes.DAY_END)
                        override fun onOpenGstReport() = navController.navigate(MoreRoutes.GST_REPORT)
                        override fun onOpenLoyalty() = navController.navigate(MoreRoutes.LOYALTY)
                        override fun onSignOut() = authRepository?.signOut() ?: Unit
                    },
                )
            }
            guarded(navController, MoreRoutes.USER_MANAGEMENT) {
                UserManagementScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.RECOVERY_CODE) {
                RecoveryCodeScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.ACTIVITY_LOG) {
                ActivityLogScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.GST_REPORT) {
                GstReportScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.LOYALTY) {
                LoyaltySettingsScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.DAY_END) {
                DayEndScreen(
                    onBack = { navController.popBackStack() },
                    onOpenClosing = { navController.navigate(MoreRoutes.dayClosing(it)) },
                )
            }
            guarded(
                navController,
                route = MoreRoutes.DAY_CLOSING,
                arguments = listOf(navArgument(MoreRoutes.ARG_CLOSING_ID) { type = NavType.LongType }),
            ) {
                DayClosingScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.SHOP_SETTINGS) {
                ShopSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStoreDetails = { navController.navigate(MoreRoutes.STORE_DETAILS) },
                    onOpenCategories = { navController.navigate(MoreRoutes.CATEGORIES) },
                )
            }
            guarded(navController, MoreRoutes.CATEGORIES) {
                com.auxo.pos.ui.screens.more.CategoriesScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.PRINTER) {
                com.auxo.pos.ui.screens.more.PrinterSettingsScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.STORE_DETAILS) {
                StoreDetailsScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.APPEARANCE) {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.QUICK_UNLOCK) {
                QuickUnlockScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            guarded(navController, MoreRoutes.REPORTS) {
                ReportsScreen(onBack = { navController.popBackStack() })
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
            onBack = { navController.popBackStack() },
            onOpenSupplier = { navController.navigate(StockRoutes.editSupplier(it)) },
            onAddSupplier = { navController.navigate(StockRoutes.newSupplier()) },
            onOpenPurchases = { navController.navigate(StockRoutes.purchases()) },
            onReceiveStock = { navController.navigate(StockRoutes.receive()) },
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
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
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
            onBack = { navController.popBackStack() },
            onOpenOrder = { navController.navigate(StockRoutes.order(it)) },
            onOpenReceipt = { navController.navigate(StockRoutes.receipt(it)) },
            onNewOrder = { navController.navigate(StockRoutes.newOrder()) },
            onReceive = { navController.navigate(StockRoutes.receive()) },
            onOpenSuppliers = { navController.navigate(StockRoutes.suppliers()) },
        )
    }
    guarded(navController,
        route = StockRoutes.ORDER,
        arguments = listOf(navArgument(StockRoutes.ARG_ORDER_ID) { type = NavType.LongType }),
    ) {
        PurchaseOrderDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(StockRoutes.editOrder(it)) },
            onReceive = { navController.navigate(StockRoutes.receive(it)) },
            onOpenReceipt = { navController.navigate(StockRoutes.receipt(it)) },
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
            onBack = { navController.popBackStack() },
            onSaved = { orderId ->
                if (editingId != 0L) {
                    navController.popBackStack()
                } else {
                    navController.navigate(StockRoutes.order(orderId)) {
                        popUpTo(StockRoutes.ORDER_EDITOR) { inclusive = true }
                    }
                }
            },
            onAddSupplier = { navController.navigate(StockRoutes.newSupplier()) },
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
            onBack = { navController.popBackStack() },
            onSaved = { receiptId ->
                navController.navigate(StockRoutes.receipt(receiptId)) {
                    popUpTo(StockRoutes.RECEIVE) { inclusive = true }
                }
            },
            onAddSupplier = { navController.navigate(StockRoutes.newSupplier()) },
        )
    }
    guarded(navController,
        route = StockRoutes.RECEIPT,
        arguments = listOf(navArgument(StockRoutes.ARG_RECEIPT_ID) { type = NavType.LongType }),
    ) {
        ReceiptDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenOrder = { navController.navigate(StockRoutes.order(it)) },
        )
    }
    guarded(navController, StockRoutes.MOVEMENTS) {
        StockMovementsScreen(
            onBack = { navController.popBackStack() },
            onOpenProduct = { navController.navigate(InventoryRoutes.product(it)) },
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
            onBack = { navController.popBackStack() },
            onOpenProduct = { navController.navigate(InventoryRoutes.product(it)) },
            onOpenOrders = { navController.navigate(StockRoutes.purchases(PurchaseTab.ORDERS)) },
        )
    }
    guarded(navController, StockRoutes.COUNT) {
        StockCountScreen(onBack = { navController.popBackStack() })
    }
    guarded(navController, StockRoutes.IMPORT) {
        ProductImportScreen(onBack = { navController.popBackStack() })
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
