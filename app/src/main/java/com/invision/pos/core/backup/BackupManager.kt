package com.invision.pos.core.backup

import android.content.Context
import android.net.Uri
import com.invision.pos.core.appearance.AppAccent
import com.invision.pos.core.appearance.AppearanceRepository
import com.invision.pos.data.local.InvisionDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Making and restoring backups of everything entered in the app. */
interface BackupService {
    /** Saves a backup in the Invision folder and returns where it went. */
    suspend fun backUp(): SavedBackup

    /**
     * Replaces everything on this phone with the backup in [source]. The app must be restarted
     * afterwards. Nothing is changed if the file is not a sound Invision backup.
     */
    suspend fun restore(source: Uri)

    /** When the last backup was made on this phone, or null if none has been. */
    fun lastBackupMillis(): Long?
}

class BackupManager(
    private val context: Context,
    private val database: InvisionDatabase,
    private val appearance: AppearanceRepository,
    private val storage: BackupStorage,
    private val clock: Clock,
    private val shop: com.invision.pos.core.shop.ShopPreferences? = null,
) : BackupService {

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun lastBackupMillis(): Long? = prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 }

    override suspend fun backUp(): SavedBackup = withContext(Dispatchers.IO) {
        // A manual and an automatic backup share one working folder, so only one may run at a time.
        BACKUP_LOCK.withLock { makeBackup() }
    }

    private fun makeBackup(): SavedBackup {
        val work = workDir()
        try {
            val snapshot = File(work, BackupArchive.DATABASE_ENTRY)
            snapshotDatabase(snapshot)
            val now = clock.millis()
            val manifest = BackupManifest(
                createdAtMillis = now,
                databaseVersion = InvisionDatabase.VERSION,
                appVersion = appVersion(),
            )
            val settings = appearance.settings.value
            val saved = storage.save(fileName(now)) { out ->
                BackupArchive.write(
                    out = out,
                    manifest = manifest,
                    database = snapshot,
                    preferences = buildMap {
                        put(PREF_ACCENT, settings.accent.id)
                        put(PREF_DARK, settings.darkMode.toString())
                        shop?.let { put(PREF_RESTAURANT_MENU, it.restaurantMenu.value.toString()) }
                        shop?.let { put(PREF_SUPPLIERS, it.suppliers.value.toString()) }
                    },
                )
            }
            prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
            return saved
        } finally {
            work.deleteRecursively()
        }
    }

    override suspend fun restore(source: Uri) = withContext(Dispatchers.IO) {
        val input = runCatching { context.contentResolver.openInputStream(source) }
            .getOrElse { throw BackupException(BackupProblem.UNREADABLE, cause = it) }
            ?: throw BackupException(BackupProblem.UNREADABLE)
        input.use { restoreFrom(it) }
    }

    /** The restore itself, from any stream; [restore] opens the chosen file and calls this. */
    fun restoreFrom(input: InputStream) {
        val work = workDir()
        try {
            val backup = BackupArchive.read(input, work)
            DatabaseCheck.verify(backup.database, InvisionDatabase.VERSION)

            // Keep what is on the phone now until the new data is in place.
            val current = context.getDatabasePath(InvisionDatabase.NAME)
            val safety = File(context.filesDir, "pre-restore").apply { mkdirs() }.resolve(InvisionDatabase.NAME)
            if (current.exists()) {
                checkpoint()
                current.copyTo(safety, overwrite = true)
            }

            database.close()
            try {
                deleteDatabaseFiles(current)
                backup.database.copyTo(current, overwrite = true)
            } catch (e: Exception) {
                runCatching {
                    deleteDatabaseFiles(current)
                    if (safety.exists()) safety.copyTo(current, overwrite = true)
                }
                throw BackupException(BackupProblem.RESTORE_FAILED, cause = e)
            }

            backup.preferences[PREF_ACCENT]?.let { appearance.setAccent(AppAccent.fromId(it)) }
            backup.preferences[PREF_DARK]?.let { appearance.setDarkMode(it.toBoolean()) }
            backup.preferences[PREF_RESTAURANT_MENU]?.let { shop?.setRestaurantMenu(it.toBoolean()) }
            backup.preferences[PREF_SUPPLIERS]?.let { shop?.setSuppliers(it.toBoolean()) }
        } finally {
            work.deleteRecursively()
        }
    }

    /** Writes everything still waiting in the database's log into the main file, then copies it. */
    private fun snapshotDatabase(target: File) {
        checkpoint()
        context.getDatabasePath(InvisionDatabase.NAME).copyTo(target, overwrite = true)
    }

    private fun checkpoint() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    private fun deleteDatabaseFiles(main: File) {
        listOf(main, File(main.path + "-wal"), File(main.path + "-shm"), File(main.path + "-journal")).forEach { it.delete() }
    }

    private fun workDir(): File = File(context.cacheDir, "backup-work").apply {
        deleteRecursively()
        mkdirs()
    }

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private fun fileName(epochMillis: Long): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(clock.zone)
        return "Invision-backup-${FILE_TIME.format(time)}.zip"
    }

    companion object {
        private val BACKUP_LOCK = Mutex()
        private const val PREFS = "backup"
        private const val KEY_LAST_BACKUP = "last_backup_millis"
        const val PREF_ACCENT = "accent"
        const val PREF_DARK = "dark_mode"
        const val PREF_RESTAURANT_MENU = "restaurant_menu"
        const val PREF_SUPPLIERS = "suppliers"
        private val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
    }
}
