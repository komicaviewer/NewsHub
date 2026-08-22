package tw.kevinzhang.newshub.repo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import tw.kevinzhang.marketplace.RepositoryCredentialProvider
import tw.kevinzhang.marketplace.RepositoryCredentialStore

@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {
    @Binds
    @Singleton
    abstract fun bindRepoRepository(impl: RepoRepositoryImpl): RepoRepository

    @Binds
    @Singleton
    abstract fun bindRepositoryCredentialStore(
        impl: RepositoryCredentialVault,
    ): RepositoryCredentialStore

    @Binds
    @Singleton
    abstract fun bindRepositoryCredentialProvider(
        impl: RepositoryCredentialVault,
    ): RepositoryCredentialProvider
}
