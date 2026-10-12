package com.auxo.pos

import android.app.Application
import com.auxo.pos.core.di.AppContainer

class AuxoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
