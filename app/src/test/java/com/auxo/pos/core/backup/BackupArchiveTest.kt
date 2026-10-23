package com.auxo.pos.core.backup

import com.auxo.pos.core.photos.PhotoFiles
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

    private val goodManifest = "app=auxo\nformat=1\ncreated=5\ndatabase_version=3\napp_version=0.4.0\n"

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

    private val goodKey = "0123456789abcdef".repeat(4)

    @Test
    fun aDatabaseKey_isWrittenAndReadBack() {
        val database = folder.newFile("source.db").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, manifest, database, emptyMap(), databaseKey = goodKey)

        val read = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), folder.newFolder())

        assertEquals(goodKey, read.databaseKey)
    }

    @Test
    fun aBackupWithoutAKey_readsWithNone() {
        val database = folder.newFile("source.db").apply { writeBytes(ByteArray(64)) }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, manifest, database, emptyMap())

        assertEquals(null, BackupArchive.read(ByteArrayInputStream(out.toByteArray()), folder.newFolder()).databaseKey)
    }

    @Test
    fun aMalformedDatabaseKey_isRejectedAsCorrupt() {
        val bytes = zipOf(
            BackupArchive.MANIFEST_ENTRY to goodManifest,
            BackupArchive.DATABASE_ENTRY to "x",
            BackupArchive.DATABASE_KEY_ENTRY to "not a key",
        )
        assertEquals(BackupProblem.CORRUPT, problemOf(bytes))
    }

    private val photoName = "0123456789abcdef0123456789abcdef.jpg"

    @Test
    fun photos_areWrittenAndReadBack() {
        val database = folder.newFile("source.db").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        val photo = java.io.File(folder.newFolder("photos"), photoName).apply { writeBytes(byteArrayOf(5, 6, 7)) }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, manifest, database, emptyMap(), photos = listOf(photo))

        val read = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), folder.newFolder())

        assertEquals(listOf(photoName), read.photos.map { it.name })
        assertArrayEquals(byteArrayOf(5, 6, 7), read.photos.single().readBytes())
    }

    @Test
    fun aBackupWithoutPhotos_readsWithNone() {
        val database = folder.newFile("source.db").apply { writeBytes(ByteArray(10)) }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, manifest, database, emptyMap())
        assertTrue(BackupArchive.read(ByteArrayInputStream(out.toByteArray()), folder.newFolder()).photos.isEmpty())
    }

    @Test
    fun photoEntriesWithOddNames_areIgnored_andNothingIsWrittenOutsideTheWorkFolder() {
        val work = folder.newFolder()
        val bytes = zipOf(
            "manifest.properties" to goodManifest,
            "auxo.db" to "db",
            "photos/../escape.jpg" to "x",
            "photos/evil.jpg" to "x",
            "photos/sub/$photoName" to "x",
            "photos/$photoName" to "ok",
        )

        val read = BackupArchive.read(ByteArrayInputStream(bytes), work)

        assertEquals(listOf(photoName), read.photos.map { it.name })
        assertEquals("ok", read.photos.single().readText())
        assertTrue(read.photos.single().canonicalPath.startsWith(work.canonicalPath))
        assertTrue(!java.io.File(work.parentFile, "escape.jpg").exists())
    }

    @Test
    fun aPhotoThatIsTooLarge_makesTheBackupCorrupt() {
        val big = "x".repeat((PhotoFiles.MAX_FILE_BYTES + 1).toInt())
        val bytes = zipOf("manifest.properties" to goodManifest, "auxo.db" to "db", "photos/$photoName" to big)
        assertEquals(BackupProblem.CORRUPT, problemOf(bytes))
    }

    @Test
    fun aFileThatIsNotAZip_isNotABackup() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf("just some text".toByteArray()))
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf(ByteArray(0)))
    }

    @Test
    fun aZipWithoutAManifest_isNotABackup() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problemOf(zipOf("auxo.db" to "x")))
    }

    @Test
    fun aNewBackup_namesItselfAuxo_andHoldsTheDatabaseUnderTheNewName() {
        val database = folder.newFile("source.db").apply { writeBytes(ByteArray(10)) }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, manifest, database, emptyMap())
        val names = java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            generateSequence { z.nextEntry }.map { it.name }.toList()
        }
        assertTrue("auxo.db" in names)
        assertEquals("auxo", BackupArchive.APP_ID)
    }

    @Test
    fun aBackupFromAnotherApp_isRefused() {
        val other = goodManifest.replace("app=auxo", "app=other")
        assertEquals(BackupProblem.WRONG_APP, problemOf(zipOf("manifest.properties" to other, "auxo.db" to "x")))
    }

    @Test
    fun aBackupFromANewerFormat_isRefused() {
        val newer = goodManifest.replace("format=1", "format=2")
        assertEquals(BackupProblem.NEWER_BACKUP, problemOf(zipOf("manifest.properties" to newer, "auxo.db" to "x")))
    }

    @Test
    fun aBackupWithoutItsDatabase_isCorrupt() {
        assertEquals(BackupProblem.CORRUPT, problemOf(zipOf("manifest.properties" to goodManifest)))
    }

    @Test
    fun aManifestWithoutAVersion_isCorrupt() {
        val noVersion = "app=auxo\nformat=1\n"
        assertEquals(BackupProblem.CORRUPT, problemOf(zipOf("manifest.properties" to noVersion, "auxo.db" to "x")))
    }

    @Test
    fun entryNamesNeverChooseWhereFilesAreWritten() {
        val work = folder.newFolder()
        val bytes = zipOf(
            "manifest.properties" to goodManifest,
            "auxo.db" to "db",
            "../../evil.txt" to "nope",
            "/etc/evil.txt" to "nope",
        )
        BackupArchive.read(ByteArrayInputStream(bytes), work)
        assertEquals(listOf("auxo.db"), work.list()!!.toList())
        assertTrue(!java.io.File(work.parentFile!!.parentFile, "evil.txt").exists())
    }
}
