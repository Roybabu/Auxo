package com.invision.pos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.invision.pos.domain.inventory.StockFilter
import com.invision.pos.ui.screens.billing.BillingScreen
import com.invision.pos.ui.screens.billing.BillingSettingsScreen
import com.invision.pos.ui.screens.billing.InvoiceDetailScreen
import com.invision.pos.ui.screens.billing.InvoiceHistoryScreen
import com.invision.pos.ui.screens.billing.ReturnScreen
import com.invision.pos.ui.screens.home.HomeScreen
import com.invision.pos.ui.screens.inventory.InventoryListScreen
import com.invision.pos.ui.screens.inventory.LabelPrintScreen
import com.invision.pos.ui.screens.inventory.ProductDetailScreen
import com.invision.pos.ui.screens.inventory.ProductEditorScreen
import com.invision.pos.ui.screens.inventory.StockPage
import com.invision.pos.ui.screens.customers.AccountPaymentScreen
import com.invision.pos.ui.screens.customers.CustomerDetailScreen
import com.invision.pos.ui.screens.customers.CustomerEditorScreen
import com.invision.pos.ui.screens.customers.CustomerListScreen
import com.invision.pos.ui.screens.menu.MenuEditorScreen
import com.invision.pos.ui.screens.menu.MenuListScreen
import com.invision.pos.ui.screens.more.MoreActions
import com.invision.pos.ui.screens.more.MoreScreen
import com.invision.pos.ui.screens.purchasing.PurchaseListScreen
import com.invision.pos.ui.screens.purchasing.PurchaseOrderDetailScreen
import com.invision.pos.ui.screens.purchasing.PurchaseOrderEditorScreen
import com.invision.pos.ui.screens.purchasing.ReceiptDetailScreen
import com.invision.pos.ui.screens.purchasing.ReceiveGoodsScreen
import com.invision.pos.ui.screens.purchasing.SupplierEditorScreen
import com.invision.pos.ui.screens.purchasing.SupplierListScreen
import com.invision.pos.ui.screens.reports.ReportsScreen
import com.invision.pos.ui.screens.stock.StockAlertsScreen
import com.invision.pos.ui.screens.stock.StockMovementsScreen
import com.invision.pos.viewmodel.InvoiceNotice
import com.invision.pos.viewmodel.PurchaseTab

@Composable
fun InvisionNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(
                onNavigate = navController::navigateToTopLevel,
                onAddProduct = { navController.navigate(InventoryRoutes.newProduct()) },
                onOpenProduct = { navController.navigate(InventoryRoutes.product(it)) },
                onOpenInventory = { filter -> navController.navigateToInventory(filter) },
                onOpenAlerts = { navController.navigate(StockRoutes.alerts()) },
                onReceiveStock = { navController.navigate(StockRoutes.receive()) },
            )
        }
        navigation(
            startDestination = BillingRoutes.POS,
            route = TopLevelDestination.BILLING.route,
        ) {
            composable(BillingRoutes.POS) {
                BillingScreen(
                    onSaleCompleted = { invoiceId, changeMinor ->
                        navController.navigate(BillingRoutes.savedInvoice(invoiceId, InvoiceNotice.SALE_SAVED, changeMinor))
                    },
                    onOpenHistory = { navController.navigate(BillingRoutes.HISTORY) },
                    onOpenSettings = { navController.navigate(BillingRoutes.SETTINGS) },
                    onAddProduct = { barcode -> navController.navigate(InventoryRoutes.newProduct(barcode)) },
                )
            }
            composable(
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
            composable(BillingRoutes.HISTORY) {
                InvoiceHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenInvoice = { navController.navigate(BillingRoutes.invoice(it)) },
                )
            }
            composable(
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
            composable(BillingRoutes.SETTINGS) {
                BillingSettingsScreen(onBack = { navController.popBackStack() })
            }
        }
        navigation(
            startDestination = InventoryRoutes.LIST,
            route = TopLevelDestination.INVENTORY.route,
        ) {
            composable(
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
            composable(
                route = InventoryRoutes.PRODUCT,
                arguments = listOf(navArgument(InventoryRoutes.ARG_PRODUCT_ID) { type = NavType.LongType }),
            ) {
                ProductDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(InventoryRoutes.editProduct(it)) },
                    onPrintLabels = { navController.navigate(InventoryRoutes.labels(it)) },
                )
            }
            composable(
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
            composable(
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
            composable(CustomerRoutes.LIST) {
                CustomerListScreen(
                    onOpenCustomer = { navController.navigate(CustomerRoutes.detail(it)) },
                    onAddCustomer = { navController.navigate(CustomerRoutes.newCustomer()) },
                )
            }
            composable(
                route = CustomerRoutes.DETAIL,
                arguments = listOf(navArgument(CustomerRoutes.ARG_CUSTOMER_ID) { type = NavType.LongType }),
            ) {
                CustomerDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(CustomerRoutes.editCustomer(it)) },
                    onReceivePayment = { navController.navigate(CustomerRoutes.payment(it)) },
                    onOpenInvoice = { navController.navigate(BillingRoutes.invoice(it)) },
                )
            }
            composable(
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
            composable(
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
            composable(MoreRoutes.HOME) {
                MoreScreen(
                    actions = object : MoreActions {
                        override fun onOpenSuppliers() = navController.navigate(StockRoutes.suppliers())
                        override fun onOpenPurchases() = navController.navigate(StockRoutes.purchases())
                        override fun onReceiveStock() = navController.navigate(StockRoutes.receive())
                        override fun onOpenAlerts() = navController.navigate(StockRoutes.alerts())
                        override fun onOpenMovements() = navController.navigate(StockRoutes.movements())
                        override fun onOpenMenu() = navController.navigate(MoreRoutes.MENU)
                        override fun onOpenReports() = navController.navigate(MoreRoutes.REPORTS)
                        override fun onOpenHistory() = navController.navigate(BillingRoutes.HISTORY)
                        override fun onOpenBillingSettings() = navController.navigate(BillingRoutes.SETTINGS)
                    },
                )
            }
            composable(MoreRoutes.MENU) {
                MenuListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDish = { navController.navigate(MoreRoutes.editMenuItem(it)) },
                    onAddDish = { navController.navigate(MoreRoutes.newMenuItem()) },
                )
            }
            composable(
                route = MoreRoutes.MENU_EDITOR,
                arguments = listOf(
                    navArgument(MoreRoutes.ARG_MENU_ITEM_ID) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) {
                MenuEditorScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(MoreRoutes.REPORTS) {
                ReportsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Suppliers, purchase orders, goods receipts, stock alerts and movement history, inside the Inventory graph. */
private fun NavGraphBuilder.stockGraph(navController: NavHostController) {
    composable(StockRoutes.SUPPLIERS) {
        SupplierListScreen(
            onBack = { navController.popBackStack() },
            onOpenSupplier = { navController.navigate(StockRoutes.editSupplier(it)) },
            onAddSupplier = { navController.navigate(StockRoutes.newSupplier()) },
        )
    }
    composable(
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
    composable(
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
    composable(
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
    composable(
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
    composable(
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
    composable(
        route = StockRoutes.RECEIPT,
        arguments = listOf(navArgument(StockRoutes.ARG_RECEIPT_ID) { type = NavType.LongType }),
    ) {
        ReceiptDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenOrder = { navController.navigate(StockRoutes.order(it)) },
        )
    }
    composable(StockRoutes.MOVEMENTS) {
        StockMovementsScreen(
            onBack = { navController.popBackStack() },
            onOpenProduct = { navController.navigate(InventoryRoutes.product(it)) },
        )
    }
    composable(
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
}

/**
 * Switches tabs. Billing, Inventory and Customers keep their place (a half-built bill, a
 * scrolled list) when you leave and come back. Two tabs are deliberate exceptions:
 *  - Home always returns to the Home screen, whatever screen you were on (editor, receive
 *    stock, a new bill...). What you were doing is saved and comes back with its tab.
 *  - More always opens its own menu page, never the option you last used from it.
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
