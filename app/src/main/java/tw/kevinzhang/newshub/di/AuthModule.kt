package tw.kevinzhang.newshub.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.extension_api.SourceContext
import tw.kevinzhang.newshub.auth.AndroidSourceContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindSourceContext(impl: AndroidSourceContext): SourceContext
}
