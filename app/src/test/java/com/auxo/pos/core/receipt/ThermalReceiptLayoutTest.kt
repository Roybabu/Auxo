package com.auxo.pos.core.receipt

import com.auxo.pos.domain.billing.SampleSales
import com.auxo.pos.domain.model.ReceiptOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** The text of the receipt on each roll: what a thermal printer prints, line by line. */
class ThermalReceiptLayoutTest {

    private val sale = SampleSales.shirtsAndBiscuits()

    private fun lines(width: Int): List<ThermalElement.Line> =
        ThermalReceiptBuilder.build(sale, ReceiptOptions(), width, ZoneOffset.UTC).elements.filterIsInstance<ThermalElement.Line>()

    @Test
    fun noPlainLineIsWiderThanTheRoll_on58_76And80() {
        for (width in listOf(32, 42, 48)) {
            for (line in lines(width)) {
                val limit = if (line.large) width / 2 else width
                assertTrue("'${line.text}' is ${line.text.length} wide on a $width-character roll", line.text.length <= limit)
            }
        }
    }

    @Test
    fun amountsSitAtTheRightEdge_soColumnsLineUp() {
        for (width in listOf(32, 42, 48)) {
            val pairs = lines(width).filter { it.label != null && !it.large }
            assertTrue(pairs.isNotEmpty())
            pairs.forEach { assertEquals("'${it.text}'", width, it.text.length) }
        }
    }

    @Test
    fun twoColumns_putsTheLabelLeftAndTheValueRight() {
        assertEquals("Total" + " ".repeat(7) + "100.00", ThermalReceiptBuilder.twoColumns("Total", "100.00", 18))
        assertEquals("Total" + " ".repeat(15) + "100.00", ThermalReceiptBuilder.twoColumns("Total", "100.00", 26))
    }

    @Test
    fun twoColumns_cutsALongLabel_andKeepsTheAmount() {
        val line = ThermalReceiptBuilder.twoColumns("A very long product name indeed", "1,299.00", 32)
        assertEquals(32, line.length)
        assertTrue(line.endsWith(" 1,299.00"))
        assertTrue(line.startsWith("A very long product"))
    }

    @Test
    fun theGrandTotalIsPrintedOnEveryRoll() {
        val total = com.auxo.pos.domain.billing.IndianNumberFormat.amount(sale.grandTotal)
        for (width in listOf(32, 42, 48)) {
            assertTrue("total missing on $width", lines(width).any { it.value == total || it.text.endsWith(total) })
        }
    }

    @Test
    fun theShopNameIsPrintedLargeAndCentredFirst() {
        val first = lines(32).first()
        assertTrue(first.large)
        assertEquals(Align.CENTER, first.align)
        assertEquals("Sample Mart", first.text)
    }
}
