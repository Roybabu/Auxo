// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.data.local.entity.HeldBillEntity
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedMappingCartTest {
    private fun line(variantId: Long = 11, custom: Boolean = false, discount: Discount? = null) = CartLine(
        variantId = variantId, productId = 7, productName = "Shirt", variantLabel = "M", barcode = "890", hsnCode = null,
        unitPrice = Money(10000), mrp = null, gstRate = GstRate.Fixed(500), quantity = 2, availableStock = null,
        discount = discount, discountReason = if (discount == null) "" else "Festive", isCustom = custom,
    )

    private val heldBill = HeldBillEntity(id = 3, label = "Table 2", createdAt = 1, updatedAt = 2, itemCount = 2,
        totalMinor = 20000, payload = "", tableNumber = 2, tokenNumber = null)

    @Test
    fun a_percent_discount_keeps_its_kind_value_and_reason() {
        val snapshot = SharedMappingCarts.cartLine(line(discount = Discount.Percent(500)))
        assertEquals(mapOf("kind" to "PERCENT", "value" to "500", "reason" to "Festive"), snapshot["discount"])
        assertEquals(SharedIds.derive("variant", "11"), snapshot["variantId"])
        assertEquals(500, snapshot["gstRateBasisPoints"])
    }

    @Test
    fun a_custom_item_has_no_product_or_variant_id() {
        val snapshot = SharedMappingCarts.cartLine(line(variantId = -1L shl 40, custom = true))
        assertNull(snapshot["productId"])
        assertNull(snapshot["variantId"])
        assertEquals(true, snapshot["customItem"])
    }

    @Test
    fun held_bill_without_a_customer_has_empty_customer_id_and_no_buyer() {
        val parent = SharedMappingCarts.heldBill(heldBill, Cart(lines = listOf(line())), shopStateCode = "27")
        assertEquals("", parent["customerId"])
        assertNull(parent["buyerName"])
        assertEquals(false, parent["interState"])
        assertEquals("2", parent["tableNumber"].toString())
    }

    @Test
    fun held_bill_is_inter_state_when_place_of_supply_differs_from_shop() {
        val cart = Cart(lines = listOf(line()), placeOfSupplyStateCode = "07", customer = CustomerDetails(name = "Asha", customerId = 5))
        val parent = SharedMappingCarts.heldBill(heldBill, cart, shopStateCode = "27")
        assertEquals(true, parent["interState"])
        assertEquals(SharedIds.derive("customer", "5"), parent["customerId"])
        assertEquals("Asha", parent["buyerName"])
    }

    @Test
    fun held_bill_lines_keep_their_order() {
        val rows = SharedMappingCarts.heldBillLines(3, Cart(lines = listOf(line(variantId = 11), line(variantId = 12))))
        assertEquals(listOf(0, 1), rows.map { it["sortOrder"] })
        assertEquals(SharedIds.derive("heldBill", "3"), rows[0]["heldBillId"])
    }
}
