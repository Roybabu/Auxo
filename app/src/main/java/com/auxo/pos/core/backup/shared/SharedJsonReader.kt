// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

/**
 * Reads the JSON of a shared backup into maps, lists, text, whole numbers (Long), decimals (Double), booleans and null.
 * It is strict: a malformed file, a repeated name or trailing text is refused rather than guessed at.
 */
object SharedJsonReader {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpace()
        require(reader.atEnd()) { "There is extra text after the backup data." }
        return value
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipSpace()
            require(i < s.length) { "The backup data ends too early." }
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                i++
                return out
            }
            while (true) {
                skipSpace()
                require(peek() == '"') { "A name in the backup data is not written correctly." }
                val key = str()
                require(!out.containsKey(key)) { "The name \"$key\" is repeated in the backup data." }
                skipSpace()
                expect(':')
                out[key] = value()
                skipSpace()
                val c = next()
                if (c == '}') return out
                require(c == ',') { "The backup data is not written correctly." }
            }
        }

        private fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            skipSpace()
            if (peek() == ']') {
                i++
                return out
            }
            while (true) {
                out.add(value())
                skipSpace()
                val c = next()
                if (c == ']') return out
                require(c == ',') { "The backup data is not written correctly." }
            }
        }

        private fun str(): String {
            i++
            val out = StringBuilder()
            while (true) {
                require(i < s.length) { "A text in the backup data is not closed." }
                val c = s[i++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        require(i < s.length) { "A text in the backup data is not closed." }
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> out.append(e)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                require(i + 4 <= s.length) { "A text in the backup data is not closed." }
                                out.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("A text in the backup data has a bad escape.")
                        }
                    }
                    c < ' ' -> throw IllegalArgumentException("A text in the backup data has a control character.")
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val token = s.substring(start, i)
            return token.toLongOrNull() ?: token.toDoubleOrNull()
                ?: throw IllegalArgumentException("A number in the backup data is not written correctly.")
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, i)) { "The backup data is not written correctly." }
            i += word.length
            return value
        }

        private fun expect(c: Char) {
            require(next() == c) { "The backup data is not written correctly." }
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun next(): Char {
            require(i < s.length) { "The backup data ends too early." }
            return s[i++]
        }
    }
}
