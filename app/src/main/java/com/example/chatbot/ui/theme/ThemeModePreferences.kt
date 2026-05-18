package com.example.chatbot.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemeModePreferences {
    private const val PREFS_NAME = "com.example.chatbot.theme"
    private const val KEY_USE_DARK = "use_dark_theme"

    fun applyPersistedMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = when {
            !prefs.contains(KEY_USE_DARK) -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            prefs.getBoolean(KEY_USE_DARK, false) -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun hasExplicitChoice(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_USE_DARK)

    fun isExplicitDark(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_USE_DARK, false)

    fun isDarkEnabledForSwitch(context: Context): Boolean =
        if (hasExplicitChoice(context)) {
            isExplicitDark(context)
        } else {
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }

    fun setExplicitDarkTheme(context: Context, useDark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_USE_DARK, useDark)
            }
        AppCompatDelegate.setDefaultNightMode(
            if (useDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
        )
    }
}
