// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.usecase

import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.StockChangeResult
import com.auxo.pos.data.repository.StockCountRepository
import com.auxo.pos.domain.inventory.CountReason
import com.auxo.pos.domain.inventory.StockCount
import com.auxo.pos.domain.inventory.StockCounts
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import kotlinx.coroutines.flow.first
import java.time.Clock

sealed interface PostCountResult {
    /** Every difference was applied and the count is closed. [adjusted] items changed; the rest matched. */
    data class Posted(val adjusted: Int, val matched: Int) : PostCountResult

    /**
     * Some differences were applied and are out of the count; [failed] items could not be (their stock fell
     * below the counted difference since they were counted). The count stays open with just those items.
     */
    data class Partial(val adjusted: Int, val failed: List<Long>) : PostCountResult

    /** The count was already closed or cancelled. */
    data object NotOpen : PostCountResult

    data object Failed : PostCountResult
}

/**
 * Posts a stock count: each difference becomes a CORRECTION stock adjustment (the counted minus the expected
 * quantity, applied to the stock as it is now, so sales made while counting are kept). Nothing changes before
 * this runs. Posting is safe to repeat: every adjustment carries a request id, so none is applied twice.
 */
class PostStockCountUseCase(
    private val inventory: InventoryRepository,
    private val counts: StockCountRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(count: StockCount, reason: CountReason, note: String): PostCountResult {
        // A count that was already posted or cancelled (a second tap, a retry) must not adjust anything again.
        if (counts.observeOpen().first()?.id != count.id) return PostCountResult.NotOpen
        val text = StockCounts.movementNote(reason, note)
        var adjusted = 0
        var matched = 0
        val failed = mutableListOf<Long>()
        for (line in count.lines) {
            if (line.difference == 0) {
                matched++
                continue
            }
            val result = try {
                inventory.recordStockChange(
                    StockMovement(
                        variantId = line.variantId,
                        quantityChange = line.difference,
                        reason = StockChangeReason.CORRECTION,
                        note = text,
                        createdAtMillis = clock.millis(),
                    ),
                    requestId = StockCounts.requestId(count.id, line.variantId),
                )
            } catch (e: java.io.IOException) {
                return PostCountResult.Failed
            }
            when (result) {
                StockChangeResult.Applied -> {
                    adjusted++
                    if (!counts.removeLine(count.id, line.variantId)) return PostCountResult.NotOpen
                }
                StockChangeResult.WouldGoNegative -> failed += line.variantId
                // The product was deleted since it was counted: nothing to adjust.
                StockChangeResult.ItemMissing -> counts.removeLine(count.id, line.variantId)
            }
        }
        if (failed.isNotEmpty()) return PostCountResult.Partial(adjusted, failed)
        return if (counts.markPosted(count.id, reason, note, adjusted)) PostCountResult.Posted(adjusted, matched) else PostCountResult.NotOpen
    }
}
