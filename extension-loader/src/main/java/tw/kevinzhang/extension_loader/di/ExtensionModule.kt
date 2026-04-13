package tw.kevinzhang.extension_loader.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.extension_loader.ExtensionLoaderImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtensionModule {

    @Binds
    @Singleton
    abstract fun bindExtensionLoader(impl: ExtensionLoaderImpl): ExtensionLoader
}
