// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.auxo.pos.core.appearance.AppAccent

/** True while the dark theme is showing, for things MaterialTheme has no colour for. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The Home header colours for the chosen accent. */
val LocalHomeColors = staticCompositionLocalOf { AccentPalette.home(AppAccent.DEFAULT, dark = false) }

/** The colours of the soft-render icons for the chosen accent. */
val LocalIconColors = staticCompositionLocalOf { AccentPalette.icons(AppAccent.DEFAULT) }

/**
 * Auxo's Material 3 theme: rounded shapes and one of eight accent colours, in a light
 * (white surfaces) or dark (near-black surfaces) version. Dynamic colour is intentionally not
 * used so the brand stays consistent across devices. The defaults are the original green,
 * light look.
 */
@Composable
fun AuxoTheme(
    accent: AppAccent = AppAccent.DEFAULT,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = remember(accent, darkTheme) { AccentPalette.scheme(accent, darkTheme) }
    val home = remember(accent, darkTheme) { AccentPalette.home(accent, darkTheme) }
    val icons = remember(accent) { AccentPalette.icons(accent) }
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalDarkTheme provides darkTheme,
        LocalHomeColors provides home,
        LocalIconColors provides icons,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AuxoTypography,
            shapes = AuxoShapes,
            content = content,
        )
    }
}
