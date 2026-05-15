package com.example.chatbot.di

import android.content.Context
import com.example.chatbot.data.local.realm.ChatMessageEntity
import com.example.chatbot.data.local.realm.ChatSessionEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.realm.kotlin.RealmConfiguration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealmModule {

    @Provides
    @Singleton
    fun provideRealmConfiguration(@ApplicationContext context: Context): RealmConfiguration {
        return RealmConfiguration.Builder(
            schema = setOf(ChatSessionEntity::class, ChatMessageEntity::class),
        )
            .name("chatbot.realm")
            .schemaVersion(1)
            .deleteRealmIfMigrationNeeded()
            .directory(context.filesDir.absolutePath)
            .build()
    }
}
