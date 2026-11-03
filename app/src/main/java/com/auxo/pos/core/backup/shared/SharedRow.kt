// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A name as people read it: "customerLedger" becomes "customer ledger". */
fun plainName(name: String): String =
    name.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }.lowercase()

/** A problem in a shared backup that stops the restore. The message is plain text for the person restoring. */
class RestoreProblem(message: String) : IllegalArgumentException(message)

/**
 * One row of a table in the file. Each accessor names the table, the row and the field when something is missing
 * or has the wrong kind of value, so the message says where the file is wrong.
 */
class SharedRow(private val table: String, private val number: Int, private val values: Map<String, Any?>) {

    private fun fail(key: String, problem: String): Nothing =
        throw RestoreProblem("In row $number of ${plainName(table)}, \"${plainName(key)}\" $problem.")

    /** The shared ID of this row. */
    fun id(): String = string("id")

    fun string(key: String): String = values[key] as? String ?: fail(key, "is missing")

    fun stringOrNull(key: String): String? = when (val value = values[key]) {
        null -> null
        is String -> value
        else -> fail(key, "is not text")
    }

    fun bool(key: String): Boolean = values[key] as? Boolean ?: fail(key, "is missing")

    fun int(key: String): Int {
        val value = values[key] as? Long ?: fail(key, "is missing")
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) fail(key, "is too large")
        return value.toInt()
    }

    fun intOrNull(key: String): Int? = if (values[key] == null) null else int(key)

    fun long(key: String): Long = values[key] as? Long ?: fail(key, "is missing")

    fun longOrNull(key: String): Long? = if (values[key] == null) null else long(key)

    /** Money is written as text of whole paise, for example "1250". */
    fun money(key: String): Long = moneyOrNull(key) ?: fail(key, "is missing")

    fun moneyOrNull(key: String): Long? {
        val text = stringOrNull(key) ?: return null
        return text.toLongOrNull() ?: fail(key, "is not an amount in paise")
    }

    /** Dates are written as YYYY-MM-DD. The result is the day number counted from 1970-01-01. */
    fun epochDay(key: String): Long = epochDayOrNull(key) ?: fail(key, "is missing")

    fun epochDayOrNull(key: String): Long? {
        val text = stringOrNull(key) ?: return null
        return try {
            LocalDate.parse(text).toEpochDay()
        } catch (e: DateTimeParseException) {
            fail(key, "is not a date in the form YYYY-MM-DD")
        }
    }

    /** A link to another record, written as that record's shared ID. */
    fun ref(key: String): String = refOrNull(key) ?: fail(key, "is missing")

    /** An optional link; blank means no link. */
    fun refOrNull(key: String): String? = stringOrNull(key)?.takeIf { it.isNotEmpty() }

    /** A nested record, such as a seller or a cart line snapshot, or null when the file leaves it out. */
    fun record(key: String): SharedRow? = when (val value = values[key]) {
        null -> null
        is Map<*, *> -> SharedRow("$table.$key", number, value.entries.associate { (k, v) -> k as String to v })
        else -> fail(key, "is not a record")
    }

    fun requireRecord(key: String): SharedRow = record(key) ?: fail(key, "is missing")
}
