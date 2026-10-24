// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.locale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aNewInstall_followsThePhone() {
        assertNull(SharedPrefsLanguageRepository(context).appLanguage.value)
        assertNull(SharedPrefsLanguageRepository.saved(context))
    }

    @Test
    fun theChoice_isSaved_andSurvivesARestart() {
        val repository = SharedPrefsLanguageRepository(context)
        repository.setAppLanguage(Language.HINDI)
        assertEquals(Language.HINDI, repository.appLanguage.value)
        assertEquals(Language.HINDI, SharedPrefsLanguageRepository(context).appLanguage.value)
        assertEquals(Language.HINDI, SharedPrefsLanguageRepository.saved(context))
    }

    @Test
    fun choosingThePhone_forgetsTheChoice() {
        val repository = SharedPrefsLanguageRepository(context)
        repository.setAppLanguage(Language.BENGALI)
        repository.setAppLanguage(null)
        assertNull(repository.appLanguage.value)
        assertNull(SharedPrefsLanguageRepository.saved(context))
    }

    @Test
    fun anUnknownSavedValue_isIgnored() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putString("app_language", "xx").commit()
        assertNull(SharedPrefsLanguageRepository(context).appLanguage.value)
    }

    @Test
    fun aWrappedContext_readsItsStringsInTheChosenLanguage() {
        val hindi = LocaleContext.wrap(context, Language.HINDI)
        assertEquals("hi", hindi.resources.configuration.locales[0].language)
        val bengali = LocaleContext.wrap(context, Language.BENGALI)
        assertEquals("bn", bengali.resources.configuration.locales[0].language)
        // The original is left alone.
        assertEquals("en", LocaleContext.wrap(context, Language.ENGLISH).resources.configuration.locales[0].language)
    }

    @Test
    fun withNoChoice_theContextIsUsedAsItIs() {
        assertSame(context, LocaleContext.wrapForSaved(context))
    }

    @Test
    fun withAChoice_theSavedLanguageIsApplied() {
        SharedPrefsLanguageRepository(context).setAppLanguage(Language.HINDI)
        assertEquals("hi", LocaleContext.wrapForSaved(context).resources.configuration.locales[0].language)
    }

    @Test
    fun theInMemoryVersion_behavesTheSame() {
        val repository = InMemoryLanguageRepository()
        assertNull(repository.appLanguage.value)
        repository.setAppLanguage(Language.BENGALI)
        assertEquals(Language.BENGALI, repository.appLanguage.value)
    }
}
