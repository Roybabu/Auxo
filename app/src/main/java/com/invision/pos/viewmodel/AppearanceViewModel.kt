package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invision.pos.core.appearance.AppAccent
import com.invision.pos.core.appearance.AppearanceRepository
import com.invision.pos.core.appearance.AppearanceSettings
import kotlinx.coroutines.flow.StateFlow

/** The app colour and dark mode choices shown on the More page. */
class AppearanceViewModel(private val repository: AppearanceRepository) : ViewModel() {

    val settings: StateFlow<AppearanceSettings> = repository.settings

    fun onAccentChange(accent: AppAccent) = repository.setAccent(accent)

    fun onDarkModeChange(enabled: Boolean) = repository.setDarkMode(enabled)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppearanceViewModel(appContainer().appearanceRepository) }
        }
    }
}
