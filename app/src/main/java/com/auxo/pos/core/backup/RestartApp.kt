package com.auxo.pos.core.backup

import android.content.Context
import android.content.Intent
import android.os.Process

/** Starts the app again from scratch, so it opens the restored data. */
object RestartApp {
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        Process.killProcess(Process.myPid())
    }
}
