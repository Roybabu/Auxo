// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelExtentsTest {

    @Test
    fun phone_collapsedMatchesTheDesign_andExpandedLeavesTheStoreRow() {
        val extents = PanelExtents.forScreen(widthDp = 411f, heightDp = 890f, statusBarDp = 24f, density = 1f)
        assertEquals(400.725f, extents.collapsedTop, 0.01f)
        assertEquals(108f, extents.expandedTop, 0.01f)
    }

    @Test
    fun density_scalesBothPositionsToPixels() {
        val extents = PanelExtents.forScreen(widthDp = 392f, heightDp = 850f, statusBarDp = 0f, density = 2f)
        assertEquals(382.2f * 2, extents.collapsedTop, 0.01f)
        assertEquals(84f * 2, extents.expandedTop, 0.01f)
    }

    @Test
    fun wideScreen_collapsedIsCapped() {
        val extents = PanelExtents.forScreen(widthDp = 800f, heightDp = 1200f, statusBarDp = 24f, density = 1f)
        assertEquals(420f, extents.collapsedTop, 0.01f)
    }

    @Test
    fun shortScreen_collapsedLeavesRoomForContent_andStillTravels() {
        val extents = PanelExtents.forScreen(widthDp = 392f, heightDp = 400f, statusBarDp = 24f, density = 1f)
        assertEquals(264f, extents.collapsedTop, 0.01f)
        assertTrue(extents.travel >= PanelExtents.MIN_TRAVEL_DP)
    }

    @Test
    fun tinyScreen_neverGetsAnInvertedRange() {
        val extents = PanelExtents.forScreen(widthDp = 300f, heightDp = 100f, statusBarDp = 24f, density = 1f)
        assertTrue(extents.expandedTop <= extents.collapsedTop)
        assertTrue(extents.expandedTop >= 24f)
    }

    @Test
    fun invertedRange_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { PanelExtents(expandedTop = 500f, collapsedTop = 100f) }
    }

    private val extents = PanelExtents(expandedTop = 100f, collapsedTop = 500f)

    @Test
    fun slowRelease_goesToTheNearerEnd() {
        assertEquals(100f, extents.settleTarget(top = 250f, velocity = 0f), 0f)
        assertEquals(500f, extents.settleTarget(top = 400f, velocity = 0f), 0f)
    }

    @Test
    fun releaseExactlyHalfway_opens() {
        assertEquals(100f, extents.settleTarget(top = 300f, velocity = 0f), 0f)
    }

    @Test
    fun flick_goesTheWayItPoints() {
        assertEquals(100f, extents.settleTarget(top = 450f, velocity = -1000f), 0f)
        assertEquals(500f, extents.settleTarget(top = 150f, velocity = 1000f), 0f)
    }

    @Test
    fun gentleVelocity_isNotAFlick() {
        assertEquals(500f, extents.settleTarget(top = 450f, velocity = -(PanelExtents.FLING_VELOCITY - 1)), 0f)
    }

    @Test
    fun noRoomToTravel_alwaysStaysCollapsed() {
        assertEquals(300f, PanelExtents(300f, 300f).settleTarget(top = 300f, velocity = -5000f), 0f)
    }

    @Test
    fun expansion_andPosition_convertBothWays() {
        assertEquals(0f, extents.expansionAt(500f), 0f)
        assertEquals(1f, extents.expansionAt(100f), 0f)
        assertEquals(0.5f, extents.expansionAt(300f), 0f)
        assertEquals(300f, extents.topAt(0.5f), 0f)
        assertEquals(100f, extents.clamp(-50f), 0f)
        assertEquals(500f, extents.clamp(900f), 0f)
    }
}

class ExpandablePanelStateTest {

    private fun state() = ExpandablePanelState(PanelExtents(expandedTop = 100f, collapsedTop = 500f), CoroutineScope(Job()))

    @Test
    fun startsCollapsed() {
        val state = state()
        assertEquals(500f, state.top, 0f)
        assertTrue(state.isCollapsed)
        assertFalse(state.isExpanded)
    }

    @Test
    fun draggingUp_movesByTheDistance() {
        val state = state()
        assertEquals(-150f, state.dragBy(-150f), 0f)
        assertEquals(350f, state.top, 0f)
        assertFalse(state.isAtRest)
    }

    @Test
    fun draggingPastTheTop_stopsAtExpanded_andReportsWhatItUsed() {
        val state = state()
        state.dragBy(-150f)
        assertEquals(-250f, state.dragBy(-1000f), 0f)
        assertTrue(state.isExpanded)
        assertEquals(0f, state.dragBy(-10f), 0f)
    }

    @Test
    fun draggingPastTheBottom_stopsAtCollapsed() {
        val state = state()
        state.dragBy(-1000f)
        assertEquals(400f, state.dragBy(1000f), 0f)
        assertTrue(state.isCollapsed)
        assertEquals(0f, state.dragBy(10f), 0f)
    }

    @Test
    fun newExtents_keepTheSameShareOfTheTravel() {
        val state = state()
        state.dragBy(-200f)
        state.updateExtents(PanelExtents(expandedTop = 200f, collapsedTop = 1000f))
        assertEquals(600f, state.top, 0f)
    }

    @Test
    fun unchangedExtents_changeNothing() {
        val state = state()
        state.dragBy(-123f)
        state.updateExtents(PanelExtents(expandedTop = 100f, collapsedTop = 500f))
        assertEquals(377f, state.top, 0f)
    }
}
