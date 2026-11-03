// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import com.auxo.pos.core.backup.SharedBackupContainer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedBackupReaderTest {

    private fun tables(): Map<String, List<Map<String, Any?>>> =
        SharedFile.TABLES.associateWith { name -> if (name == "settings") listOf(mapOf<String, Any?>("id" to "s1")) else emptyList() }

    private fun fileOf(json: String, passphrase: String = "a long passphrase"): ByteArray =
        SharedBackupContainer.encrypt(json.toByteArray(Charsets.UTF_8), passphrase)

    private fun failureOf(block: () -> Unit): String? =
        runCatching(block).exceptionOrNull()?.takeIf { it is IllegalArgumentException }?.message

    @Test
    fun aWrittenBackup_readsBack_withItsShopPhotosAndRows() {
        val photo = mapOf<String, Any?>("id" to "p1", "mimeType" to "image/jpeg", "dataBase64" to java.util.Base64.getEncoder().encodeToString("hello".toByteArray()))
        val json = SharedFile.toJson(SharedFile.assemble(tables(), shopName = "Test Shop", createdAt = 123L, appVersion = "1.0", photos = listOf(photo)))

        val doc = SharedBackupReader.read(fileOf(json), "a long passphrase")

        assertEquals("Test Shop", doc.shopName)
        assertEquals(123L, doc.createdAtMillis)
        assertEquals("1.0", doc.appVersion)
        assertEquals("android", doc.sourcePlatform)
        assertEquals(1, doc.rowCount("settings"))
        assertEquals(0, doc.rowCount("invoices"))
        assertEquals(1, doc.photos.size)
        assertArrayEquals("hello".toByteArray(), doc.photos[0].bytes)
    }

    @Test
    fun aWrongPassphrase_isReported_withoutReadingAnything() {
        val json = SharedFile.toJson(SharedFile.assemble(tables(), shopName = "Shop", createdAt = 1L, appVersion = "1.0"))
        val reason = failureOf { SharedBackupReader.read(fileOf(json), "not the passphrase") }
        assertTrue(reason != null && reason.contains("Wrong passphrase"))
    }

    @Test
    fun aMissingTable_isNamed_andRefused() {
        val partial = tables().filterKeys { it != "invoices" }
        val json = SharedFile.toJson(mapOf(
            "format" to "auxo-backup", "version" to 1, "sharedModelVersion" to 1, "createdAt" to 1L,
            "source" to mapOf("platform" to "web", "appVersion" to "1"), "shopName" to "Shop",
            "tables" to partial, "photos" to emptyList<Any?>(),
        ))
        assertEquals("The backup is missing the invoices table.", failureOf { SharedBackupReader.read(fileOf(json), "a long passphrase") })
    }

    @Test
    fun aFileFromAnotherFormat_isRefused() {
        val reason = failureOf { SharedBackupReader.check(SharedJsonReader.parse("""{"format":"something-else"}""")) }
        assertEquals("This file is not an Auxo backup.", reason)
    }

    @Test
    fun theJsonReader_readsEscapes_numbers_andLiterals() {
        val parsed = SharedJsonReader.parse("""{"a":"x\"yA","b":[1,-2,null,true,false]}""") as Map<*, *>
        assertEquals("x\"yA", parsed["a"])
        assertEquals(listOf(1L, -2L, null, true, false), parsed["b"])
    }

    @Test
    fun theJsonReader_refusesRepeatedNames_andTrailingText() {
        assertTrue(failureOf { SharedJsonReader.parse("""{"a":1,"a":2}""") } != null)
        assertTrue(failureOf { SharedJsonReader.parse("""{"a":1} x""") } != null)
    }
}
