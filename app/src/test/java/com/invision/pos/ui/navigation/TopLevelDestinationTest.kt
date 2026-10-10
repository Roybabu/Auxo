package com.invision.pos.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun destinations_areInNavigationBarOrder() {
        assertEquals(
            listOf(
                TopLevelDestination.HOME,
                TopLevelDestination.BILLING,
                TopLevelDestination.INVENTORY,
                TopLevelDestination.CUSTOMERS,
                TopLevelDestination.MORE,
            ),
            TopLevelDestination.entries,
        )
    }

    @Test
    fun routes_areUnique() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun startDestination_isHome() {
        assertEquals(TopLevelDestination.HOME, TopLevelDestination.START)
    }
}
