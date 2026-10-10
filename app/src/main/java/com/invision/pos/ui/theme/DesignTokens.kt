package com.invision.pos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Spacing scale used for padding and gaps across the app. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal padding for screen content. */
    val screen = 16.dp
}

/** Foreground and background pair for a status such as "In stock" or "Expired". */
@Immutable
data class ToneColors(val container: Color, val content: Color, val accent: Color)

/** Semantic colours that Material 3 does not provide. */
@Immutable
data class StatusColors(
    val success: ToneColors,
    val warning: ToneColors,
    val danger: ToneColors,
    val neutral: ToneColors,
)

val LightStatusColors = StatusColors(
    success = ToneColors(container = Green100, content = Green900, accent = Green700),
    warning = ToneColors(container = Amber100, content = Amber900, accent = Amber700),
    danger = ToneColors(container = Red100, content = Red900, accent = Red700),
    neutral = ToneColors(container = ContainerHigh, content = InkMuted, accent = InkMuted),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Access to Invision-specific theme values, alongside MaterialTheme. */
object InvisionThemeExtras {
    val statusColors: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}
