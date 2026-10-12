package com.auxo.pos.core.locale

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.auxo.pos.domain.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The language the person chose for the app on this device. Null means "follow the phone", which is what a new
 * install does. This is only about the app's own screens: the language bills are printed in is a shop setting.
 */
interface LanguageRepository {
    val appLanguage: StateFlow<Language?>
    fun setAppLanguage(language: Language?)
}

class InMemoryLanguageRepository(initial: Language? = null) : LanguageRepository {
    private val state = MutableStateFlow(initial)
    override val appLanguage: StateFlow<Language?> = state.asStateFlow()
    override fun setAppLanguage(language: Language?) {
        state.value = language
    }
}

/** Saves the choice on the device. */
class SharedPrefsLanguageRepository(private val prefs: SharedPreferences) : LanguageRepository {

    constructor(context: Context) : this(preferencesOf(context))

    private val state = MutableStateFlow(read(prefs))
    override val appLanguage: StateFlow<Language?> = state.asStateFlow()

    override fun setAppLanguage(language: Language?) {
        prefs.edit().apply {
            if (language == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, language.tag)
        }.apply()
        state.value = language
    }

    companion object {
        private const val FILE = "language"
        private const val KEY_LANGUAGE = "app_language"

        private fun preferencesOf(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        private fun read(prefs: SharedPreferences): Language? = Language.fromTag(prefs.getString(KEY_LANGUAGE, null))

        /** The saved choice, read straight from the device. Used before the app is set up, when a screen is created. */
        fun saved(context: Context): Language? = read(preferencesOf(context))
    }
}

/** Puts the app's screens in a chosen language. */
object LocaleContext {

    /** [base] with its resources in [language], so every string the screens read comes from that language. */
    fun wrap(base: Context, language: Language): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(language.locale)
        configuration.setLayoutDirection(language.locale)
        return base.createConfigurationContext(configuration)
    }

    /** [base] in the person's chosen language; unchanged when they follow the phone, which the system already handles. */
    fun wrapForSaved(base: Context): Context {
        val chosen = SharedPrefsLanguageRepository.saved(base) ?: return base
        return wrap(base, chosen)
    }
}
