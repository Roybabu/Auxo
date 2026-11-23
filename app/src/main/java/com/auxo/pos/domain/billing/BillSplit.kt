// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.Rounding
import com.auxo.pos.domain.model.StockUnit

/** Why a bill could not be split by item. */
enum class SplitError {
    /** No item was chosen. */
    NOTHING_CHOSEN,

    /** Every item was chosen, so nothing would stay on this bill. */
    NOTHING_LEFT,

    /** A discount on the whole bill cannot be shared between two bills; it is removed first. */
    BILL_DISCOUNT,

    /** Loyalty points are being used on this bill; they are taken off first. */
    POINTS,

    /** A line, or a quantity, that is not on the bill. */
    INVALID,
}

sealed interface SplitOutcome {
    /** [remaining] stays on the counter; [moved] is parked as its own bill. */
    data class Ok(val remaining: Cart, val moved: Cart) : SplitOutcome

    data class Failed(val error: SplitError) : SplitOutcome
}

/** Splitting one bill between several people: by item, or into equal shares. */
object BillSplit {
    const val MIN_PEOPLE = 2
    const val MAX_PEOPLE = 20

    /**
     * [total] divided between [people], to the paisa. When it does not divide evenly the first people pay one paisa
     * more, so the shares always add up to exactly [total].
     */
    fun equalShares(total: Money, people: Int): List<Money> {
        val count = people.coerceIn(MIN_PEOPLE, MAX_PEOPLE)
        val base = total.minorUnits / count
        val extra = (total.minorUnits % count).toInt()
        return List(count) { index -> Money(base + if (index < extra) 1 else 0) }
    }

    /**
     * Takes [quantities] (units per line, by variant id; lines not listed or listed as 0 stay) off [cart] into a new
     * cart for the same customer. A line sold by weight or volume moves whole or not at all. A fixed-amount
     * discount on a line is shared by quantity; a percentage discount goes with the units. The bill-level discount and
     * loyalty points are not shared, so a bill that has either one cannot be split.
     */
    fun splitOff(cart: Cart, quantities: Map<Long, Int>): SplitOutcome {
        if (cart.billDiscount != null) return SplitOutcome.Failed(SplitError.BILL_DISCOUNT)
        if (cart.loyalty != null) return SplitOutcome.Failed(SplitError.POINTS)
        val byId = cart.lines.associateBy { it.variantId }
        val chosen = quantities.filterValues { it > 0 }
        if (chosen.isEmpty()) return SplitOutcome.Failed(SplitError.NOTHING_CHOSEN)
        for ((id, wanted) in chosen) {
            val line = byId[id] ?: return SplitOutcome.Failed(SplitError.INVALID)
            if (wanted > line.quantity) return SplitOutcome.Failed(SplitError.INVALID)
            if (line.stockUnit != StockUnit.UNIT && wanted != line.quantity) return SplitOutcome.Failed(SplitError.INVALID)
        }
        val staying = mutableListOf<CartLine>()
        val leaving = mutableListOf<CartLine>()
        for (line in cart.lines) {
            val wanted = chosen[line.variantId] ?: 0
            when {
                wanted == 0 -> staying += line
                wanted == line.quantity -> leaving += line
                else -> {
                    val (rest, part) = partOf(line, wanted)
                    staying += rest
                    leaving += part
                }
            }
        }
        if (staying.isEmpty()) return SplitOutcome.Failed(SplitError.NOTHING_LEFT)
        return SplitOutcome.Ok(
            remaining = cart.copy(lines = staying),
            moved = cart.copy(lines = leaving, billDiscount = null, billDiscountReason = "", loyalty = null),
        )
    }

    /** [line] cut into what stays and the [units] that leave, with a fixed-amount discount shared by quantity. */
    private fun partOf(line: CartLine, units: Int): Pair<CartLine, CartLine> {
        val discount = line.discount
        if (discount !is Discount.Amount) {
            return line.copy(quantity = line.quantity - units) to line.copy(quantity = units)
        }
        val movedShare = Money(Rounding.halfUp(discount.value.minorUnits * units, line.quantity.toLong()))
        val restShare = discount.value - movedShare
        return line.copy(quantity = line.quantity - units, discount = restShare.takeIf { it > Money.ZERO }?.let { Discount.Amount(it) }) to
            line.copy(quantity = units, discount = movedShare.takeIf { it > Money.ZERO }?.let { Discount.Amount(it) })
    }
}
