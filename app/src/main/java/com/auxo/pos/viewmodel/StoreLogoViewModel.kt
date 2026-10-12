package com.auxo.pos.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.shop.LogoSaveResult
import com.auxo.pos.core.shop.StoreLogoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreLogoUiState(
    val logo: Bitmap? = null,
    val isWorking: Boolean = false,
    /** The last picture chosen could not be read. */
    val unreadable: Boolean = false,
)

class StoreLogoViewModel(private val repository: StoreLogoRepository) : ViewModel() {

    private val transient = MutableStateFlow(StoreLogoUiState())

    val uiState: StateFlow<StoreLogoUiState> = combine(repository.logo, transient) { logo, extra ->
        extra.copy(logo = logo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreLogoUiState(logo = repository.logo.value))

    /** The person picked a picture from the gallery; null means they backed out. */
    fun onPicked(uri: Uri?) {
        if (uri == null) return
        run { repository.save(uri) }
    }

    fun onRemove() {
        viewModelScope.launch {
            repository.remove()
            transient.update { it.copy(unreadable = false) }
        }
    }

    private fun run(save: suspend () -> LogoSaveResult) {
        transient.update { it.copy(isWorking = true, unreadable = false) }
        viewModelScope.launch {
            val result = save()
            transient.update { it.copy(isWorking = false, unreadable = result == LogoSaveResult.UNREADABLE) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { StoreLogoViewModel(appContainer().storeLogoRepository) }
        }
    }
}
