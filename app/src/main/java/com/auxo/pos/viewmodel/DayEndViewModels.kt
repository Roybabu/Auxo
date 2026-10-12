package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.data.repository.CloseDayResult
import com.auxo.pos.domain.billing.AmountInput
import com.auxo.pos.data.repository.DayEndRepository
import com.auxo.pos.domain.dayend.CashComparison
import com.auxo.pos.domain.dayend.CountError
import com.auxo.pos.domain.dayend.CountedCash
import com.auxo.pos.domain.dayend.DayClosing
import com.auxo.pos.domain.dayend.DayFigures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayEndUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val date: LocalDate? = null,
    /** Today's figures while the day is open. */
    val figures: DayFigures? = null,
    /** Set once today has been closed. */
    val closedToday: DayClosing? = null,
    /** Earlier closed days, latest first. */
    val history: List<DayClosing> = emptyList(),
    val counted: String = "",
    val note: String = "",
    val countError: CountError? = null,
    /** Asking "close the day with these numbers?" */
    val confirming: Boolean = false,
    val isSaving: Boolean = false,
) {
    /** Counted against expected, once a valid amount is typed. */
    val comparison: CashComparison?
        get() {
            val figures = figures ?: return null
            val counted = (CountedCash.parse(counted) as? CountedCash.Valid)?.amount ?: return null
            return CashComparison(figures.expectedCash, counted)
        }
}

sealed interface DayEndEvent {
    data class Closed(val closingId: Long) : DayEndEvent

    /** Someone else closed it first; the closing on record is shown. */
    data object AlreadyClosed : DayEndEvent

    data object Failed : DayEndEvent
}

class DayEndViewModel(private val repository: DayEndRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DayEndUiState())
    val uiState: StateFlow<DayEndUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<DayEndEvent>(Channel.BUFFERED)
    val events: Flow<DayEndEvent> = eventChannel.receiveAsFlow()

    private var observing: Job? = null

    init {
        load()
    }

    fun load() {
        observing?.cancel()
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        observing = viewModelScope.launch {
            try {
                val today = repository.today()
                repository.observeClosings().collect { closings ->
                    val closedToday = closings.firstOrNull { it.date == today }
                    val figures = if (closedToday == null) repository.figuresFor(today) else null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                            date = today,
                            figures = figures,
                            closedToday = closedToday,
                            history = closings.filter { c -> c.date != today },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    /** Works the figures out again, to include bills made since the screen was opened. */
    fun refresh() {
        val date = _uiState.value.date ?: return
        if (_uiState.value.closedToday != null) return
        viewModelScope.launch {
            val figures = runCatching { repository.figuresFor(date) }.getOrNull() ?: return@launch
            _uiState.update { it.copy(figures = figures) }
        }
    }

    fun onCountedChange(text: String) = _uiState.update {
        it.copy(counted = AmountInput.sanitize(text), countError = null)
    }

    fun onNoteChange(text: String) = _uiState.update { it.copy(note = text.take(NOTE_MAX)) }

    /** Checks the count, refreshes the figures, and asks for confirmation. */
    fun requestClose() {
        val state = _uiState.value
        if (state.closedToday != null || state.isSaving) return
        when (val parsed = CountedCash.parse(state.counted)) {
            is CountedCash.Invalid -> _uiState.update { it.copy(countError = parsed.error) }
            is CountedCash.Valid -> viewModelScope.launch {
                val date = state.date ?: return@launch
                val figures = runCatching { repository.figuresFor(date) }.getOrNull()
                _uiState.update { it.copy(figures = figures ?: it.figures, confirming = true) }
            }
        }
    }

    fun cancelClose() = _uiState.update { it.copy(confirming = false) }

    fun confirmClose() {
        val state = _uiState.value
        val date = state.date ?: return
        val counted = (CountedCash.parse(state.counted) as? CountedCash.Valid)?.amount ?: return
        if (state.isSaving || state.closedToday != null) return
        _uiState.update { it.copy(confirming = false, isSaving = true) }
        viewModelScope.launch {
            val result = try {
                repository.closeDay(date, counted, state.note, closedBy = "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _uiState.update { it.copy(isSaving = false) }
            eventChannel.send(
                when (result) {
                    is CloseDayResult.Closed -> DayEndEvent.Closed(result.closing.id)
                    is CloseDayResult.AlreadyClosed -> DayEndEvent.AlreadyClosed
                    CloseDayResult.FutureDay, null -> DayEndEvent.Failed
                },
            )
        }
    }

    companion object {
        const val NOTE_MAX = 200

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { DayEndViewModel(appContainer().dayEndRepository) }
        }
    }
}

data class DayClosingUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val closing: DayClosing? = null,
    val notFound: Boolean = false,
)

class DayClosingViewModel(private val closingId: Long, private val repository: DayEndRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DayClosingUiState())
    val uiState: StateFlow<DayClosingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val closing = repository.getClosing(closingId)
                _uiState.update { DayClosingUiState(isLoading = false, closing = closing, notFound = closing == null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    companion object {
        const val ARG_CLOSING_ID = "closingId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DayClosingViewModel(
                    closingId = createSavedStateHandle().get<Long>(ARG_CLOSING_ID) ?: 0L,
                    repository = appContainer().dayEndRepository,
                )
            }
        }
    }
}
