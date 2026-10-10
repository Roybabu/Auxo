package com.invision.pos.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.invision.pos.ui.components.InvisionBottomBar
import com.invision.pos.ui.navigation.InvisionNavHost
import com.invision.pos.ui.navigation.TopLevelDestination
import com.invision.pos.ui.navigation.navigateToTopLevel

/** Root composable: main navigation bar plus the screen for the selected tab. */
@Composable
fun InvisionApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            InvisionBottomBar(
                destinations = TopLevelDestination.entries,
                isSelected = { destination ->
                    currentDestination?.hierarchy?.any { it.route == destination.route } == true
                },
                onSelect = { destination -> navController.navigateToTopLevel(destination) },
            )
        },
    ) { innerPadding ->
        InvisionNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
