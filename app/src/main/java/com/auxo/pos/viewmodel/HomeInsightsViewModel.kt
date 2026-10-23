// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.BillingRepository
import com.auxo.pos.data.repository.InventoryRepository
import com.auxo.pos.data.repository.PurchasingRepository
import com.auxo.pos.domain.billing.businessDays
import com.auxo.pos.domain.insights.Collected
import com.auxo.pos.domain.insights.Insights
import com.auxo.pos.domain.insights.InsightsBuilder
import com.auxo.pos.domain.insights.InsightsPeriod
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.reports.PeriodPreset
import com.auxo.pos.domain.reports.ReportPeriod
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
    /** Cash and UPI taken today for the header cards; null until loaded or if loading failed. */
    val collectedToday: Collected? = null,
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

    private var todayJob: Job? = null

    init {
        load()
        loadToday()
    }

    fun onPeriodChange(period: InsightsPeriod) {
        if (period == _uiState.value.period) return
        _uiState.update { it.copy(period = period) }
        load()
    }

    fun retry() {
        load()
        loadToday()
    }

    /** Header figures: always today's business day, whatever period the charts show. */
    private fun loadToday() {
        todayJob?.cancel()
        _uiState.update { it.copy(collectedToday = null) }
        todayJob = viewModelScope.launch {
            try {
                // Moves on to the new day at midnight, so the header never shows yesterday's takings as today's.
                clock.businessDays().collectLatest { day ->
                    val (from, to) = ReportPeriod.of(PeriodPreset.TODAY, day).bounds(clock.zone)
                    billing.observeInvoices(from, to).collectLatest {
                        val collected = runCatching { InsightsBuilder.collected(billing.invoicesBetween(from, to)) }
                            .onFailure { e -> if (e is CancellationException) throw e }.getOrNull()
                        _uiState.update { it.copy(collectedToday = collected) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("HomeInsightsViewModel", "Failed and showed an error", e)
                _uiState.update { it.copy(collectedToday = null) }
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        val period = _uiState.value.period
        _uiState.update { it.copy(isLoading = true, failed = false, insights = null) }
        loadJob = viewModelScope.launch {
            try {
                // The period is counted back from today, which moves on at midnight.
                clock.businessDays().collectLatest { today ->
                    val (from, to) = ReportPeriod.of(period.preset, today).bounds(clock.zone)
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
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("HomeInsightsViewModel", "Failed and showed an error", e)
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
