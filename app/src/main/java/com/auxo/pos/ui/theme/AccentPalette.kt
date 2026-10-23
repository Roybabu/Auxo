// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.auxo.pos.core.appearance.AppAccent

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

internal fun AppAccent.hueAndSaturation(): Pair<Float, Float> = hue().let { it.hue to it.saturation }

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

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val h = ((hue % 360f) + 360f) % 360f
    val chroma = (1f - kotlin.math.abs(2f * l - 1f)) * s
    fun channel(offset: Float): Float {
        val k = (offset + h / 30f) % 12f
        return l - chroma / 2f * (minOf(k - 3f, 9f - k, 1f)).coerceIn(-1f, 1f)
    }
    return Color(channel(0f).coerceIn(0f, 1f), channel(8f).coerceIn(0f, 1f), channel(4f).coerceIn(0f, 1f))
}

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

/**
 * The colours the soft-render icons are drawn with, one set per accent. [bright], [mid] and [deep] run from the lit top
 * of a glyph to its shaded bottom; the mint ones are the pale tile behind it in the light theme, and the dark ones are
 * the tile and glyph in the dark theme.
 */
@Immutable
data class IconColors(
    val bright: Color,
    val mid: Color,
    val deep: Color,
    val mint: Color,
    val mintLight: Color,
    val mintEdge: Color,
    val darkTileTop: Color,
    val darkTileBottom: Color,
    val darkEdge: Color,
    val darkGlyphTop: Color,
    val darkGlyphBottom: Color,
)

/** Emerald keeps the exact colours the icons always had; every other accent is the same shape of palette in its own hue. */
fun AccentPalette.icons(accent: AppAccent): IconColors {
    if (accent == AppAccent.EMERALD) {
        return IconColors(
            bright = Color(0xFF10A56F), mid = Color(0xFF0B7A55), deep = Color(0xFF064E3B),
            mint = Color(0xFFD9F3E7), mintLight = Color(0xFFF0FBF6), mintEdge = Color(0xFFBFE4CD),
            darkTileTop = Color(0xFF14503E), darkTileBottom = Color(0xFF0B3B2D), darkEdge = Color(0xFF1B6B53),
            darkGlyphTop = Color(0xFFA7F3D0), darkGlyphBottom = Color(0xFF34D399),
        )
    }
    val (h, s) = accent.hueAndSaturation()
    // Emerald's own saturation is 0.9, so every strength below is that palette's value scaled to this accent.
    val f = s / 0.9f
    return IconColors(
        bright = hsl(h, 0.82f * f, 0.355f), mid = hsl(h, 0.84f * f, 0.26f), deep = hsl(h, 0.85f * f, 0.165f),
        mint = hsl(h, 0.50f * f, 0.90f), mintLight = hsl(h, 0.48f * f, 0.955f), mintEdge = hsl(h, 0.40f * f, 0.82f),
        darkTileTop = hsl(h, 0.60f * f, 0.196f), darkTileBottom = hsl(h, 0.69f * f, 0.137f), darkEdge = hsl(h, 0.60f * f, 0.263f),
        darkGlyphTop = hsl(h, 0.87f * f, 0.80f), darkGlyphBottom = hsl(h, 0.65f * f, 0.52f),
    )
}
