package com.invision.pos.testutil

import com.invision.pos.data.repository.HeldBillRepository
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.HeldBill
import com.invision.pos.domain.billing.HeldBillSummary
import com.invision.pos.domain.billing.HeldBills
import com.invision.pos.domain.billing.KitchenOrders
import com.invision.pos.domain.billing.PlaceOrderResult
import com.invision.pos.domain.billing.SentItem
import com.invision.pos.domain.billing.ServiceType
import com.invision.pos.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHeldBillRepository : HeldBillRepository {
    private val bills = MutableStateFlow<List<HeldBill>>(emptyList())
    private var nextId = 1L
    var failHolds = false
    private var tick = 0L

    val all: List<HeldBill> get() = bills.value

    override fun observeHeld(): Flow<List<HeldBillSummary>> = bills.map { list -> list.map { it.summary }.sortedByDescending { it.updatedAtMillis } }

    override suspend fun hold(cart: Cart, total: Money, label: String, replacingId: Long?): Long {
        if (failHolds) error("disk full")
        val existing = bills.value.firstOrNull { it.summary.id == replacingId }
        val id = existing?.summary?.id ?: nextId++
        val summary = HeldBillSummary(
            id = id,
            label = HeldBills.cleanLabel(label),
            createdAtMillis = existing?.summary?.createdAtMillis ?: ++tick,
            updatedAtMillis = ++tick,
            itemCount = cart.itemCount,
            total = total,
            serviceType = existing?.summary?.serviceType,
            tableNumber = existing?.summary?.tableNumber,
            kotCount = existing?.summary?.kotCount ?: 0,
        )
        bills.value = bills.value.filterNot { it.summary.id == id } + HeldBill(summary, cart)
        return id
    }

    private val sentByOrder = mutableMapOf<Long, List<SentItem>>()
    private var kotNumber = 0

    override suspend fun placeOrder(cart: Cart, total: Money, label: String, replacingId: Long?): PlaceOrderResult {
        if (failHolds) error("disk full")
        val service = requireNotNull(cart.serviceType)
        val table = if (service == ServiceType.DINE_IN) KitchenOrders.normalizeTable(cart.tableNumber) else ""
        require(service != ServiceType.DINE_IN || table.isNotEmpty())
        val existing = bills.value.firstOrNull { it.summary.id == replacingId }
        if (table.isNotEmpty()) {
            bills.value.firstOrNull { it.summary.tableNumber == table && it.summary.id != existing?.summary?.id }
                ?.let { return PlaceOrderResult.TableInUse(it.summary.id) }
        }
        val sent = existing?.let { sentByOrder[it.summary.id] }.orEmpty()
        val delta = KitchenOrders.delta(cart.lines, sent)
        if (delta.isEmpty) return PlaceOrderResult.NothingNew
        val id = existing?.summary?.id ?: nextId++
        val count = (existing?.summary?.kotCount ?: 0) + 1
        val summary = HeldBillSummary(
            id = id,
            label = HeldBills.cleanLabel(label),
            createdAtMillis = existing?.summary?.createdAtMillis ?: ++tick,
            updatedAtMillis = ++tick,
            itemCount = cart.itemCount,
            total = total,
            serviceType = service,
            tableNumber = table.ifEmpty { null },
            kotCount = count,
        )
        bills.value = bills.value.filterNot { it.summary.id == id } + HeldBill(summary, cart)
        sentByOrder[id] = KitchenOrders.sentFrom(cart.lines)
        return PlaceOrderResult.Placed(id, "KOT-" + (++kotNumber).toString().padStart(3, '0'), delta, isAddOn = sent.isNotEmpty(), kotCount = count)
    }

    override suspend fun get(id: Long): HeldBill? = bills.value.firstOrNull { it.summary.id == id }

    override suspend fun discard(id: Long) {
        bills.value = bills.value.filterNot { it.summary.id == id }
    }
}
