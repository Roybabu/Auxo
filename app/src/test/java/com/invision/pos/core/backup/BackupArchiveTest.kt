package com.invision.pos.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val manifest = BackupManifest(createdAtMillis = 1_700_000_000_000, databaseVersion = 3, appVersion = "0.4.0")

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun problemOf(bytes: ByteArray): BackupProblem? = try {
        BackupArchive.read(ByteArrayInputStream(bytes), folder.newFolder())
        null
    } catch (e: BackupException) {
        e.problem
    }

    private val goodManifest = "app=invision\nformat=1\ncreated=5\ndatabase_version=3\napp_version=0.4.0\n"

    @Test
    fun writtenBackup_readsBackWithEverythingIntact() {
        val database = folder.newFile("source.db").apply { writeBytes(ByteArray(5000) { (it % 251).toByte() }) }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, manifest, database, mapOf("accent" to "violet", "dark_mode" to "true"))

        val read = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), folder.newFolder())

        assertEquals(manifest, read.manifest)
        assertArrayEquals(database.readBytes(), read.database.readBytes())
        assertEquals(mapOf("accent" to "violet", "dark_mode" to "true"), read.preferences)
    }

    @Test
    fun aFileThatIsNotAZip_isNotABackup() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf("just some text".toByteArray()))
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf(ByteArray(0)))
    }

    @Test
    fun aZipWithoutAManifest_isNotABackup() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf(zipOf("invision.db" to "x")))
    }

    @Test
    fun aBackupFromAnotherApp_isRefused() {
        val other = goodManifest.replace("app=invision", "app=other")
        assertEquals(BackupProblem.WRONG_APP, problemOf(zipOf("manifest.properties" to other, "invision.db" to "x")))
    }

    @Test
    fun aBackupFromANewerFormat_isRefused() {
        val newer = goodManifest.replace("format=1", "format=2")
        assertEquals(BackupProblem.NEWER_BACKUP, problemOf(zipOf("manifest.properties" to newer, "invision.db" to "x")))
    }

    @Test
    fun aBackupWithoutItsDatabase_isCorrupt() {
        assertEquals(BackupProblem.CORRUPT, problemOf(zipOf("manifest.properties" to goodManifest)))
    }

    @Test
    fun aManifestWithoutAVersion_isCorrupt() {
        val noVersion = "app=invision\nformat=1\n"
        assertEquals(BackupProblem.CORRUPT, problemOf(zipOf("manifest.properties" to noVersion, "invision.db" to "x")))
    }

    @Test
    fun entryNamesNeverChooseWhereFilesAreWritten() {
        val work = folder.newFolder()
        val bytes = zipOf(
            "manifest.properties" to goodManifest,
            "invision.db" to "db",
            "../../evil.txt" to "nope",
            "/etc/evil.txt" to "nope",
        )
        BackupArchive.read(ByteArrayInputStream(bytes), work)
        assertEquals(listOf("invision.db"), work.list()!!.toList())
        assertTrue(!java.io.File(work.parentFile.parentFile, "evil.txt").exists())
    }
}
