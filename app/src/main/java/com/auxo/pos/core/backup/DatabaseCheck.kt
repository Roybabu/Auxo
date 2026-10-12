package com.auxo.pos.core.backup

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File

/** Checks that a database file from a backup is sound before it replaces the real one. */
object DatabaseCheck {

    /** Tables every Auxo database has. A file without them is not an Auxo backup. */
    private val REQUIRED_TABLES = setOf("products", "product_variants", "invoices", "invoice_lines", "payments", "store_settings", "customers")

    /**
     * Throws a [BackupException] unless [file] is an intact SQLite database with Auxo's
     * tables, at a version up to [currentVersion]. Older versions are fine: they are brought
     * up to date the next time the app opens the database.
     */
    fun verify(file: File, currentVersion: Int) {
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                if (db.version > currentVersion) throw BackupException(BackupProblem.NEWER_BACKUP)
                if (db.version < 1) throw BackupException(BackupProblem.NOT_A_BACKUP)

                val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                if (!tables.containsAll(REQUIRED_TABLES)) throw BackupException(BackupProblem.NOT_A_BACKUP)

                val result = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (result != "ok") throw BackupException(BackupProblem.CORRUPT, "The database failed its integrity check.")
            }
        } catch (e: SQLiteException) {
            throw BackupException(BackupProblem.CORRUPT, cause = e)
        }
    }
}
