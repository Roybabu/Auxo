package com.invision.pos.core.label

import com.invision.pos.core.barcode.BarcodeEncoder
import com.invision.pos.core.format.MoneyFormatter
import com.invision.pos.domain.barcode.BarcodeSymbology
import com.invision.pos.testutil.FakeInventoryRepository
import com.invision.pos.testutil.MainDispatcherRule
import com.invision.pos.testutil.TestData
import com.invision.pos.viewmodel.LabelPrintViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LabelSheetLayoutTest {

    @Test
    fun a4Sheet_fillsRowsLeftToRight() {
        val template = LabelTemplate.A4_24
        val slots = LabelSheetLayout.slots(template, 26)
        assertEquals(26, slots.size)
        assertEquals(LabelSlot(0, 7.25f, 12.9f), slots[0])
        assertEquals(7.25f + 63.5f + 2.5f, slots[1].leftMm, 0.001f)
        assertEquals(12.9f + 33.9f, slots[3].topMm, 0.001f) // first label of the second row
        assertEquals(0, slots[23].pageIndex)
        assertEquals(1, slots[24].pageIndex)
        assertEquals(LabelSlot(1, 7.25f, 12.9f), slots[24])
    }

    @Test
    fun sheetFitsOnA4() {
        val t = LabelTemplate.A4_24
        val right = t.marginLeftMm + t.columns * t.labelWidthMm + (t.columns - 1) * t.gapXMm
        val bottom = t.marginTopMm + t.rows * t.labelHeightMm + (t.rows - 1) * t.gapYMm
        assertTrue(right <= t.pageWidthMm)
        assertTrue(bottom <= t.pageHeightMm)
    }

    @Test
    fun pageCount() {
        assertEquals(0, LabelSheetLayout.pageCount(LabelTemplate.A4_24, 0))
        assertEquals(1, LabelSheetLayout.pageCount(LabelTemplate.A4_24, 24))
        assertEquals(2, LabelSheetLayout.pageCount(LabelTemplate.A4_24, 25))
        assertEquals(5, LabelSheetLayout.pageCount(LabelTemplate.ROLL_50X25, 5))
    }

    @Test
    fun millimetresToPoints() {
        assertEquals(595.28f, LabelSheetLayout.mmToPoints(210f), 0.01f)
    }
}

class LabelContentFactoryTest {

    private val formatter = MoneyFormatter()

    @Test
    fun buildsOneLabelPerCopy() {
        val product = TestData.tShirt()
        val (m, l) = product.variants
        val labels = LabelContentFactory.build(product, mapOf(m.id to 2, l.id to 0), showMrp = false, formatter::format)
        assertEquals(2, labels.size)
        val label = labels.first()
        assertEquals("Cotton T-Shirt", label.title)
        assertEquals("Roy Wear · M · Navy", label.subtitle)
        assertEquals(m.barcode, label.barcode)
        assertNull(label.mrp)
    }

    @Test
    fun includesMrpWhenRequested() {
        val product = TestData.biscuits()
        val label = LabelContentFactory.forVariant(product, product.variants.single(), showMrp = true, formatter::format)
        assertEquals(formatter.format(product.variants.single().mrp!!), label.mrp)
    }
}

class BarcodeEncoderTest {

    @Test
    fun ean13_has95Modules() {
        val pattern = BarcodeEncoder.encode("4006381333931")!!
        assertEquals(BarcodeSymbology.EAN_13, pattern.symbology)
        assertEquals(95, pattern.modules.size)
        // Start guard bar-space-bar.
        assertTrue(pattern.modules[0] && !pattern.modules[1] && pattern.modules[2])
    }

    @Test
    fun upcA_matchesEan13WithLeadingZero() {
        val upc = BarcodeEncoder.encode("036000291452")!!
        val ean = BarcodeEncoder.encode("0036000291452")!!
        assertTrue(upc.modules.contentEquals(ean.modules))
    }

    @Test
    fun ean8AndCode128() {
        assertEquals(67, BarcodeEncoder.encode("96385074")!!.modules.size)
        val code128 = BarcodeEncoder.encode("SHIRT-M")!!
        assertEquals(BarcodeSymbology.CODE_128, code128.symbology)
        assertTrue(code128.modules.isNotEmpty())
    }

    @Test
    fun invalidValues_returnNull() {
        assertNull(BarcodeEncoder.encode("4006381333930"))
        assertNull(BarcodeEncoder.encode("bad value"))
    }
}

class LabelPrintViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun defaultsToOneCopyPerVariant_andMatchStock() {
        val viewModel = LabelPrintViewModel(1, FakeInventoryRepository(listOf(TestData.tShirt())))
        val state = viewModel.uiState.value
        assertEquals(2, state.totalLabels)
        assertEquals(1, state.pageCount)

        viewModel.matchStock()
        assertEquals(9, viewModel.uiState.value.totalLabels)

        viewModel.setTemplate(LabelTemplate.ROLL_50X25)
        assertEquals(9, viewModel.uiState.value.pageCount)

        viewModel.setCopies(11, 1000)
        assertEquals(LabelPrintViewModel.MAX_COPIES, viewModel.uiState.value.copies[11])
    }

    @Test
    fun missingProduct_reportsFailure() {
        val viewModel = LabelPrintViewModel(7, FakeInventoryRepository())
        assertTrue(viewModel.uiState.value.loadFailed)
    }
}
