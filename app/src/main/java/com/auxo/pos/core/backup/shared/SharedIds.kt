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

    /**
     * Fixed UUIDs for the built-in product categories, the same as the web app uses (src/domain/sharedSpec.ts),
     * so a built-in category has the same ID in every backup.
     */
    private val builtInCategory = mapOf(
        "GARMENT" to "21698313-7bd8-56ea-9c69-9ebe0fc84a82",
        "FOOTWEAR" to "326c2ddf-a1a0-5d1e-91eb-ddb8cb003a87",
        "ACCESSORIES" to "890d9f9a-c20c-5a59-aa2c-303f77300787",
        "PACKAGED_FOOD" to "4fc4cfed-fae8-550c-a895-7f52e5e29c08",
        "BEVERAGE" to "2a47ef63-65f0-54e1-8f27-934d8dc7a4a5",
        "GENERAL" to "828e7b4d-9fac-5892-ad9d-726e22c55973",
        "ELECTRONICS" to "12f7da24-108e-5afa-8d7d-016d30d75a3d",
        "PHARMACY" to "cb054182-c54d-53c8-940f-0dab4bb99460",
        "COSMETICS" to "9a48869d-c8c3-5d01-99c7-0fb9ee73f78a",
        "LOOSE" to "fb0db978-456f-5fa1-b083-43b002189187",
        "JEWELLERY" to "d327dc5b-352a-57d1-846f-f62cc826463b",
        "RESTAURANT" to "27533c16-dbd0-5a7f-b95a-cddc6e984278",
    )

    /** The shared ID for a category: the fixed ID for a built-in category, otherwise a derived one. */
    fun category(profile: String, builtIn: Boolean, id: Long): String =
        if (builtIn) builtInCategory[profile] ?: derive("category", id.toString()) else derive("category", id.toString())
}
