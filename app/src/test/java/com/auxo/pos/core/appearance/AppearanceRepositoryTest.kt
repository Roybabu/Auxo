// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.appearance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun startsWithTheOriginalGreenLightLook() {
        val settings = SharedPrefsAppearanceRepository(context).settings.value
        assertEquals(AppAccent.EMERALD, settings.accent)
        assertFalse(settings.darkMode)
    }

    @Test
    fun choices_areRememberedNextTime() {
        val repository = SharedPrefsAppearanceRepository(context)
        repository.setAccent(AppAccent.VIOLET)
        repository.setDarkMode(true)
        assertEquals(AppearanceSettings(AppAccent.VIOLET, darkMode = true), repository.settings.value)

        val reopened = SharedPrefsAppearanceRepository(context).settings.value
        assertEquals(AppAccent.VIOLET, reopened.accent)
        assertTrue(reopened.darkMode)
    }

    @Test
    fun changingOneChoice_keepsTheOther() {
        val repository = SharedPrefsAppearanceRepository(context)
        repository.setDarkMode(true)
        repository.setAccent(AppAccent.ROSE)
        assertTrue(repository.settings.value.darkMode)
        repository.setDarkMode(false)
        assertEquals(AppAccent.ROSE, repository.settings.value.accent)
    }

    @Test
    fun anUnknownSavedColour_fallsBackToTheDefault() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("accent", "gone").commit()
        assertEquals(AppAccent.DEFAULT, SharedPrefsAppearanceRepository(context).settings.value.accent)
        assertEquals(AppAccent.TEAL, AppAccent.fromId("teal"))
        assertEquals(AppAccent.DEFAULT, AppAccent.fromId(null))
    }
}
