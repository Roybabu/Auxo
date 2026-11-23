package com.auxo.pos.viewmodel

import com.auxo.pos.domain.usecase.CheckoutUseCase
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import com.auxo.pos.testutil.FakeSettingsRepository
import com.auxo.pos.testutil.MainDispatcherRule
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The New bill search filters the whole product list in memory on every keystroke, so it is checked here against a
 * big shop: the answers stay right and capped, and typing does not slow down in a way that grows out of hand.
 */
class LargeCatalogueSearchTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val count = 10_000

    /** A valid EAN-13 for [n]: 12 digits then the check digit, so the scan rules accept it. */
    private fun ean(n: Int): String {
        val body = "890" + n.toString().padStart(9, '0')
        val sum = body.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return body + ((10 - sum % 10) % 10)
    }

    private val inventory = FakeInventoryRepository(
        (1..count).map { n ->
            TestData.tShirt(n.toLong()).copy(
                name = "Item $n",
                brand = null,
                variants = listOf(TestData.variant(id = n * 10L + 1, barcode = ean(n), size = "M", colour = "Navy", stock = 5)),
            )
        },
    )
    private val settings = FakeSettingsRepository()
    private val billing = FakeBillingRepository(inventory)

    private fun createViewModel() = BillingViewModel(inventory, settings, CheckoutUseCase(billing, settings, TestData.clock))

    @Test
    fun aBroadSearch_isCappedAtTheResultLimit() {
        val viewModel = createViewModel()
        viewModel.onSearchTextChange("Item 99")
        // Item 99, 990-999 and 9900-9999 all match, far more than the limit.
        assertEquals(BillingViewModel.MAX_RESULTS, viewModel.uiState.value.searchResults.size)
    }

    @Test
    fun anExactBarcode_findsJustThatOneItem() {
        val viewModel = createViewModel()
        viewModel.onSearchTextChange(ean(7_321))
        val results = viewModel.uiState.value.searchResults
        assertEquals(listOf("Item 7321"), results.map { it.product.name })
    }

    @Test
    fun aNameThatMatchesNothing_givesNoResults() {
        val viewModel = createViewModel()
        viewModel.onSearchTextChange("zzz not an item")
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
    }

    @Test
    fun typingStaysQuick_withTenThousandProducts() {
        val viewModel = createViewModel()
        val keystrokes = listOf("I", "It", "Ite", "Item", "Item ", "Item 5", "Item 50", "Item 500", "Item 5000", "5000")
        // One warm-up pass so class loading is not counted, then the timed passes.
        keystrokes.forEach(viewModel::onSearchTextChange)

        val started = System.nanoTime()
        repeat(5) { keystrokes.forEach(viewModel::onSearchTextChange) }
        val perKeystrokeMillis = (System.nanoTime() - started) / 1_000_000.0 / (5 * keystrokes.size)

        // A deliberately generous bound: this guards against a search that suddenly becomes far slower
        // (for example repeated work per product), not against normal machine-to-machine differences.
        assertTrue("Each keystroke took $perKeystrokeMillis ms with $count products", perKeystrokeMillis < 100.0)
    }
}
