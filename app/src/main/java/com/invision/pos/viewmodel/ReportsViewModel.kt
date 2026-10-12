package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.core.export.ExportFile
import com.invision.pos.data.repository.BillingRepository
import com.invision.pos.data.repository.CustomerRepository
import com.invision.pos.data.repository.InventoryRepository
import com.invision.pos.data.repository.PurchasingRepository
import com.invision.pos.core.auth.Permission
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.Supplier
import com.invision.pos.domain.reports.ExpiryWindow
import com.invision.pos.domain.reports.ItemKind
import com.invision.pos.domain.reports.PeriodPreset
import com.invision.pos.domain.reports.ReportCsv
import com.invision.pos.domain.reports.ReportPeriod
import com.invision.pos.domain.reports.ReportTable
import com.invision.pos.domain.reports.ReportType
import com.invision.pos.domain.reports.Reports
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** The filters a report offers besides its period. */
val ReportType.usesCategory: Boolean
    get() = this == ReportType.STOCK_VALUE || this == ReportType.STOCK_MOVEMENTS || this == ReportType.WASTAGE

/**
 * What a person needs to be allowed to read the data behind this report, or null when the basic billing
 * permissions are enough. Reports are hidden from anyone without it, instead of opening and failing.
 */
val ReportType.requiredPermission: Permission?
    get() = when (this) {
        ReportType.PURCHASES -> Permission.MANAGE_PURCHASES
        ReportType.STOCK_MOVEMENTS, ReportType.WASTAGE, ReportType.EXPIRY -> Permission.VIEW_STOCK_RECORDS
        else -> null
    }

/** The reports offered to someone: the supplier report only when suppliers are on, and only those they may read. */
fun availableReportTypes(suppliersOn: Boolean, can: (Permission) -> Boolean): List<ReportType> =
    ReportType.entries.filter { (suppliersOn || !it.usesSupplier) && (it.requiredPermission?.let(can) ?: true) }

val ReportType.usesSupplier: Boolean get() = this == ReportType.PURCHASES

val ReportType.usesItemKind: Boolean get() = this == ReportType.ITEM_SALES

val ReportType.usesExpiryWindow: Boolean get() = this == ReportType.EXPIRY

val ReportType.usesOutstanding: Boolean get() = this == ReportType.CUSTOMER_BALANCES

data class ReportFilters(
    val type: ReportType = ReportType.SALES_SUMMARY,
    val preset: PeriodPreset = PeriodPreset.THIS_MONTH,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val category: ProductCategory? = null,
    val supplierId: Long? = null,
    val itemKind: ItemKind = ItemKind.ALL,
    val expiryWindow: ExpiryWindow = ExpiryWindow.WITHIN_30_DAYS,
    val onlyOutstanding: Boolean = true,
)

data class ReportsUiState(
    val filters: ReportFilters = ReportFilters(),
    /** The period as of today, for showing its dates. */
    val period: ReportPeriod? = null,
    val suppliers: List<Supplier> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val table: ReportTable? = null,
)

sealed interface ReportsEvent {
    data class Export(val file: ExportFile) : ReportsEvent
}

/** Builds the chosen report from saved data when a filter or the underlying data changes. */
class ReportsViewModel(
    private val billing: BillingRepository,
    private val inventory: InventoryRepository,
    private val purchasing: PurchasingRepository,
    private val customers: CustomerRepository,
    private val clock: Clock,
    private val shop: com.invision.pos.core.shop.ShopPreferences? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ReportsEvent>(Channel.BUFFERED)
    val events: Flow<ReportsEvent> = eventChannel.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val suppliers = runCatching { purchasing.observeSuppliers().first() }.getOrDefault(emptyList())
            _uiState.update { it.copy(suppliers = suppliers) }
        }
        load()
    }

    fun onTypeChange(type: ReportType) = change { it.copy(type = type) }

    fun onPresetChange(preset: PeriodPreset) = change { it.copy(preset = preset) }

    /** Picks a custom range; the ends are swapped if they are the wrong way round. */
    fun onCustomRangeChange(from: LocalDate?, to: LocalDate?) =
        change { it.copy(preset = PeriodPreset.CUSTOM, customFrom = from, customTo = to) }

    fun onCategoryChange(category: ProductCategory?) = change { it.copy(category = category) }

    fun onSupplierChange(supplierId: Long?) = change { it.copy(supplierId = supplierId) }

    fun onItemKindChange(kind: ItemKind) = change { it.copy(itemKind = kind) }

    fun onExpiryWindowChange(window: ExpiryWindow) = change { it.copy(expiryWindow = window) }

    fun onOnlyOutstandingChange(only: Boolean) = change { it.copy(onlyOutstanding = only) }

    fun retry() = load()

    private fun change(transform: (ReportFilters) -> ReportFilters) {
        _uiState.update { it.copy(filters = transform(it.filters)) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val filters = _uiState.value.filters
        val today = LocalDate.now(clock)
        val period = ReportPeriod.of(filters.preset, today, filters.customFrom, filters.customTo)
        _uiState.update { it.copy(isLoading = true, loadFailed = false, period = period) }
        loadJob = viewModelScope.launch {
            val (from, to) = period.bounds(clock.zone)
            try {
                // Builds the report now and again whenever the data behind it changes.
                combine(sources(filters, from, to)) { }.collectLatest {
                    val table = runCatching { build(filters, period, today) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                    _uiState.update { it.copy(isLoading = false, loadFailed = table == null, table = table) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true, table = null) }
            }
        }
    }

    /** Streams that emit when saved data the chosen report reads from changes. */
    private fun sources(filters: ReportFilters, from: Long, to: Long): List<Flow<Any?>> = when (filters.type) {
        ReportType.SALES_SUMMARY, ReportType.ITEM_SALES, ReportType.GST_RATES, ReportType.HSN_SUMMARY ->
            listOf(billing.observeInvoices(from, to))
        ReportType.COLLECTIONS -> listOf(billing.observeInvoices(from, to), customers.observeAccountPayments(from, to))
        ReportType.PURCHASES -> listOf(purchasing.observeReceipts(from, to))
        ReportType.STOCK_MOVEMENTS, ReportType.WASTAGE -> listOf(inventory.observeMovementHistory(from, to))
        ReportType.STOCK_VALUE -> listOf(inventory.observeProducts())
        ReportType.LOW_STOCK -> listOf(inventory.observeProducts(), purchasing.observeSuppliers().catch { emit(emptyList()) })
        ReportType.EXPIRY -> listOf(inventory.observeBatches())
        ReportType.CUSTOMER_BALANCES -> listOf(customers.observeAccounts())
    }

    private suspend fun build(filters: ReportFilters, period: ReportPeriod, today: LocalDate): ReportTable {
        val zone = clock.zone
        val (from, to) = period.bounds(zone)
        return when (filters.type) {
            ReportType.SALES_SUMMARY -> Reports.salesSummary(billing.invoicesBetween(from, to), zone)
            ReportType.ITEM_SALES -> Reports.itemSales(billing.invoicesBetween(from, to), filters.itemKind)
            ReportType.COLLECTIONS -> Reports.collections(billing.invoicesBetween(from, to), customers.observeAccountPayments(from, to).first())
            ReportType.GST_RATES -> Reports.gstRates(billing.invoicesBetween(from, to))
            ReportType.HSN_SUMMARY -> Reports.hsnSummary(billing.invoicesBetween(from, to))
            ReportType.PURCHASES -> Reports.purchases(purchasing.observeReceipts(from, to).first(), filters.supplierId)
            ReportType.STOCK_MOVEMENTS -> Reports.stockMovements(inventory.observeMovementHistory(from, to).first(), category = filters.category)
            ReportType.WASTAGE -> Reports.wastage(inventory.observeMovementHistory(from, to).first(), filters.category)
            ReportType.STOCK_VALUE -> Reports.stockValue(inventory.observeProducts().first(), filters.category)
            ReportType.LOW_STOCK -> Reports.lowStock(inventory.observeProducts().first(), runCatching { purchasing.observeSuppliers().first() }.getOrDefault(emptyList()), withSupplier = shop?.suppliers?.value ?: true)
            ReportType.EXPIRY -> Reports.expiry(inventory.observeBatches().first(), today, filters.expiryWindow)
            ReportType.CUSTOMER_BALANCES -> Reports.customerBalances(customers.observeAccounts().first(), filters.onlyOutstanding)
        }
    }

    /** The report on screen as a CSV file. */
    fun export() {
        val state = _uiState.value
        val table = state.table ?: return
        if (state.isLoading) return
        eventChannel.trySend(ReportsEvent.Export(ExportFile(fileName(state.filters.type, state.period), ReportCsv.write(table, clock.zone))))
    }

    /** Such as "sales-summary_2026-10-01_2026-10-11.csv", or "stock-value_2026-10-11.csv" for a report of the current position. */
    fun fileName(type: ReportType, period: ReportPeriod?): String {
        val name = type.name.lowercase().replace('_', '-')
        val suffix = if (type.usesPeriod && period != null) period.slug else LocalDate.now(clock).toString()
        return "${name}_$suffix.csv"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ReportsViewModel(
                    billing = container.billingRepository,
                    inventory = container.inventoryRepository,
                    purchasing = container.purchasingRepository,
                    customers = container.customerRepository,
                    clock = container.clock,
                    shop = container.shopPreferences,
                )
            }
        }
    }
}
