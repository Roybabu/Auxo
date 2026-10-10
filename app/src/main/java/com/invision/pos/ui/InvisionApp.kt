package com.invision.pos.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.invision.pos.ui.navigation.InvisionNavHost
import com.invision.pos.ui.navigation.TopLevelDestination
import com.invision.pos.ui.navigation.navigateToTopLevel

/**
 * Root composable. The five main destinations appear as a bottom bar on phones
 * and as a side rail on tablets and other wide screens.
 */
@Composable
fun InvisionApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        InvisionNavHost(navController = navController)
    }
}
