package tw.kevinzhang.newshub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import tw.kevinzhang.newshub.BuildConfig
import tw.kevinzhang.extension_api.HostBrokerProvider
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.newshub.auth.SourceSessionManager
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().run {
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor().apply {
                    redactHeader("Authorization")
                    redactHeader("Cookie")
                    redactHeader("Set-Cookie")
                }
                addInterceptor(logging.setLevel(HttpLoggingInterceptor.Level.HEADERS))
            }
            readTimeout(10, TimeUnit.SECONDS)
            writeTimeout(10, TimeUnit.SECONDS)
            build()
        }
    }

    @Provides
    @Singleton
    fun provideHostBrokerProvider(manager: SourceSessionManager): HostBrokerProvider = manager

    @Provides
    @Singleton
    fun provideHostResourceProvider(manager: SourceSessionManager): HostResourceProvider = manager

}
