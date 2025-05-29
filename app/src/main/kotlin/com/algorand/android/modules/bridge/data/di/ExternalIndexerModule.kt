package com.algorand.android.modules.bridge.data.di

import com.algorand.android.modules.bridge.data.service.ExternalAlgorandIndexerApi
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExternalIndexerModule {

    private const val EXTERNAL_INDEXER_BASE_URL = "https://mainnet-idx.4160.nodely.dev/"
    private const val TIMEOUT_CONSTANT = 30L // Shorter timeout for external public API

    @Provides
    @Singleton
    @Named("externalIndexerOkHttpClient")
    fun provideExternalIndexerOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor // Assuming HttpLoggingInterceptor is globally provided
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // For logging, if desired
            .connectTimeout(TIMEOUT_CONSTANT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_CONSTANT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_CONSTANT, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("externalIndexerRetrofit")
    fun provideExternalIndexerRetrofit(
        @Named("externalIndexerOkHttpClient") okHttpClient: OkHttpClient,
        gson: Gson // Assuming Gson is globally provided
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(EXTERNAL_INDEXER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideExternalAlgorandIndexerApi(
        @Named("externalIndexerRetrofit") retrofit: Retrofit
    ): ExternalAlgorandIndexerApi {
        return retrofit.create(ExternalAlgorandIndexerApi::class.java)
    }
}
