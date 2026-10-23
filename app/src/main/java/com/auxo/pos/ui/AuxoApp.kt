package com.auxo.pos.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auxo.pos.ui.navigation.AuxoNavHost
import com.auxo.pos.ui.navigation.TopLevelDestination
import com.auxo.pos.ui.navigation.navigateToTopLevel
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize

/**
 * Root composable. The five main destinations appear as a bottom bar on phones
 * and as a side rail on tablets and other wide screens. The bar is hidden on the Home
 * screen, which has its own quick actions, and appears once you go anywhere else.
 *
 * The app draws edge to edge, so the on-screen keyboard is drawn over the page instead of shrinking it. Every screen
 * sits inside [imePadding] for that reason: the page shrinks to what the keyboard leaves, and a field being typed in
 * scrolls into view instead of ending up behind the keys. The bottom bar steps out of the way while the keyboard is up,
 * otherwise its height would be left as a gap above the keys.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuxoApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onHome = currentDestination?.route == TopLevelDestination.HOME.route
    LaunchedEffect(currentDestination?.route) {
        currentDestination?.route?.let { com.auxo.pos.core.debuglog.DebugLog.event("Screen", it) }
    }
    val capabilities = LocalCapabilities.current
    val adaptiveLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val imeVisible = WindowInsets.isImeVisible

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            // Tabs a person may not use are not offered; their pages also refuse them (RouteAccess).
            TopLevelDestination.entries.filter { it.permission == null || capabilities.can(it.permission) }.forEach { destination ->
                item(
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = {
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        AuxoIcon(destination.iconRes, contentDescription = null, size = AuxoIconSize.Nav, selected = selected)
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
        layoutType = when {
            onHome -> NavigationSuiteType.None
            imeVisible && adaptiveLayout == NavigationSuiteType.NavigationBar -> NavigationSuiteType.None
            else -> adaptiveLayout
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        AuxoNavHost(navController = navController, modifier = Modifier.imePadding())
    }
}
