// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.auxo.pos.ui.LocalProductPhotos

/**
 * A product's photo, or [fallback] (usually the category icon) when it has none, the file is missing, or no photo
 * store is provided (as in previews and tests). [maxSidePx] is the size the picture is loaded at, so a list of
 * small pictures does not hold large ones in memory.
 */
@Composable
fun ProductPhoto(
    photoFile: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxSidePx: Int = 200,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    val store = LocalProductPhotos.current
    if (photoFile == null || store == null) {
        fallback()
        return
    }
    val bitmap by produceState<Bitmap?>(initialValue = remember(photoFile, maxSidePx) { store.cachedThumbnail(photoFile, maxSidePx) }, photoFile, maxSidePx, store) {
        value = store.thumbnail(photoFile, maxSidePx)
    }
    val loaded = bitmap
    if (loaded == null) {
        fallback()
    } else {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
