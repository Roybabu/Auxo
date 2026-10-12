package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.security.GstReportService
import com.auxo.pos.domain.gst.GstReport
import com.auxo.pos.domain.billing.InvoiceNumbers
import com.auxo.pos.domain.reports.PeriodPreset
import com.auxo.pos.domain.reports.ReportPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

enum class GstPeriodMode { MONTH, RANGE }

data class GstReportUiState(
    val mode: GstPeriodMode = GstPeriodMode.MONTH,
    val month: YearMonth,
    val from: LocalDate,
    val to: LocalDate,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val report: GstReport? = null,
    /** Months after the current one are not offered. */
    val canGoNext: Boolean = false,
) {
    /** The days the report covers, both ends included. */
    val period: ReportPeriod
        get() = when (mode) {
            GstPeriodMode.MONTH -> ReportPeriod.of(PeriodPreset.CUSTOM, month.atDay(1), month.atDay(1), month.atEndOfMonth())
            GstPeriodMode.RANGE -> ReportPeriod.of(PeriodPreset.CUSTOM, from, from, to)
        }
}

/** The GST report for a chosen month or date range, Super Admin only. */
class GstReportViewModel(
    private val service: GstReportService,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)

    private val _uiState = MutableStateFlow(
        // Opens on last month's GST, which is what is normally being filed.
        YearMonth.from(LocalDate.now(clock)).minusMonths(1).let { month ->
            GstReportUiState(month = month, from = month.atDay(1), to = month.atEndOfMonth(), canGoNext = true)
        },
    )
    val uiState: StateFlow<GstReportUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun onModeChange(mode: GstPeriodMode) {
        if (mode == _uiState.value.mode) return
        _uiState.update { it.copy(mode = mode) }
        load()
    }

    fun previousMonth() {
        _uiState.update { it.copy(month = it.month.minusMonths(1), canGoNext = true) }
        load()
    }

    fun nextMonth() {
        val current = YearMonth.from(today)
        val next = _uiState.value.month.plusMonths(1)
        if (next.isAfter(current)) return
        _uiState.update { it.copy(month = next, canGoNext = next.isBefore(current)) }
        load()
    }

    /** Sets the range; the ends are swapped if they are the wrong way round. */
    fun onRangeChange(from: LocalDate, to: LocalDate) {
        val start = if (to.isBefore(from)) to else from
        val end = if (to.isBefore(from)) from else to
        _uiState.update { it.copy(mode = GstPeriodMode.RANGE, from = start, to = end) }
        load()
    }

    /** The financial year so far (1 April to today). */
    fun thisFinancialYear() = onRangeChange(LocalDate.of(InvoiceNumbers.financialYearStart(today), 4, 1), today)

    fun load() {
        job?.cancel()
        val period = _uiState.value.period
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        job = viewModelScope.launch {
            try {
                val report = service.report(period, clock.zone)
                _uiState.update { if (it.period == period) it.copy(isLoading = false, report = report) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { if (it.period == period) it.copy(isLoading = false, loadFailed = true, report = null) else it }
            }
        }
    }

    val zone get() = clock.zone

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                GstReportViewModel(container.gstReportService, container.clock)
            }
        }
    }
}
