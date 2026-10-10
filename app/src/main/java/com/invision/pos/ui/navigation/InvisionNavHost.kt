package com.invision.pos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.invision.pos.ui.screens.more.MoreScreen
import com.invision.pos.ui.screens.placeholder.ComingSoonScreen

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
            )
        }
        navigation(
            startDestination = BillingRoutes.POS,
            route = TopLevelDestination.BILLING.route,
        ) {
            composable(BillingRoutes.POS) {
                BillingScreen(
                    onSaleCompleted = { navController.navigate(BillingRoutes.invoice(it)) },
                    onOpenHistory = { navController.navigate(BillingRoutes.HISTORY) },
                    onOpenSettings = { navController.navigate(BillingRoutes.SETTINGS) },
                    onAddProduct = { barcode -> navController.navigate(InventoryRoutes.newProduct(barcode)) },
                )
            }
            composable(
                route = BillingRoutes.INVOICE,
                arguments = listOf(navArgument(BillingRoutes.ARG_INVOICE_ID) { type = NavType.LongType }),
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
                        navController.navigate(BillingRoutes.invoice(creditNoteId)) {
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
        }
        composable(TopLevelDestination.CUSTOMERS.route) {
            ComingSoonScreen(destination = TopLevelDestination.CUSTOMERS)
        }
        composable(TopLevelDestination.MORE.route) {
            MoreScreen(
                onOpenBillingSettings = { navController.navigate(BillingRoutes.SETTINGS) },
                onOpenHistory = { navController.navigate(BillingRoutes.HISTORY) },
            )
        }
    }
}

/**
 * Switches tabs the standard way: one copy of each tab on the back stack,
 * with each tab's state saved and restored when you leave and return.
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
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
