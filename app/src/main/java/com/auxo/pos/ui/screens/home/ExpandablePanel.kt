// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The two heights the panel rests at, as the distance in pixels from the top of the
 * screen to the panel's top edge. [collapsedTop] is where it starts; [expandedTop] is
 * as high as it can go. [expandedTop] is never below [collapsedTop].
 */
data class PanelExtents(val expandedTop: Float, val collapsedTop: Float) {

    init {
        require(expandedTop <= collapsedTop) { "The expanded position can't be lower than the collapsed one" }
    }

    /** Pixels the panel can travel. Zero on a screen too small to expand. */
    val travel: Float get() = collapsedTop - expandedTop

    fun clamp(top: Float): Float = top.coerceIn(expandedTop, collapsedTop)

    /** 0 when collapsed, 1 when expanded. */
    fun expansionAt(top: Float): Float = if (travel <= 0f) 0f else (collapsedTop - clamp(top)) / travel

    fun topAt(expansion: Float): Float = collapsedTop - travel * expansion.coerceIn(0f, 1f)

    /**
     * Where the panel should come to rest when let go at [top] moving at [velocity]
     * (pixels per second, negative is upwards): a quick flick goes the way it points,
     * a slow release goes to the nearer end.
     */
    fun settleTarget(top: Float, velocity: Float): Float = when {
        travel <= 0f -> collapsedTop
        velocity <= -FLING_VELOCITY -> expandedTop
        velocity >= FLING_VELOCITY -> collapsedTop
        expansionAt(top) >= 0.5f -> expandedTop
        else -> collapsedTop
    }

    companion object {
        /** Pixels per second above which a release counts as a flick. */
        const val FLING_VELOCITY = 800f

        /** The collapsed panel starts this far down, as a fraction of the screen width, as in the design. */
        const val COLLAPSED_WIDTH_RATIO = 0.975f
        const val COLLAPSED_MAX_DP = 420f

        /** The collapsed panel never takes more than this share of the height away from the content. */
        const val COLLAPSED_HEIGHT_RATIO = 0.66f

        /** Space under the status bar kept for the store row when the panel is fully open. */
        const val EXPANDED_BELOW_STATUS_DP = 84f

        /** Least distance the panel travels, so dragging always does something visible. */
        const val MIN_TRAVEL_DP = 96f

        /**
         * Extents for a screen of [widthDp] by [heightDp] with a status bar [statusBarDp]
         * tall, converted to pixels with [density].
         */
        fun forScreen(widthDp: Float, heightDp: Float, statusBarDp: Float, density: Float): PanelExtents {
            val collapsed = min(min(widthDp * COLLAPSED_WIDTH_RATIO, COLLAPSED_MAX_DP), heightDp * COLLAPSED_HEIGHT_RATIO)
            val expanded = min(statusBarDp + EXPANDED_BELOW_STATUS_DP, collapsed - MIN_TRAVEL_DP).coerceAtLeast(statusBarDp)
            return PanelExtents(expandedTop = expanded * density, collapsedTop = max(collapsed, expanded) * density)
        }
    }
}

/** Where the panel is, and the moves that change it: dragging by hand and animating to a rest position. */
@Stable
class ExpandablePanelState(extents: PanelExtents, private val scope: CoroutineScope) {

    var extents: PanelExtents by mutableStateOf(extents)
        private set

    /** Distance in pixels from the top of the screen to the top edge of the panel. */
    var top: Float by mutableFloatStateOf(extents.collapsedTop)
        private set

    private var animation: Job? = null

    val isExpanded: Boolean get() = top <= extents.expandedTop + REST_TOLERANCE
    val isCollapsed: Boolean get() = top >= extents.collapsedTop - REST_TOLERANCE
    val isAtRest: Boolean get() = isExpanded || isCollapsed

    /** Moves the panel by [delta] pixels, stopping at either end; returns how much it actually moved. */
    fun dragBy(delta: Float): Float {
        animation?.cancel()
        val target = extents.clamp(top + delta)
        val moved = target - top
        top = target
        return moved
    }

    /** Animates to the rest position that suits letting go at the current spot with [velocity]. */
    fun settle(velocity: Float = 0f) = animateTo(extents.settleTarget(top, velocity), velocity)

    fun expand() = animateTo(extents.expandedTop, 0f)

    fun collapse() = animateTo(extents.collapsedTop, 0f)

    /** Keeps the panel at the same fraction of its travel when the screen size changes. */
    fun updateExtents(new: PanelExtents) {
        if (new == extents) return
        val expansion = extents.expansionAt(top)
        animation?.cancel()
        extents = new
        top = new.topAt(expansion)
    }

    private fun animateTo(target: Float, velocity: Float) {
        animation?.cancel()
        animation = scope.launch {
            animate(
                initialValue = top,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> top = extents.clamp(value) }
        }
    }

    private companion object {
        const val REST_TOLERANCE = 0.5f
    }
}

/**
 * Lets the panel and its scrolling content share one gesture. A finger moving up first
 * lifts the panel and only then scrolls the content; a finger moving down first scrolls
 * the content back to its top and only then lowers the panel.
 */
private class PanelScrollConnection(private val state: ExpandablePanelState) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
        return Offset(0f, state.dragBy(available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        return Offset(0f, state.dragBy(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.isAtRest) return Velocity.Zero
        state.settle(available.y)
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // The content has run out of room, so a flick down at the top of the content lowers the panel.
        if (state.isExpanded && available.y >= PanelExtents.FLING_VELOCITY) state.settle(available.y)
        return Velocity.Zero
    }
}

/**
 * A rounded white panel over the header that the user can drag up to open and down to
 * close, between the screen's [extents]. [content] should scroll vertically itself; the
 * panel hands drags to it once fully open.
 */
@Composable
fun ExpandablePanel(
    extents: PanelExtents,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember { ExpandablePanelState(extents, scope) }
    LaunchedEffect(extents) { state.updateExtents(extents) }
    val connection = remember(state) { PanelScrollConnection(state) }
    val expandLabel = stringResource(R.string.home_panel_expand)
    val collapseLabel = stringResource(R.string.home_panel_collapse)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                // The panel always reaches the bottom of the screen, so its height follows its top edge.
                val top = state.top.roundToInt().coerceAtMost(constraints.maxHeight)
                val height = (constraints.maxHeight - top).coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, top) }
            }
            .nestedScroll(connection)
            .semantics {
                testTag = "home_panel"
                customActions = listOf(
                    CustomAccessibilityAction(expandLabel) { state.expand(); true },
                    CustomAccessibilityAction(collapseLabel) { state.collapse(); true },
                )
            },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

/** Panel extents for the space this composable is laid out in. */
@Composable
fun rememberPanelExtents(widthDp: Float, heightDp: Float, statusBarDp: Float): PanelExtents {
    val density = LocalDensity.current.density
    return remember(widthDp, heightDp, statusBarDp, density) {
        PanelExtents.forScreen(widthDp, heightDp, statusBarDp, density)
    }
}
