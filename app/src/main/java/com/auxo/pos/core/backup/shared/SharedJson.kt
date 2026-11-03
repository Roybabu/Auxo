// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

/** Writes the JSON used by the shared backup: maps, lists, text, whole numbers, booleans and null. */
object SharedJson {
    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Int, is Long -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, item) ->
            quote(key as String) + ":" + encode(item)
        }
        is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") { encode(it) }
        else -> throw IllegalArgumentException("Cannot write ${value::class.java.simpleName} as JSON")
    }

    private fun quote(text: String): String {
        val out = StringBuilder("\"")
        for (ch in text) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch < ' ' -> out.append("\\u%04x".format(ch.code))
                else -> out.append(ch)
            }
        }
        return out.append('"').toString()
    }
}
