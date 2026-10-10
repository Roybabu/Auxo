package com.invision.pos

import android.app.Application
import com.invision.pos.core.di.AppContainer

class InvisionApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
