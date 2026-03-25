package tw.kevinzhang.extension_loader.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.extension_loader.ExtensionLoaderImpl
import tw.kevinzhang.extensions_builtin._2cat._2catSource
import tw.kevinzhang.extensions_builtin.gamer.GamerSource
import tw.kevinzhang.extensions_builtin.sora.SoraSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtensionModule {

    @Binds
    @Singleton
    abstract fun bindExtensionLoader(impl: ExtensionLoaderImpl): ExtensionLoader

    companion object {
        @Provides
        @Singleton
        fun provideBuiltInSources(
            gamer: GamerSource,
            sora: SoraSource,
            _2cat: _2catSource,
        ): List<Source> = listOf(gamer, sora, _2cat)
    }
}
