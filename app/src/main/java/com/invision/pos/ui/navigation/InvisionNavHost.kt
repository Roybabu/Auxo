package com.invision.pos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.invision.pos.ui.screens.home.HomeScreen
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
            HomeScreen(onNavigate = navController::navigateToTopLevel)
        }
        composable(TopLevelDestination.BILLING.route) {
            ComingSoonScreen(destination = TopLevelDestination.BILLING)
        }
        composable(TopLevelDestination.INVENTORY.route) {
            ComingSoonScreen(destination = TopLevelDestination.INVENTORY)
        }
        composable(TopLevelDestination.CUSTOMERS.route) {
            ComingSoonScreen(destination = TopLevelDestination.CUSTOMERS)
        }
        composable(TopLevelDestination.MORE.route) {
            ComingSoonScreen(destination = TopLevelDestination.MORE)
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
