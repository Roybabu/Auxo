// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.core.backup.SharedBackupContainer
import java.util.Base64

/** A shared backup that was decrypted and checked. Nothing from it has been written to the phone yet. */
class SharedBackupDocument internal constructor(
    val shopName: String,
    val createdAtMillis: Long,
    val appVersion: String,
    val sourcePlatform: String,
    val tables: Map<String, List<Map<String, Any?>>>,
    val photos: List<Photo>,
) {
    class Photo(val id: String, val mimeType: String, val bytes: ByteArray)

    fun rowCount(table: String): Int = tables[table]?.size ?: 0
}

/**
 * Opens a shared backup: decrypts it, reads the JSON, and checks the header and every table is present.
 * Each problem is reported in plain words, so the screen can say what is wrong with the file.
 */
object SharedBackupReader {
    private const val FORMAT = "auxo-backup"
    private const val VERSION = 1L

    fun read(file: ByteArray, passphrase: String): SharedBackupDocument {
        val json = String(SharedBackupContainer.decrypt(file, passphrase), Charsets.UTF_8)
        return check(SharedJsonReader.parse(json))
    }

    fun check(root: Any?): SharedBackupDocument {
        val doc = root as? Map<*, *> ?: fail("The backup data is not a backup.")
        if (doc["format"] != FORMAT) fail("This file is not an Auxo backup.")
        if (doc["version"] != VERSION || doc["sharedModelVersion"] != VERSION) {
            fail("This backup uses a format version this app does not read.")
        }
        val rawTables = doc["tables"] as? Map<*, *> ?: fail("The backup has no tables.")
        val tables = SharedFile.TABLES.associateWith { name ->
            val rows = rawTables[name] as? List<*> ?: fail("The backup is missing the $name table.")
            rows.mapIndexed { index, row ->
                val record = row as? Map<*, *> ?: fail("Row ${index + 1} of $name is not a record.")
                record.entries.associate { (key, value) -> key as String to value }
            }
        }
        val shopName = doc["shopName"] as? String ?: fail("The backup has no shop name.")
        val createdAt = doc["createdAt"] as? Long ?: fail("The backup has no creation time.")
        val source = doc["source"] as? Map<*, *>
        val photos = (doc["photos"] as? List<*> ?: emptyList<Any?>()).mapIndexed { index, item ->
            val photo = item as? Map<*, *> ?: fail("Photo ${index + 1} is not a record.")
            val id = photo["id"] as? String ?: fail("Photo ${index + 1} has no id.")
            val data = photo["dataBase64"] as? String ?: fail("Photo $id has no picture.")
            val bytes = try {
                Base64.getDecoder().decode(data)
            } catch (e: IllegalArgumentException) {
                fail("Photo $id is damaged.")
            }
            SharedBackupDocument.Photo(id, photo["mimeType"] as? String ?: "image/jpeg", bytes)
        }
        return SharedBackupDocument(
            shopName = shopName,
            createdAtMillis = createdAt,
            appVersion = source?.get("appVersion") as? String ?: "",
            sourcePlatform = source?.get("platform") as? String ?: "unknown",
            tables = tables,
            photos = photos,
        )
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
}
