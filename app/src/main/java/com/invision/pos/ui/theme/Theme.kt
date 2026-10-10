package com.invision.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val InvisionLightColors = lightColorScheme(
    primary = Emerald700,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,
    // Material uses the secondary container for selected chips and the navigation
    // indicator, so it stays in the emerald family.
    secondary = Sage600,
    onSecondary = Color.White,
    secondaryContainer = Emerald100,
    onSecondaryContainer = Emerald900,
    tertiary = Amber700,
    onTertiary = Color.White,
    tertiaryContainer = Amber100,
    onTertiaryContainer = Amber900,
    error = Red700,
    onError = Color.White,
    errorContainer = Red100,
    onErrorContainer = Red900,
    background = BackgroundLight,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = GreyLineSoft,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = ContainerLow,
    surfaceContainer = ContainerDefault,
    surfaceContainerHigh = ContainerHigh,
    surfaceContainerHighest = GreyLineSoft,
    outline = GreyLine,
    outlineVariant = GreyLineSoft,
)

/**
 * Invision's Material 3 theme: light surfaces, emerald accents, dark text and
 * rounded shapes. Dynamic colour is intentionally not used so the brand stays
 * consistent across devices.
 */
@Composable
fun InvisionTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides LightStatusColors) {
        MaterialTheme(
            colorScheme = InvisionLightColors,
            typography = InvisionTypography,
            shapes = InvisionShapes,
            content = content,
        )
    }
}
