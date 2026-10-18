package com.auxo.pos.testutil

import com.auxo.pos.data.repository.HeldBillRepository
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.HeldBill
import com.auxo.pos.domain.billing.HeldBillSummary
import com.auxo.pos.domain.billing.HeldBills
import com.auxo.pos.domain.model.Money
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
        )
        bills.value = bills.value.filterNot { it.summary.id == id } + HeldBill(summary, cart)
        return id
    }

    override suspend fun get(id: Long): HeldBill? = bills.value.firstOrNull { it.summary.id == id }

    override suspend fun discard(id: Long) {
        bills.value = bills.value.filterNot { it.summary.id == id }
    }
}
