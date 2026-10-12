package com.invision.pos.core.backup.auto

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the automatic-backup choices and results are kept. */
interface AutoBackupSettingsStore {
    val settings: StateFlow<AutoBackupSettings>

    /** Applies [change] to the saved settings as one step and keeps the result within range. */
    fun update(change: (AutoBackupSettings) -> AutoBackupSettings)
}

/**
 * Kept in the app's private preferences, not in the database or the backup file, so restoring an old backup
 * does not switch automatic backup off or point it at a different folder.
 */
class SharedPrefsAutoBackupStore(context: Context) : AutoBackupSettingsStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<AutoBackupSettings> = _settings.asStateFlow()

    @Synchronized
    override fun update(change: (AutoBackupSettings) -> AutoBackupSettings) {
        val next = change(read()).sanitized()
        prefs.edit()
            .putString(FOLDER_URI, next.folderUri)
            .putString(FOLDER_NAME, next.folderName)
            .putString(SCHEDULE, next.schedule.name)
            .putInt(INTERVAL, next.invoiceInterval)
            .putInt(KEEP, next.keep)
            .putLong(LAST_SUCCESS, next.lastSuccessMillis ?: 0L)
            .putLong(LAST_FAILURE, next.lastFailureMillis ?: 0L)
            .putString(FAILURE, next.lastFailure?.name)
            .putInt(SINCE, next.invoicesSinceBackup)
            .apply()
        _settings.value = next
    }

    private fun read(): AutoBackupSettings = AutoBackupSettings(
        folderUri = prefs.getString(FOLDER_URI, null)?.takeIf { it.isNotEmpty() },
        folderName = prefs.getString(FOLDER_NAME, "").orEmpty(),
        schedule = prefs.getString(SCHEDULE, null)?.let { name -> BackupSchedule.entries.firstOrNull { it.name == name } } ?: BackupSchedule.OFF,
        invoiceInterval = prefs.getInt(INTERVAL, AutoBackupSettings.DEFAULT_INVOICE_INTERVAL),
        keep = prefs.getInt(KEEP, AutoBackupSettings.DEFAULT_KEEP),
        lastSuccessMillis = prefs.getLong(LAST_SUCCESS, 0L).takeIf { it > 0 },
        lastFailureMillis = prefs.getLong(LAST_FAILURE, 0L).takeIf { it > 0 },
        lastFailure = prefs.getString(FAILURE, null)?.let { name -> AutoBackupFailure.entries.firstOrNull { it.name == name } },
        invoicesSinceBackup = prefs.getInt(SINCE, 0),
    ).sanitized()

    private companion object {
        const val PREFS = "auto_backup"
        const val FOLDER_URI = "folder_uri"
        const val FOLDER_NAME = "folder_name"
        const val SCHEDULE = "schedule"
        const val INTERVAL = "invoice_interval"
        const val KEEP = "keep"
        const val LAST_SUCCESS = "last_success"
        const val LAST_FAILURE = "last_failure"
        const val FAILURE = "failure"
        const val SINCE = "invoices_since"
    }
}
