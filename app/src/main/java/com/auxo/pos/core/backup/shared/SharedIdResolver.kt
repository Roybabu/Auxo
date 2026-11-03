// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

/**
 * The shared ID of each record. A record restored from a shared backup keeps the ID it had there, which is kept as a
 * stored link; any other record gets the ID derived from its table and its local number.
 */
class SharedIdResolver(private val linked: Map<String, Map<Long, String>> = emptyMap()) {
    /** The shared ID of the record with local number [id] in [table]. */
    fun of(table: String, id: Long): String = linked[table]?.get(id) ?: SharedIds.derive(table, id.toString())

    /** The shared ID of a product category: fixed for a built-in one, otherwise as for [of]. */
    fun category(profile: String, builtIn: Boolean, id: Long): String =
        if (builtIn) SharedIds.category(profile, true, id) else of("category", id)
}
