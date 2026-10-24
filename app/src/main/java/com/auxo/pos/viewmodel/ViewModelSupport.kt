// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.auxo.pos.AuxoApplication
import com.auxo.pos.core.di.AppContainer
import com.auxo.pos.domain.inventory.ItemSearch
import com.auxo.pos.domain.inventory.PickableItem
import com.auxo.pos.domain.model.Product
import java.time.Clock
import java.time.LocalDate

/** The app's dependency container, for use inside ViewModel factory initializers. */
internal fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as AuxoApplication).container

/** Keeps StateFlows alive briefly across configuration changes. */
internal const val STOP_TIMEOUT_MILLIS = 5_000L

/** Start (inclusive) and end (exclusive) instants of [this] range as of today in [clock]'s zone. */
internal fun HistoryRange.bounds(clock: Clock): Pair<Long, Long> {
    val today = LocalDate.now(clock)
    val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
    val startDate = when (this) {
        HistoryRange.TODAY -> today
        HistoryRange.LAST_7_DAYS -> today.minusDays(6)
        HistoryRange.THIS_MONTH -> today.withDayOfMonth(1)
        HistoryRange.ALL -> return 0L to Long.MAX_VALUE
    }
    return startDate.atStartOfDay(clock.zone).toInstant().toEpochMilli() to end
}

/** A search box for adding stock items to an order or receipt. */
data class ItemPickerState(
    val query: String = "",
    val results: List<PickableItem> = emptyList(),
)

/** Matches for [query] among [products], or nothing while the query is blank. */
internal fun pickItems(products: List<Product>, query: String, include: (Product) -> Boolean): List<PickableItem> =
    if (query.isBlank()) emptyList() else ItemSearch.search(products, query, include, limit = PICKER_LIMIT)

private const val PICKER_LIMIT = 12
