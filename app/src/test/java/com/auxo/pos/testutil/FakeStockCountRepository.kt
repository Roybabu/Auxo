// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.testutil

import com.auxo.pos.data.repository.StockCountRepository
import com.auxo.pos.domain.inventory.CountLine
import com.auxo.pos.domain.inventory.CountReason
import com.auxo.pos.domain.inventory.StockCount
import com.auxo.pos.domain.inventory.StockCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStockCountRepository : StockCountRepository {
    private val open = MutableStateFlow<StockCount?>(null)
    private var nextId = 1L
    var failWrites = false

    /** Every count that was posted: id to (reason, note, adjusted). */
    val posted = mutableMapOf<Long, Triple<CountReason, String, Int>>()
    val cancelled = mutableListOf<Long>()

    val current: StockCount? get() = open.value

    override fun observeOpen(): Flow<StockCount?> = open

    override suspend fun start(): StockCount {
        if (failWrites) error("disk full")
        return open.value ?: StockCount(nextId++, TestData.clock.millis(), emptyList()).also { open.value = it }
    }

    override suspend fun setLine(countId: Long, line: CountLine): Boolean {
        if (failWrites) error("disk full")
        val count = open.value?.takeIf { it.id == countId } ?: return false
        open.value = count.copy(lines = StockCounts.withLine(count.lines, line))
        return true
    }

    override suspend fun removeLine(countId: Long, variantId: Long): Boolean {
        val count = open.value?.takeIf { it.id == countId } ?: return false
        open.value = count.copy(lines = count.lines.filterNot { it.variantId == variantId })
        return true
    }

    override suspend fun cancel(countId: Long) {
        if (open.value?.id == countId) {
            cancelled += countId
            open.value = null
        }
    }

    override suspend fun markPosted(countId: Long, reason: CountReason, note: String, adjusted: Int): Boolean {
        if (open.value?.id != countId) return false
        posted[countId] = Triple(reason, note, adjusted)
        open.value = null
        return true
    }
}
