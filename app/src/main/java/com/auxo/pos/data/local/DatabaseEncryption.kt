// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.auxo.pos.core.debuglog.DebugLog
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the key that locks the database file is kept on this phone. The key is 64 hex characters (32 random bytes).
 */
interface DatabaseKeyStore {
    /** True once a key has been made on this phone. */
    fun isSet(): Boolean

    /**
     * The key, made and saved on first use. Throws when a key was saved but can no longer be read (for example
     * after the Keystore was reset), because making a new one would hide the problem and the data could not be opened.
     */
    fun key(): String

    /** Makes [key] this phone's key. A restored backup brings its own, and the file it holds only opens with it. */
    fun replace(key: String)
}

/**
 * Keeps the key sealed with an AES key that lives in the Android Keystore and never leaves it, so the saved text is
 * useless on its own. The preferences file is excluded from phone backups (the app turns them off).
 */
class KeystoreDatabaseKeyStore(context: Context) : DatabaseKeyStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isSet(): Boolean = prefs.contains(KEY_BLOB)

    @Synchronized
    override fun key(): String {
        val blob = prefs.getString(KEY_BLOB, null)
        if (blob != null) {
            return open(blob) ?: throw IllegalStateException("The database key can no longer be read from the Keystore.")
        }
        val fresh = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        save(fresh)
        return fresh
    }

    @Synchronized
    override fun replace(key: String) {
        require(DatabaseEncryption.isValidKey(key)) { "Not a database key." }
        save(key)
    }

    private fun save(key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val sealed = cipher.iv + cipher.doFinal(key.toByteArray(Charsets.US_ASCII))
        // Written at once, not later: the database must never be locked with a key that has not been saved.
        prefs.edit(commit = true) { putString(KEY_BLOB, Base64.encodeToString(sealed, Base64.NO_WRAP)) }
    }

    private fun open(blob: String): String? = runCatching {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.US_ASCII)
    }.getOrNull()

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "database_protection"
        const val KEY_BLOB = "key"
        const val ALIAS = "auxo_database_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * Locks the database file with SQLCipher (AES-256). A phone that already holds a plain database file is converted
 * the first time the app opens after the update: a locked copy is made, checked against the original, and only then
 * put in place of it (the plain file is overwritten with zeros first). If anything goes wrong the plain file is left
 * as it was and the app keeps using it, to be tried again next time, so a failed conversion never costs data.
 */
object DatabaseEncryption {

    private val PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val KEY_FORMAT = Regex("[0-9a-f]{64}")

    /** Tables compared before and after a conversion. A table that does not exist is skipped (on both sides). */
    private val CHECKED_TABLES = listOf("products", "product_variants", "invoices", "invoice_lines", "payments", "store_settings", "customers")

    /** True when [file] starts like an ordinary, unlocked SQLite database. A locked file starts with random bytes. */
    fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < PLAIN_HEADER.size) return false
        val head = ByteArray(PLAIN_HEADER.size)
        file.inputStream().use { if (it.read(head) != head.size) return false }
        return head.contentEquals(PLAIN_HEADER)
    }

    fun isValidKey(key: String): Boolean = KEY_FORMAT.matches(key)

    /** Loads SQLCipher's native library. False when it cannot be loaded (the database then stays unlocked). */
    val isAvailable: Boolean by lazy { runCatching { System.loadLibrary("sqlcipher") }.isSuccess }

    fun openHelperFactory(key: String): SupportSQLiteOpenHelper.Factory =
        SupportOpenHelperFactory(key.toByteArray(Charsets.US_ASCII))

    /**
     * Gets the database file ready to be opened locked, converting an unlocked one. Returns the key to open it
     * with, or null when the database must be opened unlocked this time.
     */
    fun prepare(context: Context, keys: DatabaseKeyStore): String? = try {
        if (!isAvailable) {
            DebugLog.warn("Database", "SQLCipher is not available; the database stays unlocked")
            null
        } else {
            val key = keys.key()
            val file = context.getDatabasePath(AuxoDatabase.NAME)
            if (isPlaintext(file)) convert(file, key)
            key
        }
    } catch (e: Exception) {
        DebugLog.error("Database", "Could not lock the database; it stays unlocked this time", e)
        null
    }

    private fun convert(file: File, key: String) {
        val locked = File(file.path + ".locked")
        locked.delete()
        try {
            // ATTACH makes the new locked file with the main connection's open flags, so CREATE is needed or it fails with
            // "unable to open database". The plain file already exists, so nothing else is created.
            val plain = SQLiteDatabase.openDatabase(
                file.path, "", null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null,
            )
            val (version, counts) = try {
                val schemaVersion = plain.version
                val counts = CHECKED_TABLES.associateWith { count(plain, it) }
                plain.rawExecSQL("ATTACH DATABASE '${locked.path}' AS encrypted KEY '$key'")
                plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                plain.rawExecSQL("PRAGMA encrypted.user_version = $schemaVersion")
                plain.rawExecSQL("DETACH DATABASE encrypted")
                schemaVersion to counts
            } finally {
                plain.close()
            }

            val copy = SQLiteDatabase.openDatabase(locked.path, key, null, SQLiteDatabase.OPEN_READONLY, null)
            try {
                check(copy.version == version) { "The locked copy has a different version." }
                check(text(copy, "PRAGMA integrity_check") == "ok") { "The locked copy failed its integrity check." }
                CHECKED_TABLES.forEach { table ->
                    check(count(copy, table) == counts.getValue(table)) { "The locked copy differs in $table." }
                }
            } finally {
                copy.close()
            }

            // Verified. Overwrite the plain file (and any log left beside it), then put the locked one in its place.
            sideFiles(file).forEach { wipe(it) }
            wipe(file)
            if (!locked.renameTo(file)) {
                locked.copyTo(file, overwrite = true)
                locked.delete()
            }
            DebugLog.event("Database", "Database file is now locked")
        } catch (e: Exception) {
            locked.delete()
            throw e
        }
    }

    private fun sideFiles(main: File) = listOf("-wal", "-shm", "-journal").map { File(main.path + it) }

    /** The number of rows in [table], or -1 when there is no such table. */
    private fun count(db: SQLiteDatabase, table: String): Long = try {
        val statement = db.compileStatement("SELECT COUNT(*) FROM $table")
        try {
            statement.simpleQueryForLong()
        } finally {
            statement.close()
        }
    } catch (e: RuntimeException) {
        -1L
    }

    private fun text(db: SQLiteDatabase, sql: String): String? {
        val statement = db.compileStatement(sql)
        return try {
            statement.simpleQueryForString()
        } finally {
            statement.close()
        }
    }

    private fun wipe(file: File) {
        if (!file.isFile) return
        runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                val zeros = ByteArray(8192)
                var left = raf.length()
                raf.seek(0)
                while (left > 0) {
                    val n = minOf(left, zeros.size.toLong()).toInt()
                    raf.write(zeros, 0, n)
                    left -= n
                }
                raf.fd.sync()
            }
        }
        file.delete()
    }

    /**
     * Opens [file] with [key] and returns its schema version, or throws when the key does not open it or it is not
     * sound. Used to check a locked file from a backup before it replaces the real one.
     */
    fun verifyLocked(file: File, key: String, requiredTables: Collection<String>): Int {
        check(isAvailable) { "SQLCipher is not available." }
        val db = SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READONLY, null)
        try {
            requiredTables.forEach { check(count(db, it) >= 0) { "Missing table $it." } }
            check(text(db, "PRAGMA integrity_check") == "ok") { "Failed its integrity check." }
            return db.version
        } finally {
            db.close()
        }
    }
}
