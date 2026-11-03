// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import java.security.MessageDigest

/**
 * Stable UUID-shaped IDs for records. The Android database uses numbers, so each ID is derived from the table name
 * and the record's own key. The same record always gets the same ID, so references between records stay correct.
 */
object SharedIds {
    fun derive(table: String, key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("auxo-backup-v1|$table|$key".toByteArray(Charsets.UTF_8))
        val bytes = digest.copyOf(16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
