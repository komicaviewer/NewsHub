package tw.kevinzhang.marketplace.di

import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.marketplace.MarketplaceRepositoryImpl
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
        fun provideGson(): Gson = Gson()
    }
}
