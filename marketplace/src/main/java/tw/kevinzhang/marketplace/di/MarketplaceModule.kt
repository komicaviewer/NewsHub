package tw.kevinzhang.marketplace.di

import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.marketplace.MarketplaceRepositoryImpl
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketplaceModule {

    @Binds
    @Singleton
    abstract fun bindMarketplaceRepository(impl: MarketplaceRepositoryImpl): MarketplaceRepository

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()

        @Provides
        @Named("marketplaceIndexUrl")
        fun provideIndexUrl(): String = ""  // Default empty; configurable from Settings
    }
}
