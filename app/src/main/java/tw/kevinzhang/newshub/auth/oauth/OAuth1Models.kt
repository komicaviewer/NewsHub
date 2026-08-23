package tw.kevinzhang.newshub.auth.oauth

import android.net.Uri
import okhttp3.HttpUrl
import okhttp3.Request
import tw.kevinzhang.extension_api.SourceIdentity

internal const val OAUTH1_TRANSACTION_TTL_MILLIS = 10L * 60L * 1_000L

data class OAuth1ClientRegistration(
    val id: String,
    val consumerKey: String,
    val consumerSecret: String,
    val callbackUri: String,
)

data class ResolvedOAuth1Provider(
    val adapter: OAuth1ProviderAdapter,
    val registration: OAuth1ClientRegistration,
)

data class OAuth1RequestToken(
    val token: String,
    val tokenSecret: String,
)

data class StoredOAuth1Credential(
    val sourceStorageKey: String,
    val sourceId: String,
    val providerId: String,
    val clientRegistrationId: String,
    val accessToken: String,
    val accessTokenSecret: String,
)

data class OAuth1Transaction(
    val requestToken: String,
    val requestTokenSecret: String,
    val providerId: String,
    val clientRegistrationId: String,
    val identity: SourceIdentity,
    val callbackUri: String,
    val expiresAtEpochMillis: Long,
)

sealed interface OAuth1Completion {
    val sourceId: String?

    data class Success(val identity: SourceIdentity) : OAuth1Completion {
        override val sourceId: String = identity.sourceId
    }

    data class Failure(
        override val sourceId: String?,
        val userMessage: String,
    ) : OAuth1Completion
}
interface OAuth1ProviderAdapter {
    val providerId: String
    val apiExactHosts: Set<String>

    fun registration(registrationId: String): OAuth1ClientRegistration?

    fun requestTokenRequest(
        registration: OAuth1ClientRegistration,
        signer: OAuth1Signer,
    ): Request

    fun authorizationUri(registration: OAuth1ClientRegistration, requestToken: String): Uri

    fun accessTokenRequest(
        registration: OAuth1ClientRegistration,
        requestToken: String,
        requestTokenSecret: String,
        verifier: String,
        signer: OAuth1Signer,
    ): Request

    fun acceptsApiUrl(url: HttpUrl): Boolean =
        url.isHttps && url.port == 443 && url.host.lowercase() in apiExactHosts
}
