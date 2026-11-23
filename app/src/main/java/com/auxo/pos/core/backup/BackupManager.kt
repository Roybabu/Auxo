// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup

import com.auxo.pos.core.debuglog.DebugLog
import android.content.Context
import android.net.Uri
import com.auxo.pos.core.appearance.AppAccent
import com.auxo.pos.core.appearance.AppearanceRepository
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.DatabaseEncryption
import com.auxo.pos.data.local.DatabaseKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import androidx.core.content.edit

/** Making and restoring backups of everything entered in the app. */
interface BackupService {
    /** Saves a backup in the Auxo folder and returns where it went. */
    suspend fun backUp(): SavedBackup

    /**
     * Replaces everything on this phone with the backup in [source]. The app must be restarted
     * afterwards. Nothing is changed if the file is not a sound Auxo backup. A locked backup needs its
     * [passphrase]; without it this throws [BackupProblem.PASSPHRASE_NEEDED].
     */
    suspend fun restore(source: Uri, passphrase: String? = null)

    /** Whether new backups are locked with a passphrase. */
    fun isProtected(): Boolean

    /** Locks new backups with [passphrase]. Older backups keep the way they were made. */
    fun setPassphrase(passphrase: String)

    /** Stops locking new backups. */
    fun clearPassphrase()

    /** When the last backup was made on this phone, or null if none has been. */
    fun lastBackupMillis(): Long?
}

class BackupManager(
    private val context: Context,
    private val database: AuxoDatabase,
    private val appearance: AppearanceRepository,
    private val storage: BackupStorage,
    private val clock: Clock,
    private val shop: com.auxo.pos.core.shop.ShopPreferences? = null,
    private val photos: com.auxo.pos.core.photos.ProductPhotoStore? = null,
    private val passphrases: BackupPassphraseStore? = null,
    private val databaseKeys: DatabaseKeyStore? = null,
) : BackupService {

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun lastBackupMillis(): Long? = prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 }

    override suspend fun backUp(): SavedBackup = withContext(Dispatchers.IO) {
        // A manual and an automatic backup share one working folder, so only one may run at a time.
        BACKUP_LOCK.withLock {
            try {
                makeBackup().also { DebugLog.event("Backup", "Backup saved (${it.fileName})") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.error("Backup", "Backup failed", e)
                throw e
            }
        }
    }

    override fun isProtected(): Boolean = passphrases?.isSet() == true

    override fun setPassphrase(passphrase: String) {
        val store = passphrases ?: throw BackupException(BackupProblem.PASSPHRASE_UNAVAILABLE)
        store.set(passphrase)
    }

    override fun clearPassphrase() {
        passphrases?.clear()
    }

    private fun makeBackup(): SavedBackup {
        // Every backup is locked. With no passphrase, or one that cannot be read, nothing is saved: an unlocked
        // backup could not be restored, and would hold the database key in the open.
        val passphrase = passphrases?.takeIf { it.isSet() }?.get() ?: throw BackupException(BackupProblem.PASSPHRASE_UNAVAILABLE)
        val work = workDir()
        try {
            val snapshot = File(work, BackupArchive.DATABASE_ENTRY)
            snapshotDatabase(snapshot)
            // A locked database file is only useful with its key, so the key travels inside the backup (which is
            // itself locked when a passphrase is set).
            val databaseKey = if (databaseKeys != null && !DatabaseEncryption.isPlaintext(snapshot)) databaseKeys.key() else null
            val now = clock.millis()
            val manifest = BackupManifest(
                createdAtMillis = now,
                databaseVersion = AuxoDatabase.VERSION,
                appVersion = appVersion(),
                shopName = shopName(),
                products = count("products"),
                customers = count("customers"),
                invoices = count("invoices"),
            )
            val settings = appearance.settings.value
            val saved = storage.save(fileName(now, locked = true)) { out ->
                BackupArchive.write(
                    out = BackupCrypto.encrypt(out, passphrase),
                    manifest = manifest,
                    database = snapshot,
                    databaseKey = databaseKey,
                    photos = photos?.files().orEmpty(),
                    preferences = buildMap {
                        put(PREF_ACCENT, settings.accent.id)
                        put(PREF_DARK, settings.darkMode.toString())
                        shop?.let { put(PREF_RESTAURANT_MENU, it.restaurantMenu.value.toString()) }
                        shop?.let { put(PREF_SUPPLIERS, it.suppliers.value.toString()) }
                        shop?.let { put(PREF_CREDIT_HOLD_DAYS, it.creditHoldDays.value.toString()) }
                        shop?.let { put(PREF_ON_TIME_BONUS, it.onTimeBonusPoints.value.toString()) }
                    },
                )
            }
            prefs.edit { putLong(KEY_LAST_BACKUP, now) }
            return saved
        } finally {
            work.deleteRecursively()
        }
    }

    override suspend fun restore(source: Uri, passphrase: String?) = withContext(Dispatchers.IO) {
        val input = runCatching { context.contentResolver.openInputStream(source) }
            .getOrElse { throw BackupException(BackupProblem.UNREADABLE, cause = it) }
            ?: throw BackupException(BackupProblem.UNREADABLE)
        try {
            input.use { restoreFrom(it, passphrase) }
            DebugLog.event("Backup", "Backup restored")
        } catch (e: Exception) {
            DebugLog.error("Backup", "Restore failed", e)
            throw e
        }
    }

    /** The restore itself, from any stream; [restore] opens the chosen file and calls this. */
    fun restoreFrom(input: InputStream, passphrase: String? = null) {
        val work = workDir()
        try {
            val backup = BackupArchive.read(unlocked(input, passphrase), work)
            DatabaseCheck.verify(backup.database, AuxoDatabase.VERSION, backup.databaseKey)

            // Keep what is on the phone now until the new data is in place.
            val current = context.getDatabasePath(AuxoDatabase.NAME)
            val safety = File(context.filesDir, "pre-restore").apply { mkdirs() }.resolve(AuxoDatabase.NAME)
            if (current.exists()) {
                checkpoint()
                current.copyTo(safety, overwrite = true)
            }

            database.close()
            try {
                deleteDatabaseFiles(current)
                backup.database.copyTo(current, overwrite = true)
                // The restored file opens only with the key it came with. A plain file needs none: it is locked
                // with this phone's own key the next time the app opens.
                val restoredKey = backup.databaseKey
                if (restoredKey != null && !DatabaseEncryption.isPlaintext(current)) databaseKeys?.replace(restoredKey)
            } catch (e: Exception) {
                com.auxo.pos.core.debuglog.DebugLog.error("BackupManager", "Failed and showed an error", e)
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
            backup.preferences[PREF_CREDIT_HOLD_DAYS]?.toIntOrNull()?.let { shop?.setCreditHoldDays(it) }
            backup.preferences[PREF_ON_TIME_BONUS]?.toIntOrNull()?.let { shop?.setOnTimeBonusPoints(it) }

            // The photos go with the data they belong to. A backup without photos clears the old ones, because the
            // restored products do not refer to them.
            photos?.let { runCatching { it.replaceAll(backup.photos) } }
        } finally {
            work.deleteRecursively()
        }
    }

    /** [input] as a plain backup: opened with [passphrase] if the file is locked, passed through if it is not. */
    private fun unlocked(input: InputStream, passphrase: String?): InputStream {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        val head = ByteArray(BackupCrypto.SIGNATURE_BYTES)
        buffered.mark(head.size)
        var filled = 0
        while (filled < head.size) {
            val read = buffered.read(head, filled, head.size - filled)
            if (read < 0) break
            filled += read
        }
        buffered.reset()
        if (!BackupCrypto.looksEncrypted(head, filled)) {
            // An unlocked backup holds the database key next to the data, so it is never restored. A zip says so; anything else is no backup.
            val looksLikeZip = filled >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
            throw BackupException(if (looksLikeZip) BackupProblem.NOT_LOCKED else BackupProblem.NOT_A_BACKUP)
        }
        if (passphrase.isNullOrEmpty()) throw BackupException(BackupProblem.PASSPHRASE_NEEDED)
        return BackupCrypto.decrypt(buffered, passphrase)
    }

    /** Writes everything still waiting in the database's log into the main file, then copies it. */
    private fun snapshotDatabase(target: File) {
        checkpoint()
        context.getDatabasePath(AuxoDatabase.NAME).copyTo(target, overwrite = true)
    }

    /** The shop's name for the list of backups; blank if it cannot be read. */
    private fun shopName(): String = runCatching {
        database.openHelper.readableDatabase.query("SELECT store_name FROM store_settings WHERE id = 1").use { if (it.moveToFirst()) it.getString(0).orEmpty().trim() else "" }
    }.getOrDefault("")

    /** Rows in [table], or null if it cannot be counted. [table] is always one of this file's own names. */
    private fun count(table: String): Int? = runCatching {
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { if (it.moveToFirst()) it.getInt(0) else null }
    }.getOrNull()

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

    private fun fileName(epochMillis: Long, locked: Boolean): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(clock.zone)
        return "Auxo-backup-${FILE_TIME.format(time)}${if (locked) BackupCrypto.FILE_EXTENSION else ".zip"}"
    }

    companion object {
        private val BACKUP_LOCK = Mutex()
        private const val PREFS = "backup"
        private const val KEY_LAST_BACKUP = "last_backup_millis"
        const val PREF_ACCENT = "accent"
        const val PREF_DARK = "dark_mode"
        const val PREF_RESTAURANT_MENU = "restaurant_menu"
        const val PREF_SUPPLIERS = "suppliers"
        const val PREF_CREDIT_HOLD_DAYS = "credit_hold_days"
        const val PREF_ON_TIME_BONUS = "on_time_bonus_points"
        private val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
    }
}
