package tw.kevinzhang.newshub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import tw.kevinzhang.newshub.BuildConfig
import tw.kevinzhang.newshub.auth.AppCookieJar
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: AppCookieJar): OkHttpClient {
        return OkHttpClient.Builder().run {
            cookieJar(cookieJar)
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor()
                addInterceptor(logging.setLevel(HttpLoggingInterceptor.Level.HEADERS))
            }
            readTimeout(10, TimeUnit.SECONDS)
            writeTimeout(10, TimeUnit.SECONDS)
            build()
        }
    }

}
