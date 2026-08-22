package tw.kevinzhang.newshub.auth.oauth

import android.net.Uri
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import tw.kevinzhang.extension_api.SourceIdentity

internal const val OAUTH_TRANSACTION_TTL_MILLIS = 10L * 60L * 1_000L
internal const val OAUTH_REFRESH_SKEW_MILLIS = 60L * 1_000L

/** Host-owned registration selected by an untrusted extension through a stable opaque id. */
data class OAuthClientRegistration(
    val id: String,
    val clientId: String,
    val redirectUri: String,
)

data class ResolvedOAuthProvider(
    val adapter: OAuthProviderAdapter,
    val registration: OAuthClientRegistration,
    val scopes: Set<String>,
)

data class OAuthAuthorizationLaunch(
    val sourceId: String,
    val authorizationUri: Uri,
)

data class OAuthTokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val scopes: Set<String>,
    val expiresAtEpochMillis: Long,
)

data class StoredOAuthCredential(
    val sourceStorageKey: String,
    val sourceId: String,
    val providerId: String,
    val clientRegistrationId: String,
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val scopes: Set<String>,
    val expiresAtEpochMillis: Long,
)

data class OAuthTransaction(
    val state: String,
    val codeVerifier: String,
    val providerId: String,
    val clientRegistrationId: String,
    val scopes: Set<String>,
    val identity: SourceIdentity,
    val redirectUri: String,
    val expiresAtEpochMillis: Long,
)

sealed interface OAuthCompletion {
    val sourceId: String?

    data class Success(val identity: SourceIdentity) : OAuthCompletion {
        override val sourceId: String = identity.sourceId
    }

    data class Failure(
        override val sourceId: String?,
        val userMessage: String,
    ) : OAuthCompletion
}

/**
 * Provider-specific behavior. Adding Facebook or Dcard is an adapter registration, not a change
 * to the transaction, callback, token-vault, or broker layers.
 */
interface OAuthProviderAdapter {
    val providerId: String
    val allowedScopes: Set<String>
    val apiExactHosts: Set<String>

    fun registration(registrationId: String): OAuthClientRegistration?

    fun authorizationUri(
        registration: OAuthClientRegistration,
        scopes: Set<String>,
        state: String,
        codeChallenge: String,
    ): Uri

    fun tokenExchangeRequest(registration: OAuthClientRegistration, code: String, codeVerifier: String): Request

    fun tokenRefreshRequest(registration: OAuthClientRegistration, refreshToken: String): Request

    fun parseTokenResponse(
        responseBody: String,
        requestedScopes: Set<String>,
        previousRefreshToken: String?,
        nowEpochMillis: Long,
    ): OAuthTokenSet

    fun acceptsApiUrl(url: HttpUrl): Boolean =
        url.isHttps && url.port == 443 && url.host.lowercase() in apiExactHosts
}

internal fun FormBody.Builder.addRequired(name: String, value: String): FormBody.Builder = apply {
    require(name.isNotBlank() && value.isNotBlank())
    add(name, value)
}
