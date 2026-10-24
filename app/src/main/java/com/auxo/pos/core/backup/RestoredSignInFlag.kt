// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers that a backup was restored from the first screen, before any account existed. Backups do not hold
 * sign-in details, so the app next asks only for a new sign-in and keeps the restored shop's data. Cleared once
 * someone has signed in.
 */
class RestoredSignInFlag(context: Context) {
    private val prefs = context.getSharedPreferences("auxo_restored_sign_in", Context.MODE_PRIVATE)

    fun isSet(): Boolean = prefs.getBoolean(KEY, false)

    // Written straight to disk: the app is killed right after a restore, which would lose a delayed write.
    fun set() = prefs.edit(commit = true) { putBoolean(KEY, true) }

    fun clear() = prefs.edit { remove(KEY) }

    private companion object {
        const val KEY = "awaiting_sign_in"
    }
}
