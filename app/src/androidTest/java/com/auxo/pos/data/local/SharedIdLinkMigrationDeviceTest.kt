// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the version 1 to 2 migration against the database Room builds for version 2. The migration must leave
 * the shared-ID link table exactly as Room expects it, with its unique index.
 */
@RunWith(AndroidJUnit4::class)
class SharedIdLinkMigrationDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "shared-id-link-migration.db"

    @Before
    fun removeAnyOldFile() {
        context.deleteDatabase(name)
    }

    @After
    fun removeTheFile() {
        context.deleteDatabase(name)
    }

    /** The link table as SQLite reports it: its columns, then its indexes. */
    private fun shapeOf(db: SupportSQLiteDatabase): List<String> {
        val shape = mutableListOf<String>()
        db.query("PRAGMA table_info(`shared_id_links`)").use { cursor ->
            while (cursor.moveToNext()) shape += (0 until cursor.columnCount).joinToString("|") { "${cursor.getString(it)}" }
        }
        db.query("PRAGMA index_list(`shared_id_links`)").use { cursor ->
            while (cursor.moveToNext()) shape += (0 until cursor.columnCount).joinToString("|") { "${cursor.getString(it)}" }
        }
        return shape
    }

    /** Drops the link table Room just made, then runs the migration in its place. */
    private fun replaceLinkTableWithMigration(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_shared_id_links_shared_id`")
        db.execSQL("DROP TABLE `shared_id_links`")
        Migrations.MIGRATION_1_2.migrate(db)
    }

    @Test
    fun theMigration_buildsTheSameLinkTableThatRoomBuildsForVersion2() {
        val room = AuxoDatabase.builder(context, name).build()
        val db = room.openHelper.writableDatabase
        val expected = shapeOf(db)
        assertTrue(expected.isNotEmpty())

        replaceLinkTableWithMigration(db)

        assertEquals(expected, shapeOf(db))
        room.close()
    }

    @Test
    fun aSharedId_cannotBeLinkedToTwoRecords_afterTheMigration() {
        val room = AuxoDatabase.builder(context, name).build()
        val db = room.openHelper.writableDatabase
        replaceLinkTableWithMigration(db)

        db.execSQL("INSERT INTO shared_id_links (table_name, local_id, shared_id) VALUES ('customers', 1, 'shared-1')")
        try {
            db.execSQL("INSERT INTO shared_id_links (table_name, local_id, shared_id) VALUES ('customers', 2, 'shared-1')")
            fail("The same shared ID was linked twice.")
        } catch (expected: SQLiteConstraintException) {
            // Refused, as intended.
        }
        room.close()
    }
}
