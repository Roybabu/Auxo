// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos

import android.app.Application
import com.auxo.pos.core.debuglog.DebugLog
import com.auxo.pos.core.di.AppContainer

class AuxoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Debug builds only: records this run to a file so problems can be read afterwards.
        DebugLog.install(this)
        container = AppContainer(this)
    }
}
