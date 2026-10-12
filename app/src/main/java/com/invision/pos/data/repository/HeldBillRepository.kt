package com.invision.pos.data.repository

import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.local.entity.HeldBillEntity
import androidx.room.withTransaction
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.HeldBill
import com.invision.pos.domain.billing.HeldBillSummary
import com.invision.pos.domain.billing.HeldBills
import com.invision.pos.domain.billing.KitchenOrders
import com.invision.pos.domain.billing.PlaceOrderResult
import com.invision.pos.domain.billing.ServiceType
import com.invision.pos.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

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

    /**
     * Sends [cart] to the kitchen as an open restaurant order and keeps it open, under its table (dine-in) or as
     * a takeaway. A ticket lists only what the kitchen has not been told yet (see [KitchenOrders.delta]). The
     * ticket number and the saved order change together or not at all. Stock and the invoice number are not
     * touched: they change only when the order is settled as a normal invoice.
     *
     * Needs a service type, and a table for dine-in. Another open order on the same table is refused.
     */
    suspend fun placeOrder(cart: Cart, total: Money, label: String, replacingId: Long? = null): PlaceOrderResult
}

class RoomHeldBillRepository(private val database: InvisionDatabase, private val clock: Clock) : HeldBillRepository {
    private val dao = database.heldBillDao()
    private val operations = database.operationDao()

    override fun observeHeld(): Flow<List<HeldBillSummary>> = dao.observeAll().map { rows -> rows.map { it.toSummary() } }

    override suspend fun placeOrder(cart: Cart, total: Money, label: String, replacingId: Long?): PlaceOrderResult {
        require(!cart.isEmpty) { "Nothing to order" }
        val service = requireNotNull(cart.serviceType) { "Choose dine-in or takeaway" }
        val table = if (service == ServiceType.DINE_IN) KitchenOrders.normalizeTable(cart.tableNumber) else ""
        require(service != ServiceType.DINE_IN || table.isNotEmpty()) { "Dine-in needs a table" }
        return database.withTransaction {
            val existing = replacingId?.let { dao.get(it) }
            if (table.isNotEmpty()) {
                dao.openOrderForTable(table, existing?.id ?: 0)?.let { return@withTransaction PlaceOrderResult.TableInUse(it.id) }
            }
            val sent = KitchenOrderCodec.decode(existing?.kotSent)
            val delta = KitchenOrders.delta(cart.lines, sent)
            if (delta.isEmpty) return@withTransaction PlaceOrderResult.NothingNew

            val now = clock.millis()
            val day = LocalDate.now(clock)
            val kotNumber = operations.nextNumber("KOT|$day") { "KOT-" + it.toString().padStart(3, '0') }
            val count = (existing?.kotCount ?: 0) + 1
            val entity = HeldBillEntity(
                id = existing?.id ?: 0,
                label = HeldBills.cleanLabel(label),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                itemCount = cart.itemCount,
                totalMinor = total.minorUnits,
                payload = HeldCartCodec.encode(cart),
                serviceType = service.name,
                tableNumber = table.ifEmpty { null },
                kotSent = KitchenOrderCodec.encode(KitchenOrders.sentFrom(cart.lines)),
                kotCount = count,
            )
            val id = if (existing != null) {
                dao.update(entity)
                existing.id
            } else {
                dao.insert(entity)
            }
            PlaceOrderResult.Placed(id, kotNumber, delta, isAddOn = sent.isNotEmpty(), kotCount = count)
        }
    }

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
            // A parked bill keeps whatever the kitchen already knows; only placing an order changes that.
            serviceType = existing?.serviceType,
            tableNumber = existing?.tableNumber,
            kotSent = existing?.kotSent,
            kotCount = existing?.kotCount ?: 0,
        )
        return if (existing != null) {
            dao.update(entity)
            existing.id
        } else {
            dao.insert(entity)
        }
    }

    override suspend fun get(id: Long): HeldBill? {
        val row = dao.get(id) ?: return null
        val cart = HeldCartCodec.decode(row.payload) ?: return null
        return HeldBill(row.toSummary(), cart)
    }

    override suspend fun discard(id: Long) = dao.delete(id)

    private fun HeldBillEntity.toSummary() = HeldBillSummary(
        id = id,
        label = label,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        itemCount = itemCount,
        total = Money(totalMinor),
        serviceType = serviceType?.let { name -> ServiceType.entries.firstOrNull { it.name == name } },
        tableNumber = tableNumber,
        kotCount = kotCount,
    )
}
