package com.invision.pos.core.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseCheckTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tables = listOf("products", "product_variants", "invoices", "invoice_lines", "payments", "store_settings", "customers")

    private fun database(version: Int, names: List<String> = tables): File {
        val file = File(folder.root, "db-${System.nanoTime()}.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            names.forEach { db.execSQL("CREATE TABLE $it (id INTEGER PRIMARY KEY)") }
            db.version = version
        }
        return file
    }

    private fun problemOf(file: File, current: Int = 3): BackupProblem? = try {
        DatabaseCheck.verify(file, current)
        null
    } catch (e: BackupException) {
        e.problem
    }

    @Test
    fun aSoundDatabase_atTheCurrentOrAnOlderVersion_passes() {
        assertEquals(null, problemOf(database(version = 3)))
        assertEquals(null, problemOf(database(version = 1)))
    }

    @Test
    fun aDatabaseFromANewerVersion_isRefused() {
        assertEquals(BackupProblem.NEWER_BACKUP, problemOf(database(version = 4)))
    }

    @Test
    fun aDatabaseMissingInvisionTables_isNotABackup() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf(database(version = 3, names = listOf("products"))))
    }

    @Test
    fun aVersionlessDatabase_isNotABackup() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf(database(version = 0)))
    }

    @Test
    fun aFileThatIsNotADatabase_isCorrupt() {
        val junk = folder.newFile("junk.db").apply { writeBytes(ByteArray(4096) { 7 }) }
        assertEquals(BackupProblem.CORRUPT, problemOf(junk))
        val empty = folder.newFile("empty.db")
        val problem = problemOf(empty)
        if (problem == null) fail("An empty file should not pass")
    }
}
