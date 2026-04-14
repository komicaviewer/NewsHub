package tw.kevinzhang.collection.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.collection.CollectionRepository
import tw.kevinzhang.collection.CollectionRepositoryImpl
import tw.kevinzhang.collection.ReadingHistoryRepository
import tw.kevinzhang.collection.SavedPostRepository
import tw.kevinzhang.collection.data.CollectionDatabase
import tw.kevinzhang.collection.data.CollectionDao
import tw.kevinzhang.collection.data.ReadingHistoryDao
import tw.kevinzhang.collection.data.SavedPostDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CollectionModule {

    @Binds
    @Singleton
    abstract fun bindCollectionRepository(impl: CollectionRepositoryImpl): CollectionRepository

    @Binds
    @Singleton
    abstract fun bindReadingHistoryRepository(impl: CollectionRepositoryImpl): ReadingHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSavedPostRepository(impl: CollectionRepositoryImpl): SavedPostRepository

    companion object {
        @Provides
        @Singleton
        fun provideCollectionDatabase(@ApplicationContext context: Context): CollectionDatabase =
            Room.databaseBuilder(context, CollectionDatabase::class.java, "collection.db")
                .fallbackToDestructiveMigration()
                .build()

        @Provides
        fun provideCollectionDao(db: CollectionDatabase): CollectionDao = db.collectionDao()

        @Provides
        fun provideReadingHistoryDao(db: CollectionDatabase): ReadingHistoryDao = db.readingHistoryDao()

        @Provides
        fun provideSavedPostDao(db: CollectionDatabase): SavedPostDao = db.savedPostDao()
    }
}
