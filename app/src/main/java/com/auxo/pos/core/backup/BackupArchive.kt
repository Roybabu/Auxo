package com.auxo.pos.core.backup

import com.auxo.pos.core.photos.PhotoFiles
import com.auxo.pos.data.local.DatabaseEncryption
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Why a backup or restore could not finish. */
enum class BackupProblem {
    /** The file is not an Auxo backup. */
    NOT_A_BACKUP,

    /** The file looks like a backup, but from a different app. */
    WRONG_APP,

    /** The backup was made by a newer version of Auxo than this one. */
    NEWER_BACKUP,

    /** The backup is damaged or incomplete. */
    CORRUPT,

    /** The file could not be opened. */
    UNREADABLE,

    /** The backup file could not be saved on the phone. */
    SAVE_FAILED,

    /** The data on this phone could not be replaced (it is put back as it was). */
    RESTORE_FAILED,

    /** The backup is locked with a passphrase and none was given. */
    PASSPHRASE_NEEDED,

    /** The passphrase did not open the backup, or the file is damaged at its start. */
    WRONG_PASSPHRASE,

    /** Backups are set to be locked, but the passphrase cannot be read on this phone any more. */
    PASSPHRASE_UNAVAILABLE,
}

class BackupException(val problem: BackupProblem, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: problem.name, cause)

/** What a backup says about itself. */
data class BackupManifest(
    val app: String = BackupArchive.APP_ID,
    val format: Int = BackupArchive.FORMAT,
    val createdAtMillis: Long,
    val databaseVersion: Int,
    val appVersion: String,
)

/** A backup opened into a work folder, ready to be checked and restored. */
class ExtractedBackup(
    val manifest: BackupManifest,
    val database: File,
    val preferences: Map<String, String>,
    /** Product photo files unpacked from the backup (empty for a backup without photos). */
    val photos: List<File> = emptyList(),
    /** The key that opens [database] when the backup holds a locked database file; null for a plain one. */
    val databaseKey: String? = null,
)

/**
 * The backup file: one zip holding the database (every product, bill, customer and setting
 * entered in the app), the appearance choices, the product photos (`photos/<name>.jpg`) and a small manifest that
 * says which app and version made it. The format number only goes up when old versions could not read a file; an
 * older version simply ignores the photo entries.
 */
object BackupArchive {
    const val APP_ID = "auxo"
    const val FORMAT = 1

    const val MANIFEST_ENTRY = "manifest.properties"
    const val DATABASE_ENTRY = "auxo.db"
    const val DATABASE_KEY_ENTRY = "database.key"
    const val PREFERENCES_ENTRY = "appearance.properties"

    /** Largest database accepted, so a damaged or hostile file cannot fill the phone. */
    const val MAX_DATABASE_BYTES = 1L shl 30
    private const val MAX_TEXT_BYTES = 64L * 1024

    fun write(
        out: OutputStream,
        manifest: BackupManifest,
        database: File,
        preferences: Map<String, String>,
        photos: List<File> = emptyList(),
        databaseKey: String? = null,
    ) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(toText(manifestProperties(manifest)).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            database.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            if (databaseKey != null) {
                zip.putNextEntry(ZipEntry(DATABASE_KEY_ENTRY))
                zip.write(databaseKey.toByteArray(Charsets.US_ASCII))
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry(PREFERENCES_ENTRY))
            zip.write(toText(Properties().apply { preferences.forEach { (k, v) -> setProperty(k, v) } }).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for (photo in photos) {
                if (!PhotoFiles.isValidName(photo.name) || !photo.isFile) continue
                zip.putNextEntry(ZipEntry(PhotoFiles.backupEntry(photo.name)))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads a backup from [input], unpacking the database and the photos into [workDir]. Only the known
     * entries are read (photo entries only with a well-formed name), and nothing is ever written to a path
     * taken from the file.
     */
    fun read(input: InputStream, workDir: File): ExtractedBackup {
        workDir.mkdirs()
        var manifestText: String? = null
        var preferencesText: String? = null
        var keyText: String? = null
        var database: File? = null
        val photos = mutableListOf<File>()
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        MANIFEST_ENTRY -> manifestText = zip.readText(MAX_TEXT_BYTES)
                        PREFERENCES_ENTRY -> preferencesText = zip.readText(MAX_TEXT_BYTES)
                        DATABASE_KEY_ENTRY -> keyText = zip.readText(MAX_TEXT_BYTES)
                        DATABASE_ENTRY -> {
                            val target = File(workDir, DATABASE_ENTRY)
                            target.outputStream().use { out -> zip.copyLimited(out, MAX_DATABASE_BYTES) }
                            database = target
                        }
                        else -> {
                            val photoName = PhotoFiles.nameFromBackupEntry(entry.name)
                            if (photoName != null && photos.size < PhotoFiles.MAX_FILES) {
                                val target = File(File(workDir, "photos").apply { mkdirs() }, photoName)
                                target.outputStream().use { out -> zip.copyLimited(out, PhotoFiles.MAX_FILE_BYTES) }
                                photos += target
                            }
                        }
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: IOException) {
            throw BackupException(BackupProblem.NOT_A_BACKUP, cause = e)
        }

        val properties = Properties()
        try {
            properties.load(StringReader(manifestText ?: throw BackupException(BackupProblem.NOT_A_BACKUP)))
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupProblem.NOT_A_BACKUP, cause = e)
        }
        val app = properties.getProperty("app") ?: throw BackupException(BackupProblem.NOT_A_BACKUP)
        if (app != APP_ID) throw BackupException(BackupProblem.WRONG_APP)
        val format = properties.getProperty("format")?.toIntOrNull() ?: throw BackupException(BackupProblem.NOT_A_BACKUP)
        if (format > FORMAT) throw BackupException(BackupProblem.NEWER_BACKUP)
        val manifest = BackupManifest(
            app = app,
            format = format,
            createdAtMillis = properties.getProperty("created")?.toLongOrNull() ?: 0L,
            databaseVersion = properties.getProperty("database_version")?.toIntOrNull() ?: throw BackupException(BackupProblem.CORRUPT),
            appVersion = properties.getProperty("app_version").orEmpty(),
        )
        val file = database ?: throw BackupException(BackupProblem.CORRUPT)

        val databaseKey = keyText?.trim()?.also { if (!DatabaseEncryption.isValidKey(it)) throw BackupException(BackupProblem.CORRUPT) }

        val preferences = Properties()
        preferencesText?.let { preferences.load(StringReader(it)) }
        return ExtractedBackup(
            manifest = manifest,
            database = file,
            preferences = preferences.stringPropertyNames().associateWith { preferences.getProperty(it) },
            photos = photos,
            databaseKey = databaseKey,
        )
    }

    private fun manifestProperties(manifest: BackupManifest) = Properties().apply {
        setProperty("app", manifest.app)
        setProperty("format", manifest.format.toString())
        setProperty("created", manifest.createdAtMillis.toString())
        setProperty("database_version", manifest.databaseVersion.toString())
        setProperty("app_version", manifest.appVersion)
    }

    private fun toText(properties: Properties): String = StringWriter().also { properties.store(it, null) }.toString()

    private fun ZipInputStream.readText(limit: Long): String {
        val bytes = java.io.ByteArrayOutputStream()
        copyLimited(bytes, limit)
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun InputStream.copyLimited(out: OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw BackupException(BackupProblem.CORRUPT, "A part of the file is too large.")
            out.write(buffer, 0, read)
        }
    }

    private const val DEFAULT_BUFFER = 16 * 1024
}
