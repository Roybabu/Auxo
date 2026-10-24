// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.components

import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Like [painterResource], but a picture file (WebP, PNG) is decoded off the main thread, so a screen with many pictures
 * (the setup pages) does not make the first frames skip. Nothing is drawn until the picture is ready. Vector drawables
 * are cheap and load as usual.
 */
@Composable
fun rememberBackgroundPainter(@DrawableRes id: Int): Painter {
    val resources = LocalContext.current.resources
    val isPicture = remember(id) {
        val value = TypedValue()
        resources.getValue(id, value, true)
        value.string?.endsWith(".xml") != true
    }
    if (!isPicture) return painterResource(id)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, id) {
        value = withContext(Dispatchers.Default) { ImageBitmap.imageResource(resources, id) }
    }
    return bitmap?.let { BitmapPainter(it) } ?: ColorPainter(Color.Transparent)
}
