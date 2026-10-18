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

@Module
@InstallIn(SingletonComponent::class)
object GitHubRepositoryModule {
    @Provides @Singleton fun http() = GitHubHttp(OkHttpClient())
    @Provides @Singleton fun catalog(http: GitHubHttp) = GitHubRepositoryCatalog(http)
    @Provides @Singleton fun oauth(http: GitHubHttp) = GitHubOAuthClient(http, BuildConfig.GITHUB_OAUTH_CLIENT_ID)
    @Provides @Singleton fun vault(@ApplicationContext context: Context): GitHubAccountStorage = GitHubAccountVault(context)
    @Provides @Singleton fun session(oauth: GitHubOAuthClient, catalog: GitHubRepositoryCatalog, vault: GitHubAccountStorage) =
        GitHubAccountSession(oauth, catalog, vault)
}
