// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Remembers the shared ID (the fixed ID used in shared backup files and by the web app) of a record that came from
 * a shared backup, so the record keeps that ID when it is exported again. One row per record, keyed by table and local id.
 */
@Entity(
    tableName = "shared_id_links",
    primaryKeys = ["table_name", "local_id"],
    indices = [Index(value = ["shared_id"], unique = true)],
)
data class SharedIdLinkEntity(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "local_id") val localId: Long,
    @ColumnInfo(name = "shared_id") val sharedId: String,
)
