package com.healthtracker.app.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** User preferences, kept in one place so screens don't reach into SharedPreferences directly. */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ── Theme ─────────────────────────────────────────────────────────────────

    var themeMode: ThemeMode
        get() = ThemeMode.from(prefs.getString(KEY_THEME, null))
        set(value) {
            prefs.edit().putString(KEY_THEME, value.key).apply()
            apply(value)
        }

    // ── Units ─────────────────────────────────────────────────────────────────

    var unitSystem: UnitSystem
        get() = UnitSystem.from(prefs.getString(KEY_UNITS, null))
        set(value) = prefs.edit().putString(KEY_UNITS, value.key).apply()

    // ── Health Connect step source ────────────────────────────────────────────

    /**
     * Package name to read steps from, or null for "everything, de-duplicated".
     * Pinning one source is what stops a phone pedometer being added on top of a
     * watch's count when Health Connect has no priority order configured.
     */
    var stepSourcePackage: String?
        get() = prefs.getString(KEY_STEP_SOURCE, null)
        set(value) = prefs.edit().putString(KEY_STEP_SOURCE, value).apply()

    // ── Language ──────────────────────────────────────────────────────────────

    /** BCP-47 tag, or null to follow the system language. */
    var languageTag: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
            applyLanguage(value)
        }

    fun applyStoredTheme() = apply(themeMode)

    companion object {
        private const val NAME = "settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_UNITS = "unit_system"
        private const val KEY_STEP_SOURCE = "step_source_package"
        private const val KEY_LANGUAGE = "language_tag"

        fun apply(mode: ThemeMode) {
            AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        }

        fun applyLanguage(tag: String?) {
            val locales = if (tag.isNullOrBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}

enum class ThemeMode(val key: String, val nightMode: Int) {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class UnitSystem(val key: String) {
    METRIC("metric"),
    IMPERIAL("imperial");

    val isMetric get() = this == METRIC

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: METRIC
    }
}
