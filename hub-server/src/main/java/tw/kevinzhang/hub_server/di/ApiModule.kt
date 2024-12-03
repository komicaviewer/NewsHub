package tw.kevinzhang.hub_server.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import tw.kevinzhang.gamer_api.GamerApi
import tw.kevinzhang.hub_server.BuildConfig
import tw.kevinzhang.komica_api.KomicaApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object ApiModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().run {
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor()
                addInterceptor(logging.setLevel(HttpLoggingInterceptor.Level.HEADERS))
            }
            readTimeout(10, TimeUnit.SECONDS)
            writeTimeout(10, TimeUnit.SECONDS)
            build()
        }
    }

    @Provides
    @Singleton
    fun provideKomicaNewsApi(client: OkHttpClient) = KomicaApi(client)

    @Provides
    @Singleton
    fun provideGamerApi(client: OkHttpClient) = GamerApi(client)
}
