package com.koval.trainingplanner.di

import com.koval.trainingplanner.BuildConfig
import com.koval.trainingplanner.data.local.TokenManager
import com.koval.trainingplanner.data.remote.api.AuthApi
import com.koval.trainingplanner.data.remote.api.CalendarApi
import com.koval.trainingplanner.data.remote.api.ChatApi
import com.koval.trainingplanner.data.remote.api.ClubApi
import com.koval.trainingplanner.data.remote.api.GoalApi
import com.koval.trainingplanner.data.remote.api.NotificationApi
import com.koval.trainingplanner.data.remote.api.SessionApi
import com.koval.trainingplanner.data.remote.api.TrainingApi
import com.koval.trainingplanner.data.remote.api.ZoneApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val token = tokenManager.getToken()
        if (token != null) {
            chain.proceed(
                request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            )
        } else {
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL + "/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideCalendarApi(retrofit: Retrofit): CalendarApi = retrofit.create(CalendarApi::class.java)

    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideClubApi(retrofit: Retrofit): ClubApi = retrofit.create(ClubApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi = retrofit.create(NotificationApi::class.java)

    @Provides
    @Singleton
    fun provideTrainingApi(retrofit: Retrofit): TrainingApi = retrofit.create(TrainingApi::class.java)

    @Provides
    @Singleton
    fun provideGoalApi(retrofit: Retrofit): GoalApi = retrofit.create(GoalApi::class.java)

    @Provides
    @Singleton
    fun provideZoneApi(retrofit: Retrofit): ZoneApi = retrofit.create(ZoneApi::class.java)

    @Provides
    @Singleton
    fun provideSessionApi(retrofit: Retrofit): SessionApi = retrofit.create(SessionApi::class.java)
}
