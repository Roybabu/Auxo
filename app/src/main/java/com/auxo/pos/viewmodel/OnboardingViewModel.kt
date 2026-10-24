// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auxo.pos.core.shop.ShopPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OnboardingUiState(
    /** The page showing: 0 is the splash, then the business introduction, then the categories. */
    val page: Int = 0,
) {
    val isLastPage: Boolean get() = page >= PAGE_COUNT - 1

    companion object {
        const val PAGE_COUNT = 3
    }
}

/**
 * The first-launch introduction: which page is showing, and remembering that it has been seen. It only appears before
 * the first administrator exists; once finished (or skipped) the app goes on to first-time setup.
 */
class OnboardingViewModel(private val shop: ShopPreferences) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onPageChanged(page: Int) =
        _uiState.update { it.copy(page = page.coerceIn(0, OnboardingUiState.PAGE_COUNT - 1)) }

    /** Called on the last page's button and on Skip. */
    fun finish() = shop.setOnboardingSeen(true)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(appContainer().shopPreferences) }
        }
    }
}
