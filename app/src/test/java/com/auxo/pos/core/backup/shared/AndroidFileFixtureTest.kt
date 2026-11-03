// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A shared file made by Android's export and opened by the web app (e2e/android-backup.auxoshared, passphrase
 * "long shared phrase 91"). It keeps the Android export and the web restore in agreement.
 */
class AndroidFileFixtureTest {

    private fun androidFile(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("shared/android-backup.auxoshared")!!.use { it.readBytes() }

    @Test
    fun aFileMadeByAndroid_makesAPlan_withItsShopProductAndCustomer() {
        val plan = SharedRestorePlanner.plan(SharedBackupFile.open(androidFile(), "long shared phrase 91"))

        assertEquals("Shop", plan.shopName)
        assertEquals(1, plan.rowCounts["products"])
        assertEquals(1, plan.rowCounts["customers"])
        assertEquals("0ef8510d-4737-5df9-a9a6-d31b7418ae9b", plan.products[0].sharedId)
        assertEquals("d1469d77-6036-544b-95bf-8c88f53bae97", plan.customers[0].sharedId)
    }

    /**
     * Android's re-export of the web app's rich file, restored by the web app in a real browser. The plan is built by the
     * device round trip (it needs Android's JSON library), so this checks that the file opens and holds every table.
     */
    @Test
    fun theRichAndroidFile_opensWithEveryFilledTable() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("shared/android-rich.auxoshared")!!.use { it.readBytes() }
        val doc = SharedBackupFile.open(bytes, "long shared phrase 91")
        assertEquals("Sample Shop", doc.shopName)
        assertEquals("android", doc.sourcePlatform)
        assertEquals(2, doc.rowCount("variants"))
        assertEquals(1, doc.rowCount("stockBatches"))
        assertEquals(1, doc.rowCount("numberSequences"))
        assertEquals(1, doc.photos.size)
    }
}
