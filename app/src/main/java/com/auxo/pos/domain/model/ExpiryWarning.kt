// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.model

/** The choices for how early stock is flagged as "expiring soon". */
object ExpiryWarning {
    const val DEFAULT_DAYS = 30
    val CHOICES: List<Int> = listOf(7, 15, 30)

    /** A saved value that is not one of the [CHOICES] (an old backup, say) falls back to [DEFAULT_DAYS]. */
    fun coerce(days: Int): Int = if (days in CHOICES) days else DEFAULT_DAYS
}
