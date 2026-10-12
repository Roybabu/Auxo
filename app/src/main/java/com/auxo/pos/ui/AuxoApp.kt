package com.auxo.pos.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auxo.pos.ui.navigation.AuxoNavHost
import com.auxo.pos.ui.navigation.TopLevelDestination
import com.auxo.pos.ui.navigation.navigateToTopLevel

/**
 * Root composable. The five main destinations appear as a bottom bar on phones
 * and as a side rail on tablets and other wide screens. The bar is hidden on the Home
 * screen, which has its own quick actions, and appears once you go anywhere else.
 */
@Composable
fun AuxoApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onHome = currentDestination?.route == TopLevelDestination.HOME.route
    val capabilities = LocalCapabilities.current
    val adaptiveLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            // Tabs a person may not use are not offered; their pages also refuse them (RouteAccess).
            TopLevelDestination.entries.filter { it.permission == null || capabilities.can(it.permission) }.forEach { destination ->
                item(
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
        layoutType = if (onHome) NavigationSuiteType.None else adaptiveLayout,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        AuxoNavHost(navController = navController)
    }
}
