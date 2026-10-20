package com.auxo.pos.core.appearance

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * The eight colours the app can wear. Each one is a hue; the full set of Material 3 light and
 * dark colours is worked out from it in the theme, so every choice keeps readable text on
 * both white and black backgrounds. [id] is what gets saved, so never rename one.
 */
enum class AppAccent(val id: String) {
    EMERALD("emerald"),
    TEAL("teal"),
    OCEAN("ocean"),
    VIOLET("violet"),
    ORCHID("orchid"),
    ROSE("rose"),
    SUNSET("sunset"),
    SLATE("slate"),
    ;

    companion object {
        val DEFAULT = EMERALD

        /** The accent saved under [id], or the default for anything unknown. */
        fun fromId(id: String?): AppAccent = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** How the person wants the app to look. The defaults are the app's original look. */
data class AppearanceSettings(
    val accent: AppAccent = AppAccent.DEFAULT,
    val darkMode: Boolean = false,
)

/** Keeps the chosen colour and dark mode, and tells the app when they change. */
interface AppearanceRepository {
    val settings: StateFlow<AppearanceSettings>
    fun setAccent(accent: AppAccent)
    fun setDarkMode(enabled: Boolean)
}

/** Saves the choice on the device, so it is still there after the app closes. */
class SharedPrefsAppearanceRepository(private val prefs: SharedPreferences) : AppearanceRepository {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val state = MutableStateFlow(read())
    override val settings: StateFlow<AppearanceSettings> = state.asStateFlow()

    private fun read() = AppearanceSettings(
        accent = AppAccent.fromId(prefs.getString(KEY_ACCENT, null)),
        darkMode = prefs.getBoolean(KEY_DARK, false),
    )

    override fun setAccent(accent: AppAccent) {
        prefs.edit { putString(KEY_ACCENT, accent.id) }
        state.value = state.value.copy(accent = accent)
    }

    override fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK, enabled) }
        state.value = state.value.copy(darkMode = enabled)
    }

    private companion object {
        const val FILE = "appearance"
        const val KEY_ACCENT = "accent"
        const val KEY_DARK = "dark_mode"
    }
}
