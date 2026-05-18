package com.example.chatbot.di

import com.example.chatbot.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Named("ollama_base_url")
    fun provideOllamaBaseUrl(): String = BuildConfig.OLLAMA_BASE_URL

    @Provides
    @Named("ollama_model")
    fun provideOllamaModel(): String = "qwen2.5:7b"

    @Provides
    @Singleton
    @Named("ollama_chat_url")
    fun provideOllamaChatUrl(@Named("ollama_base_url") baseUrl: String): String {
        val normalized = baseUrl.trimEnd('/')
        require(normalized.toHttpUrlOrNull() != null) { "Invalid Ollama base URL: $baseUrl" }
        return "$normalized/api/chat"
    }
}
