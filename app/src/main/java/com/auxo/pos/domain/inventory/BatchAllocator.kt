// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.inventory

import kotlin.math.min

/** Units in one batch, listed in the order stock should be taken (earliest expiry first). */
data class BatchStock(val batchId: Long, val quantity: Int)

/** Units taken from, or put back into, one batch. */
data class BatchTake(val batchId: Long, val quantity: Int)

/** Where returned units go; [unplaced] units have no known batch to go back to. */
data class ReturnPlacement(val takes: List<BatchTake>, val unplaced: Int)

/** Decides which batches stock comes out of and goes back into. */
object BatchAllocator {

    /**
     * Takes [quantity] from [batches] in the order given, so the batch that expires
     * first is used first. Returns null if the batches don't hold enough.
     */
    fun allocate(batches: List<BatchStock>, quantity: Int): List<BatchTake>? {
        require(quantity > 0) { "Quantity must be positive" }
        var left = quantity
        val takes = mutableListOf<BatchTake>()
        for (batch in batches) {
            if (left == 0) break
            if (batch.quantity <= 0) continue
            val take = min(left, batch.quantity)
            takes += BatchTake(batch.batchId, take)
            left -= take
        }
        return if (left == 0) takes else null
    }

    /**
     * Puts [quantity] returned units back into the batches a sale took them from.
     * [soldLastFirst] lists those batches with the units the sale took, starting
     * with the batch it took from last; [alreadyReturned] is what earlier returns
     * put back per batch. Units beyond what the sale took from known batches (for
     * example sales made before batches were kept) are left [ReturnPlacement.unplaced].
     */
    fun placeReturn(soldLastFirst: List<BatchTake>, alreadyReturned: Map<Long, Int>, quantity: Int): ReturnPlacement {
        require(quantity > 0) { "Quantity must be positive" }
        var left = quantity
        val takes = mutableListOf<BatchTake>()
        for (sold in soldLastFirst) {
            if (left == 0) break
            val room = sold.quantity - (alreadyReturned[sold.batchId] ?: 0)
            if (room <= 0) continue
            val put = min(left, room)
            takes += BatchTake(sold.batchId, put)
            left -= put
        }
        return ReturnPlacement(takes, left)
    }
}
