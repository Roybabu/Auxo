// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.auto

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.auxo.pos.AuxoApplication
import java.util.concurrent.TimeUnit

/** Keeps a background check running while automatic backup is on. */
interface AutoBackupScheduler {
    /** Starts the periodic check when [active], stops it otherwise. Safe to call repeatedly. */
    fun sync(active: Boolean)
}

/**
 * Wakes the app every few hours to ask the runner whether a backup is due. The runner decides, so this works
 * for every schedule, and a phone that was off or asleep catches up as soon as it can. The app also checks
 * whenever it is opened.
 */
class WorkManagerAutoBackupScheduler(private val context: Context) : AutoBackupScheduler {

    override fun sync(active: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!active) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(CHECK_EVERY_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "auto_backup_check"
        const val CHECK_EVERY_HOURS = 6L
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // A failure is recorded in the settings for the Super Admin to see; retrying here would only repeat it.
        (applicationContext as AuxoApplication).container.autoBackupRunner.runIfDue()
        return Result.success()
    }
}
