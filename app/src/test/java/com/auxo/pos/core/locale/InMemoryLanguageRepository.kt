// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.locale

import com.auxo.pos.domain.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the choice in memory only, for tests. */
class InMemoryLanguageRepository(initial: Language? = null) : LanguageRepository {
    private val state = MutableStateFlow(initial)
    override val appLanguage: StateFlow<Language?> = state.asStateFlow()
    override fun setAppLanguage(language: Language?) {
        state.value = language
    }
}
