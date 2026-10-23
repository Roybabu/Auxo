// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.usecase

import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.barcode.InternalBarcodeGenerator
import com.auxo.pos.domain.inventory.StockAdjustmentError
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SaveProductUseCaseTest {

    @Test
    fun blankBarcodes_getDistinctInStoreCodes_andGivenOnesAreKept() = runTest {
        val repository = FakeInventoryRepository(listOf(TestData.tShirt()))
        val useCase = SaveProductUseCase(repository, InternalBarcodeGenerator(Random(3)))
        val socks = Product(
            name = "Socks",
            category = ProductCategory.GARMENT,
            variants = listOf(
                TestData.variant(barcode = "", size = "S", colour = "Black"),
                TestData.variant(barcode = "", size = "M", colour = "Black"),
                TestData.variant(barcode = TestData.EAN[6], size = "L", colour = "Black"),
            ),
        )

        val saved = useCase(socks) as SaveProductResult.Saved
        val codes = repository.products.value.first { it.id == saved.productId }.variants.map { it.barcode }
        assertEquals(3, codes.toSet().size)
        assertTrue(codes.take(2).all { InternalBarcodeGenerator.isInStoreCode(it) })
        assertEquals(TestData.EAN[6], codes[2])
    }
}

class AdjustStockUseCaseTest {

    private val repository = FakeInventoryRepository(listOf(TestData.tShirt()))
    private val useCase = AdjustStockUseCase(repository, TestData.clock)
    private val medium = TestData.tShirt().variants.first() // 8 in stock

    @Test
    fun validAdjustment_isRecordedWithTimeAndNote() = runTest {
        assertEquals(AdjustStockResult.Done, useCase(medium, StockChangeReason.DAMAGED, "2", "  Torn seam "))
        val movement = repository.movements.value.single()
        assertEquals(-2, movement.quantityChange)
        assertEquals("Torn seam", movement.note)
        assertEquals(TestData.clock.millis(), movement.createdAtMillis)
        assertEquals(6, repository.getProduct(1)!!.variants.first().stockQuantity)
    }

    @Test
    fun invalidInput_doesNotTouchStock() = runTest {
        assertEquals(
            AdjustStockResult.Rejected(StockAdjustmentError.QUANTITY_REQUIRED),
            useCase(medium, StockChangeReason.RECEIVED, "", ""),
        )
        assertTrue(repository.movements.value.isEmpty())
    }

    @Test
    fun blankNote_isStoredAsNull() = runTest {
        useCase(medium, StockChangeReason.RECEIVED, "1", "   ")
        assertNull(repository.movements.value.single().note)
    }
}

class FindByBarcodeUseCaseTest {

    @Test
    fun trimsInput_andIgnoresBlank() = runTest {
        val repository = FakeInventoryRepository(listOf(TestData.biscuits()))
        val useCase = FindByBarcodeUseCase(repository)
        assertEquals(2L, useCase("  ${TestData.EAN[2]} ")?.product?.id)
        assertNull(useCase("   "))
        assertNull(useCase("0000000000000"))
    }
}

class ObserveInventorySummaryUseCaseTest {

    @Test
    fun usesTheClockForExpiry() = runTest {
        val repository = FakeInventoryRepository(listOf(TestData.juice(stock = 3)))
        val summary = ObserveInventorySummaryUseCase(repository, TestData.clock)().first()
        assertEquals(1, summary.expiringSoonCount)
        assertEquals(3, summary.unitsInStock)
    }
}
