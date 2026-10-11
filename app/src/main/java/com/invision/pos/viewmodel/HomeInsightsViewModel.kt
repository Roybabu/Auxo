package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.data.repository.BillingRepository
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.PurchasingRepository
import com.invision.pos.domain.insights.Insights
import com.invision.pos.domain.insights.InsightsBuilder
import com.invision.pos.domain.insights.InsightsPeriod
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.reports.ReportPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * What the Home charts need: the chosen [period], whether the data is loading or failed to
 * load, and the figures once built. [insights] is cleared while loading or after a failure,
 * so figures for one period are never shown under another.
 */
data class InsightsUiState(
    val period: InsightsPeriod = InsightsPeriod.LAST_7_DAYS,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val insights: Insights? = null,
)

/** Builds the Home charts from saved bills, goods receipts and products, and rebuilds them when any of them change. */
class HomeInsightsViewModel(
    private val billing: BillingRepository,
    private val inventory: InventoryRepository,
    private val purchasing: PurchasingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onPeriodChange(period: InsightsPeriod) {
        if (period == _uiState.value.period) return
        _uiState.update { it.copy(period = period) }
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        val period = _uiState.value.period
        val today = LocalDate.now(clock)
        val (from, to) = ReportPeriod.of(period.preset, today).bounds(clock.zone)
        _uiState.update { it.copy(isLoading = true, failed = false, insights = null) }
        loadJob = viewModelScope.launch {
            try {
                combine(
                    billing.observeInvoices(from, to),
                    purchasing.observeReceipts(from, to),
                    inventory.observeProducts(),
                ) { _, receipts, products -> receipts to products }
                    .collectLatest { (receipts, products) ->
                        val built = runCatching {
                            InsightsBuilder.build(
                                period = period,
                                today = today,
                                zone = clock.zone,
                                invoices = billing.invoicesBetween(from, to),
                                receipts = receipts,
                                categoryByVariant = categories(products),
                            )
                        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                        _uiState.update { it.copy(isLoading = false, failed = built == null, insights = built) }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, failed = true, insights = null) }
            }
        }
    }

    private fun categories(products: List<Product>): Map<Long, ProductCategory> =
        products.flatMap { product -> product.variants.map { it.id to product.category } }.toMap()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeInsightsViewModel(
                    billing = container.billingRepository,
                    inventory = container.inventoryRepository,
                    purchasing = container.purchasingRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
