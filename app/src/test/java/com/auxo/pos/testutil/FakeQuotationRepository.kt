// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.testutil

import com.auxo.pos.data.repository.QuotationRepository
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.Quotation
import com.auxo.pos.domain.billing.QuotationStatus
import com.auxo.pos.domain.billing.QuotationSummary
import com.auxo.pos.domain.billing.Quotations
import com.auxo.pos.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeQuotationRepository : QuotationRepository {
    private val rows = MutableStateFlow<List<Quotation>>(emptyList())
    private var nextId = 1L
    var failSaves = false

    val all: List<Quotation> get() = rows.value

    override fun observeAll(): Flow<List<QuotationSummary>> = rows.map { list -> list.map { it.summary }.sortedByDescending { it.id } }

    override suspend fun save(cart: Cart, total: Money, validUntil: LocalDate): QuotationSummary {
        if (failSaves) error("disk full")
        val id = nextId++
        val summary = QuotationSummary(
            id = id,
            number = Quotations.number(2026, id),
            createdAtMillis = id,
            validUntil = validUntil,
            customerName = cart.customer.name,
            customerPhone = cart.customer.phone,
            itemCount = cart.itemCount,
            total = total,
            status = QuotationStatus.OPEN,
            convertedInvoiceId = null,
        )
        rows.value = rows.value + Quotation(summary, cart)
        return summary
    }

    override suspend fun get(id: Long): Quotation? = rows.value.firstOrNull { it.summary.id == id }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.summary.id == id }
    }

    override suspend fun markConverted(id: Long, invoiceId: Long): Boolean {
        val row = get(id)?.takeIf { it.summary.status == QuotationStatus.OPEN } ?: return false
        rows.value = rows.value.map { if (it.summary.id == id) row.copy(summary = row.summary.copy(status = QuotationStatus.CONVERTED, convertedInvoiceId = invoiceId)) else it }
        return true
    }
}
