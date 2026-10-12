package com.invision.pos.domain.billing

import com.invision.pos.domain.model.GstRate
import com.invision.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenOrdersTest {

    private fun line(key: Long, name: String, qty: Int, variant: String = "") = CartLine(
        variantId = key, productId = 0, productName = name, variantLabel = variant, barcode = "", hsnCode = null,
        unitPrice = Money(100_00), mrp = null, gstRate = GstRate.Fixed(500), quantity = qty,
        availableStock = CartLine.UNLIMITED, menuItemId = -key,
    )

    @Test
    fun tableNumbers_areKeptTidy() {
        assertEquals("T4", KitchenOrders.normalizeTable("  t4 "))
        assertEquals("A 2", KitchenOrders.normalizeTable("a   2"))
        assertEquals(KitchenOrders.TABLE_MAX, KitchenOrders.normalizeTable("x".repeat(40)).length)
        assertEquals("", KitchenOrders.normalizeTable("   "))
    }

    @Test
    fun firstTicket_hasEverything() {
        val delta = KitchenOrders.delta(listOf(line(-1, "Masala Dosa", 2), line(-2, "Tea", 1)), emptyList())
        assertEquals(listOf(KotLine("Masala Dosa", "", 2), KotLine("Tea", "", 1)), delta.added)
        assertTrue(delta.cancelled.isEmpty())
    }

    @Test
    fun addOnTicket_hasOnlyTheExtraUnitsAndNewItems() {
        val first = listOf(line(-1, "Masala Dosa", 2), line(-2, "Tea", 1))
        val sent = KitchenOrders.sentFrom(first)
        val now = listOf(line(-1, "Masala Dosa", 3), line(-2, "Tea", 1), line(-3, "Vada", 2))
        val delta = KitchenOrders.delta(now, sent)
        assertEquals(listOf(KotLine("Masala Dosa", "", 1), KotLine("Vada", "", 2)), delta.added)
        assertTrue(delta.cancelled.isEmpty())
    }

    @Test
    fun fewerOrRemovedItems_areListedAsCancelled() {
        val sent = KitchenOrders.sentFrom(listOf(line(-1, "Masala Dosa", 3), line(-2, "Tea", 1)))
        val delta = KitchenOrders.delta(listOf(line(-1, "Masala Dosa", 2)), sent)
        assertTrue(delta.added.isEmpty())
        assertEquals(listOf(KotLine("Masala Dosa", "", 1), KotLine("Tea", "", 1)), delta.cancelled)
    }

    @Test
    fun nothingChanged_meansNoTicket() {
        val lines = listOf(line(-1, "Masala Dosa", 2))
        assertTrue(KitchenOrders.delta(lines, KitchenOrders.sentFrom(lines)).isEmpty)
    }

    @Test
    fun swappingOneUnitForAnother_sendsBothSides() {
        val sent = KitchenOrders.sentFrom(listOf(line(-1, "Dosa", 1)))
        val delta = KitchenOrders.delta(listOf(line(-2, "Idli", 1)), sent)
        assertEquals(listOf(KotLine("Idli", "", 1)), delta.added)
        assertEquals(listOf(KotLine("Dosa", "", 1)), delta.cancelled)
    }
}
