package com.auxo.pos.data.repository

import com.auxo.pos.core.debuglog.DebugLog
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.entity.HeldBillEntity
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.HeldBill
import com.auxo.pos.domain.billing.HeldBillSummary
import com.auxo.pos.domain.billing.HeldBills
import com.auxo.pos.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * Bills parked at the counter. Parking changes nothing else: no stock is reserved, no invoice number is
 * used and no account is touched. A held bill stays until it is finished or discarded.
 */
interface HeldBillRepository {
    /** Newest first. A bill that cannot be read any more (damaged) is still listed so it can be discarded; [get] returns null for it. */
    fun observeHeld(): Flow<List<HeldBillSummary>>

    /**
     * Parks [cart]. With [replacingId] the bill already parked under that id is updated (a resumed bill held
     * again); if it is gone a new one is made. Returns the id.
     */
    suspend fun hold(cart: Cart, total: Money, label: String, replacingId: Long? = null): Long

    /** The parked bill, or null if it was finished or discarded meanwhile (or cannot be read). */
    suspend fun get(id: Long): HeldBill?

    suspend fun discard(id: Long)
}

class RoomHeldBillRepository(private val database: AuxoDatabase, private val clock: Clock) : HeldBillRepository {
    private val dao = database.heldBillDao()

    override fun observeHeld(): Flow<List<HeldBillSummary>> = dao.observeAll().map { rows -> rows.map { it.toSummary() } }

    override suspend fun hold(cart: Cart, total: Money, label: String, replacingId: Long?): Long {
        require(!cart.isEmpty) { "Nothing to hold" }
        val now = clock.millis()
        val existing = replacingId?.let { dao.get(it) }
        val entity = HeldBillEntity(
            id = existing?.id ?: 0,
            label = HeldBills.cleanLabel(label),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            itemCount = cart.itemCount,
            totalMinor = total.minorUnits,
            payload = HeldCartCodec.encode(cart),
        )
        return if (existing != null) {
            dao.update(entity)
            DebugLog.event("HeldBill", "Held bill ${existing.id} updated: ${cart.itemCount} items")
            existing.id
        } else {
            dao.insert(entity).also { DebugLog.event("HeldBill", "Bill held as $it: ${cart.itemCount} items") }
        }
    }

    override suspend fun get(id: Long): HeldBill? {
        val row = dao.get(id) ?: return null
        val cart = HeldCartCodec.decode(row.payload) ?: run {
            DebugLog.error("HeldBill", "Held bill $id could not be read back (damaged data)")
            return null
        }
        return HeldBill(row.toSummary(), cart)
    }

    override suspend fun discard(id: Long) {
        dao.delete(id)
        DebugLog.event("HeldBill", "Held bill $id discarded")
    }

    private fun HeldBillEntity.toSummary() = HeldBillSummary(
        id = id,
        label = label,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        itemCount = itemCount,
        total = Money(totalMinor),
    )
}
