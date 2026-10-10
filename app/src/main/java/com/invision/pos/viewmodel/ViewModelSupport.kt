package com.invision.pos.viewmodel

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.invision.pos.InvisionApplication
import com.invision.pos.core.di.AppContainer

/** The app's dependency container, for use inside ViewModel factory initializers. */
internal fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as InvisionApplication).container

/** Keeps StateFlows alive briefly across configuration changes. */
internal const val STOP_TIMEOUT_MILLIS = 5_000L
