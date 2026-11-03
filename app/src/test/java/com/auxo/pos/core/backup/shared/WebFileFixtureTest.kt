// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shared file made by the web app (e2e/backup.auxoshared, passphrase "long shared phrase 91"), restored on Android
 * as a plan. The built-in categories must carry the same fixed IDs on both apps, or they would be duplicated.
 */
class WebFileFixtureTest {

    private fun webFile(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("shared/web-backup.auxoshared")!!.use { it.readBytes() }

    @Test
    fun aFileMadeByTheWebApp_makesAPlan_withItsShopAndBuiltInCategories() {
        val plan = SharedRestorePlanner.plan(SharedBackupFile.open(webFile(), "long shared phrase 91"))

        assertEquals("E2E Shop", plan.shopName)
        assertEquals("E2E Shop", plan.settings.storeName)
        assertEquals(12, plan.categories.size)
        assertEquals(4, plan.auditLog.size)
        assertTrue(plan.categories.all { it.links.single().first == "category" })
    }

    @Test
    fun theBuiltInCategories_haveTheSameIdsAsOnAndroid() {
        val plan = SharedRestorePlanner.plan(SharedBackupFile.open(webFile(), "long shared phrase 91"))
        assertTrue(plan.categories.map { it.sharedId }.contains("21698313-7bd8-56ea-9c69-9ebe0fc84a82"))
        assertEquals(12, plan.categories.map { it.sharedId }.toSet().size)
    }
}
