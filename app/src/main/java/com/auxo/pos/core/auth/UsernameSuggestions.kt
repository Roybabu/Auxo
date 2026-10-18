package com.auxo.pos.core.auth

import java.util.Locale

/**
 * Username ideas built only from the name the person typed, such as "sample.user" from "Sample User". They are never checked
 * against saved accounts, so the setup form cannot be used to find out which usernames exist.
 */
object UsernameSuggestions {
    private const val MAX = 3

    fun from(name: String): List<String> {
        val words = AdminSetupValidator.normalizeName(name)
            .lowercase(Locale.ROOT)
            .split(' ')
            .map { word -> word.filter { it in 'a'..'z' || it in '0'..'9' } }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val first = words.first()
        val last = words.last()
        val candidates = if (words.size == 1) {
            listOf(first, first + "1", first + ".pos")
        } else {
            listOf("$first.$last", "$first$last", "${first}_$last", "${first.first()}$last")
        }
        return candidates.filter { AdminSetupValidator.isUsername(it) }.distinct().take(MAX)
    }
}
