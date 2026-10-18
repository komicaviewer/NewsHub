package tw.kevinzhang.newshub.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import tw.kevinzhang.newshub.BuildConfig
import tw.kevinzhang.newshub.github.*
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import tw.kevinzhang.newshub.repo.RepoRepository
import tw.kevinzhang.newshub.repo.RepositoryAccessStatusStore
import tw.kevinzhang.marketplace.MarketplaceRepository

@Module
@InstallIn(SingletonComponent::class)
object GitHubRepositoryModule {
    @Provides @Singleton fun http() = GitHubHttp(OkHttpClient())
    @Provides @Singleton fun catalog(http: GitHubHttp) = GitHubRepositoryCatalog(http)
    @Provides @Singleton fun oauth(http: GitHubHttp) = GitHubOAuthClient(http, BuildConfig.GITHUB_OAUTH_CLIENT_ID)
    @Provides @Singleton fun vault(@ApplicationContext context: Context): GitHubAccountStorage = GitHubAccountVault(context)
    @Provides @Singleton fun session(oauth: GitHubOAuthClient, catalog: GitHubRepositoryCatalog, vault: GitHubAccountStorage) =
        GitHubAccountSession(oauth, catalog, vault)
    @Provides @Singleton fun monitor(@ApplicationScope scope: CoroutineScope, session: GitHubAccountSession,
        repo: RepoRepository, marketplace: MarketplaceRepository, catalog: GitHubRepositoryCatalog,
        statuses: RepositoryAccessStatusStore) = GitHubRepositoryAccessMonitor(scope, session.state,
        repo.getRepositoryDomains(), session::currentCredential, { domain, token ->
            val identity = catalog.getAccount(token)
            check(identity.id == session.state.value.account?.id)
            marketplace.verifyRepositoryAccess(domain.id, token)
        }, statuses)
}
