package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomItemTest {

    private fun valid(name: String = " Gift wrap ", price: String = "49.50", quantity: String = "2", gst: Int = 1_800) =
        CustomItem.validate(name, price, quantity, gst)

    @Test
    fun validDetails_makeAnItem_withTheNameTrimmed() {
        val draft = (valid() as CustomItemResult.Valid).draft
        assertEquals(CustomItemDraft("Gift wrap", Money(4_950), 2, 1_800), draft)
    }

    @Test
    fun missingOrBadDetails_areReportedTogether() {
        val result = CustomItem.validate("  ", "0", "0", 500) as CustomItemResult.Invalid
        assertEquals(
            setOf(CustomItemProblem.NAME_REQUIRED, CustomItemProblem.PRICE_INVALID, CustomItemProblem.QUANTITY_INVALID),
            result.problems,
        )
        assertTrue((valid(price = "12.345") as CustomItemResult.Invalid).problems.contains(CustomItemProblem.PRICE_INVALID))
        assertTrue((valid(quantity = "1000") as CustomItemResult.Invalid).problems.contains(CustomItemProblem.QUANTITY_INVALID))
    }

    @Test
    fun anItemOnTheBill_isNotTiedToStock_andSavesWithoutAProduct() {
        val line = CartLine.custom(CartLine.CUSTOM_KEY_BASE, (valid() as CustomItemResult.Valid).draft)
        assertNull(line.stockVariantId)
        assertNull(line.menuItemId)

        val cart = Cart(lines = listOf(line))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        assertTrue(CartValidator.check(cart, totals, TestData.settings.gstRegistered).canCheckout)

        val invoice = SaleBuilder.build(cart, totals, TestData.settings, emptyList(), TestData.clock.millis())
        assertNull(invoice.lines.single().variantId)
        assertNull(invoice.lines.single().menuItemId)
        assertEquals("Gift wrap", invoice.lines.single().productName)
    }

    @Test
    fun twoCustomItems_stayOnSeparateLines() {
        val draft = (valid() as CustomItemResult.Valid).draft
        val cart = Cart()
            .add(CartLine.custom(CartLine.CUSTOM_KEY_BASE, draft))
            .add(CartLine.custom(CartLine.CUSTOM_KEY_BASE - 1, draft))
        assertEquals(2, cart.lines.size)
    }
}
