package com.invision.pos.data.repository

import androidx.room.withTransaction
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.local.entity.StockCountEntity
import com.invision.pos.domain.inventory.CountLine
import com.invision.pos.domain.inventory.CountReason
import com.invision.pos.domain.inventory.StockCount
import com.invision.pos.domain.inventory.StockCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * Stock counts. Saving a line, removing one, cancelling or posting a count never touches stock by itself: stock
 * changes only through the adjustments the count is posted as (see PostStockCountUseCase). There is at most one
 * open count at a time.
 */
interface StockCountRepository {
    /** The count in progress, or null. */
    fun observeOpen(): Flow<StockCount?>

    /** The count in progress, or a new one if there is none. */
    suspend fun start(): StockCount

    /** Adds or replaces the counted line for one item. Returns false if the count is no longer open. */
    suspend fun setLine(countId: Long, line: CountLine): Boolean

    /** Takes one item out of the count. Returns false if the count is no longer open. */
    suspend fun removeLine(countId: Long, variantId: Long): Boolean

    /** Throws the count away without changing any stock. */
    suspend fun cancel(countId: Long)

    /** Closes the count after its differences were posted. Returns false if it was already closed. */
    suspend fun markPosted(countId: Long, reason: CountReason, note: String, adjusted: Int): Boolean
}

class RoomStockCountRepository(private val database: InvisionDatabase, private val clock: Clock) : StockCountRepository {
    private val dao = database.stockCountDao()

    override fun observeOpen(): Flow<StockCount?> = dao.observeOpen().map { it?.toDomain() }

    override suspend fun start(): StockCount = database.withTransaction {
        val open = dao.getOpen()
        if (open != null) {
            open.toDomain()
        } else {
            val now = clock.millis()
            val id = dao.insert(StockCountEntity(startedAt = now, status = "OPEN", payload = StockCounts.encode(emptyList())))
            StockCount(id, now, emptyList())
        }
    }

    override suspend fun setLine(countId: Long, line: CountLine): Boolean = database.withTransaction {
        val entity = dao.get(countId)?.takeIf { it.status == "OPEN" } ?: return@withTransaction false
        val lines = StockCounts.withLine(StockCounts.decode(entity.payload), line)
        dao.updatePayload(countId, StockCounts.encode(lines)) > 0
    }

    override suspend fun removeLine(countId: Long, variantId: Long): Boolean = database.withTransaction {
        val entity = dao.get(countId)?.takeIf { it.status == "OPEN" } ?: return@withTransaction false
        val lines = StockCounts.decode(entity.payload).filterNot { it.variantId == variantId }
        dao.updatePayload(countId, StockCounts.encode(lines)) > 0
    }

    override suspend fun cancel(countId: Long) {
        dao.cancel(countId, clock.millis())
    }

    override suspend fun markPosted(countId: Long, reason: CountReason, note: String, adjusted: Int): Boolean =
        dao.markPosted(countId, clock.millis(), reason.name, note.trim().take(StockCounts.NOTE_MAX).ifEmpty { null }, adjusted) > 0

    private fun StockCountEntity.toDomain() = StockCount(id, startedAt, StockCounts.decode(payload))
}
