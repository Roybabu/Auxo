// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Database upgrades. Each one keeps every existing row; the schema it produces is exported in app/schemas. */
object Migrations {
    /** Version 1 to 2: adds the table that remembers shared IDs. No existing table changes. */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `shared_id_links` (" +
                    "`table_name` TEXT NOT NULL, `local_id` INTEGER NOT NULL, `shared_id` TEXT NOT NULL, " +
                    "PRIMARY KEY(`table_name`, `local_id`))",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shared_id_links_shared_id` ON `shared_id_links` (`shared_id`)")
        }
    }
}
