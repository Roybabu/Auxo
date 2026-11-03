// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a version 1 database that already holds a customer (as a phone in use does) to version 2.
 * The customer must survive unchanged, and the shared-ID link table must exist and refuse a second link.
 */
@RunWith(AndroidJUnit4::class)
class SharedIdLinkMigrationDeviceTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AuxoDatabase::class.java)

    private fun upgradedPhoneWithACustomer(name: String) = run {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO customers (name, created_at, updated_at) VALUES ('Asha', 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, Migrations.MIGRATION_1_2)
    }

    @Test
    fun upgradeKeepsTheExistingCustomer_andLinksASharedId() {
        val db = upgradedPhoneWithACustomer("upgrade-keeps-data.db")
        try {
            db.query("SELECT name FROM customers").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Asha", cursor.getString(0))
            }
            db.execSQL("INSERT INTO shared_id_links (table_name, local_id, shared_id) VALUES ('customers', 1, 'shared-1')")
            db.query("SELECT shared_id FROM shared_id_links WHERE table_name = 'customers' AND local_id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("shared-1", cursor.getString(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun aSharedId_cannotBeLinkedToTwoRecords() {
        val db = upgradedPhoneWithACustomer("unique-shared-id.db")
        try {
            db.execSQL("INSERT INTO shared_id_links (table_name, local_id, shared_id) VALUES ('customers', 1, 'shared-1')")
            try {
                db.execSQL("INSERT INTO shared_id_links (table_name, local_id, shared_id) VALUES ('customers', 2, 'shared-1')")
                fail("The same shared ID was linked twice.")
            } catch (expected: SQLiteConstraintException) {
                // Refused, as intended.
            }
        } finally {
            db.close()
        }
    }
}
