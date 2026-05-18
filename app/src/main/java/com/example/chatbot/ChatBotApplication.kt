package com.example.chatbot

import android.app.Application
import com.example.chatbot.ui.theme.ThemeModePreferences
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChatBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeModePreferences.applyPersistedMode(this)
    }
}
