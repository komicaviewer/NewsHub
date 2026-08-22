package tw.kevinzhang.newshub.auth.oauth

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.newshub.auth.sourceStorageKey
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuthCoordinator @Inject constructor(
    private val registry: OAuthProviderRegistry,
    private val transactions: OAuthTransactionStore,
    private val tokenVault: OAuthTokenVault,
    private val httpClient: OAuthHttpClient,
) {
    fun begin(identity: SourceIdentity, spec: AuthSpec.OAuth): OAuthAuthorizationLaunch {
        val resolved = registry.resolve(spec)
        // An explicit re-login revokes the old Host-held credential before a new transaction.
        tokenVault.remove(sourceStorageKey(identity))
        val state = OAuthSecurity.newState()
        val verifier = OAuthSecurity.newCodeVerifier()
        val transaction = OAuthTransaction(
            state = state,
            codeVerifier = verifier,
            providerId = resolved.adapter.providerId,
            clientRegistrationId = resolved.registration.id,
            scopes = resolved.scopes,
            identity = identity,
            redirectUri = resolved.registration.redirectUri,
            expiresAtEpochMillis = System.currentTimeMillis() + OAUTH_TRANSACTION_TTL_MILLIS,
        )
        transactions.put(transaction)
        return OAuthAuthorizationLaunch(
            sourceId = identity.sourceId,
            authorizationUri = resolved.adapter.authorizationUri(
                registration = resolved.registration,
                scopes = resolved.scopes,
                state = state,
                codeChallenge = OAuthSecurity.codeChallenge(verifier),
            ),
        )
    }

    suspend fun handleRedirect(uri: Uri): OAuthCompletion = withContext(Dispatchers.IO) {
        val state = runCatching { uri.getQueryParameter("state") }.getOrNull()
            ?.takeIf { it.length in 32..128 }
            ?: return@withContext OAuthCompletion.Failure(null, "登入回傳缺少有效驗證狀態。")
        val transaction = transactions.consume(state)
            ?: return@withContext OAuthCompletion.Failure(null, "登入要求已過期或已使用，請重新登入。")

        val sourceId = transaction.identity.sourceId
        runCatching {
            val resolved = registry.resolve(
                transaction.providerId,
                transaction.clientRegistrationId,
                transaction.scopes,
            )
            require(oauthCallbackMatches(uri.toString(), transaction.redirectUri)) { "OAuth callback URI mismatch" }
            val providerError = uri.getQueryParameter("error")
            require(providerError == null) { "OAuth authorization was denied" }
            val code = requireNotNull(uri.getQueryParameter("code")) { "OAuth callback omitted code" }
                .also { require(it.length in 1..4_096 && '\r' !in it && '\n' !in it) }
            val now = System.currentTimeMillis()
            val responseBody = httpClient.exchange(
                resolved.adapter.tokenExchangeRequest(resolved.registration, code, transaction.codeVerifier),
            )
            val token = resolved.adapter.parseTokenResponse(
                responseBody = responseBody,
                requestedScopes = resolved.scopes,
                previousRefreshToken = null,
                nowEpochMillis = now,
            )
            tokenVault.put(
                StoredOAuthCredential(
                    sourceStorageKey = sourceStorageKey(transaction.identity),
                    sourceId = sourceId,
                    providerId = resolved.adapter.providerId,
                    clientRegistrationId = resolved.registration.id,
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    tokenType = token.tokenType,
                    scopes = token.scopes,
                    expiresAtEpochMillis = token.expiresAtEpochMillis,
                ),
            )
        }.fold(
            onSuccess = { OAuthCompletion.Success(transaction.identity) },
            onFailure = { OAuthCompletion.Failure(sourceId, "無法完成 OAuth 登入，請稍後重新嘗試。") },
        )
    }

    fun cancel(sourceId: String) {
        transactions.removeSource(sourceId)
    }

    fun logout(identity: SourceIdentity) {
        transactions.removeSource(identity.sourceId)
        tokenVault.remove(sourceStorageKey(identity))
    }

}

internal fun oauthCallbackMatches(actualValue: String, expectedValue: String): Boolean = runCatching {
    val actual = URI(actualValue)
    val expected = URI(expectedValue)
    actual.scheme.equals(expected.scheme, ignoreCase = true) &&
        actual.host.equals(expected.host, ignoreCase = true) &&
        actual.port == expected.port &&
        actual.rawPath == expected.rawPath &&
        actual.rawUserInfo == null &&
        actual.rawFragment == null
}.getOrDefault(false)
