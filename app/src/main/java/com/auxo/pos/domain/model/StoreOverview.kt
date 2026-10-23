// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.model

/** A snapshot of the store's activity for the current day, shown on Home. */
data class StoreOverview(
    val storeName: String,
    val salesToday: Money,
    val billsToday: Int,
)
