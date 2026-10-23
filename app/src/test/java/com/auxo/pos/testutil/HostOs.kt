// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.testutil

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** True when the tests run on a Windows computer; the phone itself is never Windows. */
fun isWindowsHost(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/**
 * How a test makes the address other apps open a shared file from. androidx's FileProvider only matches a file to a
 * folder when the path continues with "/", and Windows writes file paths with "\", so on Windows it cannot be used and a
 * fixed-shape address is made instead. Everywhere else the real FileProvider is used.
 */
fun testFileUri(context: Context): (File) -> Uri =
    if (isWindowsHost()) {
        { file -> Uri.parse("content://${context.packageName}.fileprovider/test/${file.name}") }
    } else {
        { file -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
    }
