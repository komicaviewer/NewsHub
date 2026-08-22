package tw.kevinzhang.newshub.auth.oauth

import okhttp3.HttpUrl
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.newshub.auth.sourceStorageKey
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies a Host-injected Bearer header only for the bound provider's exact API hosts. */
@Singleton
class OAuthCredentialProvider @Inject constructor(
    private val registry: OAuthProviderRegistry,
    private val tokenVault: OAuthTokenVault,
    private val httpClient: OAuthHttpClient,
) {
    fun hasCredential(identity: SourceIdentity): Boolean =
        tokenVault.credential(sourceStorageKey(identity))?.let { credential ->
            credential.sourceId == identity.sourceId && credential.sourceStorageKey == sourceStorageKey(identity)
        } == true

    @Synchronized
    fun authorizationHeader(
        identity: SourceIdentity,
        targetUrl: HttpUrl,
        nowEpochMillis: Long = System.currentTimeMillis(),
        forceRefresh: Boolean = false,
    ): String? {
        val storageKey = sourceStorageKey(identity)
        val current = tokenVault.credential(storageKey) ?: return null
        if (current.sourceId != identity.sourceId || current.sourceStorageKey != storageKey) return null
        val resolved = runCatching {
            registry.resolve(current.providerId, current.clientRegistrationId, current.scopes)
        }.getOrNull() ?: return null
        if (!resolved.adapter.acceptsApiUrl(targetUrl)) return null

        val credential = if (!forceRefresh &&
            current.expiresAtEpochMillis > nowEpochMillis + OAUTH_REFRESH_SKEW_MILLIS
        ) {
            current
        } else {
            refresh(current, resolved, nowEpochMillis) ?: return null
        }
        if (!credential.tokenType.equals("bearer", ignoreCase = true) ||
            credential.accessToken.isBlank() || '\r' in credential.accessToken || '\n' in credential.accessToken
        ) return null
        return "Bearer ${credential.accessToken}"
    }

    private fun refresh(
        current: StoredOAuthCredential,
        resolved: ResolvedOAuthProvider,
        nowEpochMillis: Long,
    ): StoredOAuthCredential? {
        val refreshToken = current.refreshToken ?: return null
        return runCatching {
            val body = httpClient.exchange(
                resolved.adapter.tokenRefreshRequest(resolved.registration, refreshToken),
            )
            val token = resolved.adapter.parseTokenResponse(
                responseBody = body,
                requestedScopes = current.scopes,
                previousRefreshToken = refreshToken,
                nowEpochMillis = nowEpochMillis,
            )
            current.copy(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                tokenType = token.tokenType,
                scopes = token.scopes,
                expiresAtEpochMillis = token.expiresAtEpochMillis,
            ).also(tokenVault::put)
        }.getOrNull()
    }
}
