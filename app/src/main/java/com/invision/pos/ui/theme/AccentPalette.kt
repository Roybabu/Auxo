package com.invision.pos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.hsl
import com.invision.pos.core.appearance.AppAccent

/** Colours of the Home screen header, which follows the chosen accent. */
@Immutable
data class HomeColors(
    /** Background behind the greeting, balances and quick actions. */
    val header: Color,
    /** The balance cards, one step away from the header. */
    val card: Color,
    val avatar: Color,
    val avatarIcon: Color,
    /** Circles behind the quick action icons. */
    val actionCircle: Color,
    val actionIcon: Color,
    val badge: Color,
)

/** A hue and how strong its colour is. Lightness is chosen per role below, not per accent. */
private data class Hue(val hue: Float, val saturation: Float)

private fun AppAccent.hue(): Hue = when (this) {
    AppAccent.EMERALD -> Hue(160f, 0.90f)
    AppAccent.TEAL -> Hue(188f, 0.85f)
    AppAccent.OCEAN -> Hue(216f, 0.85f)
    AppAccent.VIOLET -> Hue(262f, 0.65f)
    AppAccent.ORCHID -> Hue(305f, 0.60f)
    AppAccent.ROSE -> Hue(346f, 0.78f)
    AppAccent.SUNSET -> Hue(22f, 0.90f)
    AppAccent.SLATE -> Hue(215f, 0.22f)
}

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color =
    Color.hsl(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))

/**
 * Builds the colours for an accent. The shapes of the two schemes follow Material 3: a strong
 * primary with readable text on it, a soft container for selected things, and neutral surfaces
 * that carry only a hint of the hue. Light uses white surfaces; dark uses near-black ones.
 * Emerald, the original colour, keeps its exact original values.
 */
object AccentPalette {

    /** The colour shown on the picker for [accent]: a brighter tone of the same hue, so the eight are easy to tell apart. */
    fun swatch(accent: AppAccent): Color = accent.hue().let { (h, s) -> hsl(h, s * 0.9f, 0.42f) }

    fun scheme(accent: AppAccent, dark: Boolean): ColorScheme =
        if (dark) darkScheme(accent) else lightScheme(accent)

    fun home(accent: AppAccent, dark: Boolean): HomeColors {
        if (accent == AppAccent.EMERALD && !dark) {
            return HomeColors(HomeGreen, HomeGreenDark, HomeAvatar, HomeGreenDark, Color.White, HomeGreenDark, HomeBadge)
        }
        val (h, s) = accent.hue()
        val quiet = s * 0.45f
        return if (dark) {
            HomeColors(
                header = hsl(h, quiet, 0.17f),
                card = hsl(h, quiet, 0.12f),
                avatar = hsl(h, quiet, 0.30f),
                avatarIcon = hsl(h, s, 0.85f),
                actionCircle = hsl(h, 0.08f, 0.08f),
                actionIcon = hsl(h, s, 0.74f),
                badge = HomeBadge,
            )
        } else {
            HomeColors(
                header = hsl(h, quiet, 0.36f),
                card = hsl(h, quiet, 0.29f),
                avatar = hsl(h, s * 0.6f, 0.82f),
                avatarIcon = hsl(h, quiet, 0.29f),
                actionCircle = Color.White,
                actionIcon = hsl(h, quiet, 0.29f),
                badge = HomeBadge,
            )
        }
    }

    private fun lightScheme(accent: AppAccent): ColorScheme {
        if (accent == AppAccent.EMERALD) return EmeraldLight
        val (h, s) = accent.hue()
        val container = hsl(h, s * 0.85f, 0.90f)
        val onContainer = hsl(h, s, 0.14f)
        return lightColorScheme(
            primary = hsl(h, s, 0.28f),
            onPrimary = Color.White,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = hsl(h, s * 0.25f, 0.36f),
            onSecondary = Color.White,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = Amber700,
            onTertiary = Color.White,
            tertiaryContainer = Amber100,
            onTertiaryContainer = Amber900,
            error = Red700,
            onError = Color.White,
            errorContainer = Red100,
            onErrorContainer = Red900,
            background = hsl(h, 0.15f, 0.975f),
            onBackground = Ink,
            surface = SurfaceWhite,
            onSurface = Ink,
            surfaceVariant = hsl(h, 0.14f, 0.90f),
            onSurfaceVariant = hsl(h, 0.10f, 0.32f),
            surfaceContainerLowest = SurfaceWhite,
            surfaceContainerLow = hsl(h, 0.15f, 0.955f),
            surfaceContainer = hsl(h, 0.14f, 0.94f),
            surfaceContainerHigh = hsl(h, 0.13f, 0.915f),
            surfaceContainerHighest = hsl(h, 0.14f, 0.90f),
            outline = hsl(h, 0.10f, 0.78f),
            outlineVariant = hsl(h, 0.14f, 0.90f),
        )
    }

    private fun darkScheme(accent: AppAccent): ColorScheme {
        val (h, s) = accent.hue()
        val container = hsl(h, s * 0.75f, 0.20f)
        val onContainer = hsl(h, s, 0.90f)
        return darkColorScheme(
            primary = hsl(h, s, 0.72f),
            onPrimary = hsl(h, s, 0.12f),
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = hsl(h, s * 0.35f, 0.76f),
            onSecondary = hsl(h, s * 0.35f, 0.14f),
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = Color(0xFFF2B35A),
            onTertiary = Color(0xFF3F2500),
            tertiaryContainer = Color(0xFF5C3A00),
            onTertiaryContainer = Color(0xFFFFDDB0),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = hsl(h, 0.06f, 0.06f),
            onBackground = hsl(h, 0.05f, 0.91f),
            surface = hsl(h, 0.06f, 0.075f),
            onSurface = hsl(h, 0.05f, 0.91f),
            surfaceVariant = hsl(h, 0.07f, 0.18f),
            onSurfaceVariant = hsl(h, 0.08f, 0.72f),
            surfaceContainerLowest = hsl(h, 0.06f, 0.04f),
            surfaceContainerLow = hsl(h, 0.06f, 0.09f),
            surfaceContainer = hsl(h, 0.06f, 0.11f),
            surfaceContainerHigh = hsl(h, 0.06f, 0.14f),
            surfaceContainerHighest = hsl(h, 0.06f, 0.17f),
            outline = hsl(h, 0.06f, 0.42f),
            outlineVariant = hsl(h, 0.06f, 0.24f),
        )
    }

    /** The original green scheme, unchanged. */
    private val EmeraldLight: ColorScheme = lightColorScheme(
        primary = Emerald700,
        onPrimary = Color.White,
        primaryContainer = Emerald100,
        onPrimaryContainer = Emerald900,
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
}
