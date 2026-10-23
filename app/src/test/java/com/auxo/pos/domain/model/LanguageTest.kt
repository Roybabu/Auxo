// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageTest {

    @Test
    fun theStartingLanguages_areEnglishHindiAndBengali() {
        assertEquals(listOf("en", "hi", "bn"), Language.entries.map { it.tag })
        assertEquals(Language.ENGLISH, Language.DEFAULT)
    }

    @Test
    fun tags_areUnique_andNamedInTheirOwnLanguage() {
        assertEquals(Language.entries.size, Language.entries.map { it.tag }.toSet().size)
        assertEquals(listOf("English", "हिन्दी", "বাংলা"), Language.entries.map { it.nativeName })
    }

    @Test
    fun aSavedTag_findsItsLanguage_andAnythingElseIsNull() {
        assertEquals(Language.HINDI, Language.fromTag("hi"))
        assertEquals(Language.BENGALI, Language.fromTag("bn"))
        assertNull(Language.fromTag("ta"))
        assertNull(Language.fromTag(""))
        assertNull(Language.fromTag(null))
        assertNull(Language.fromTag("HI"))
    }

    @Test
    fun eachLanguage_hasItsLocale() {
        assertEquals(listOf("en", "hi", "bn"), Language.entries.map { it.locale.language })
    }
}
