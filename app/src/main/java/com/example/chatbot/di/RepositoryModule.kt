package com.example.chatbot.di

import com.example.chatbot.data.repository.ChatRepository
import com.example.chatbot.data.repository.OllamaChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(implementation: OllamaChatRepository): ChatRepository
}
