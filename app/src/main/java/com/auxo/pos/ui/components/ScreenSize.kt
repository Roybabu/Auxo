// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A phone turned on its side is about 384 dp tall; a phone upright is over 800 dp. */
const val SHORT_SCREEN_DP = 480

/**
 * True when the window is short (a phone in landscape). A page with a search box and filters above its list keeps them
 * fixed on a tall screen, but on a short one they would fill the page and leave the list almost no room, so they
 * scroll away with the list instead.
 */
@Composable
fun rememberIsShortScreen(): Boolean = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP

/** The tallest a list inside a dialog may be: [max], but never more than 40% of the window, so a short window still shows the buttons. */
@Composable
fun rememberDialogListMaxHeight(max: Dp): Dp = minOf(max, (LocalConfiguration.current.screenHeightDp * 0.4f).dp)
