package tw.kevinzhang.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.data.CollectionRepository
import tw.kevinzhang.data.CollectionRepositoryImpl
import tw.kevinzhang.data.ReadingHistoryRepository
import tw.kevinzhang.data.SavedPostRepository
import tw.kevinzhang.data.SourceIdentityRepository
import tw.kevinzhang.data.domain.CollectionDao
import tw.kevinzhang.data.domain.CollectionDatabase
import tw.kevinzhang.data.domain.PostReadDao
import tw.kevinzhang.data.domain.ReadingHistoryDao
import tw.kevinzhang.data.domain.SavedPostDao
import tw.kevinzhang.data.domain.SourceIdentityDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindCollectionRepository(impl: CollectionRepositoryImpl): CollectionRepository

    @Binds
    @Singleton
    abstract fun bindReadingHistoryRepository(impl: CollectionRepositoryImpl): ReadingHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSavedPostRepository(impl: CollectionRepositoryImpl): SavedPostRepository

    @Binds
    @Singleton
    abstract fun bindSourceIdentityRepository(impl: CollectionRepositoryImpl): SourceIdentityRepository

    companion object {
        @Provides
        @Singleton
        fun provideCollectionDatabase(@ApplicationContext context: Context): CollectionDatabase =
            Room.databaseBuilder(context, CollectionDatabase::class.java, "collection.db")
                .addMigrations(*CollectionDatabase.ALL_MIGRATIONS)
                .build()

        @Provides
        fun provideCollectionDao(db: CollectionDatabase): CollectionDao = db.collectionDao()

        @Provides
        fun provideReadingHistoryDao(db: CollectionDatabase): ReadingHistoryDao = db.readingHistoryDao()

        @Provides
        fun provideSavedPostDao(db: CollectionDatabase): SavedPostDao = db.savedPostDao()

        @Provides
        fun providePostReadDao(db: CollectionDatabase): PostReadDao = db.postReadDao()

        @Provides
        fun provideSourceIdentityDao(db: CollectionDatabase): SourceIdentityDao = db.sourceIdentityDao()
    }
}
