package tw.kevinzhang.newshub.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import tw.kevinzhang.newshub.auth.oauth.OAuthProviderAdapter
import tw.kevinzhang.newshub.auth.oauth.RedditOAuthProviderAdapter

@Module
@InstallIn(SingletonComponent::class)
abstract class OAuthModule {
    @Binds
    @IntoSet
    abstract fun bindRedditOAuthProvider(adapter: RedditOAuthProviderAdapter): OAuthProviderAdapter
}
