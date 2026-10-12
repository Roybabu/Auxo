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
class AppearanceViewModel(
    private val repository: AppearanceRepository,
    private val languages: com.invision.pos.core.locale.LanguageRepository,
) : ViewModel() {

    val settings: StateFlow<AppearanceSettings> = repository.settings

    /** The language chosen for the app on this device; null follows the phone. */
    val appLanguage: StateFlow<com.invision.pos.domain.model.Language?> = languages.appLanguage

    fun onAppLanguageChange(language: com.invision.pos.domain.model.Language?) = languages.setAppLanguage(language)

    fun onAccentChange(accent: AppAccent) = repository.setAccent(accent)

    fun onDarkModeChange(enabled: Boolean) = repository.setDarkMode(enabled)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppearanceViewModel(appContainer().appearanceRepository, appContainer().languageRepository) }
        }
    }
}
