package tw.kevinzhang.newshub.auth.oauth

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.newshub.auth.sourceStorageKey
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuth1Coordinator @Inject constructor(
    private val registry: OAuth1ProviderRegistry,
    private val transactions: OAuth1TransactionStore,
    private val tokenVault: OAuth1TokenVault,
    private val httpClient: OAuthHttpClient,
) {
    /** Routes provider-denied callbacks without teaching the Activity provider-specific paths. */
    fun acceptsRedirect(uri: Uri): Boolean = transactions.hasCallback(uri.toString())

    suspend fun begin(identity: SourceIdentity, spec: AuthSpec.OAuth1): OAuthAuthorizationLaunch =
        withContext(Dispatchers.IO) {
            val resolved = registry.resolve(spec)
            tokenVault.remove(sourceStorageKey(identity))
            val requestToken = parseRequestToken(
                httpClient.exchange(
                    resolved.adapter.requestTokenRequest(resolved.registration, OAuth1Signer()),
                ),
                requireCallbackConfirmed = true,
            )
            transactions.put(
                OAuth1Transaction(
                    requestToken = requestToken.token,
                    requestTokenSecret = requestToken.tokenSecret,
                    providerId = resolved.adapter.providerId,
                    clientRegistrationId = resolved.registration.id,
                    identity = identity,
                    callbackUri = resolved.registration.callbackUri,
                    expiresAtEpochMillis = System.currentTimeMillis() + OAUTH1_TRANSACTION_TTL_MILLIS,
                ),
            )
            OAuthAuthorizationLaunch(
                sourceId = identity.sourceId,
                authorizationUri = resolved.adapter.authorizationUri(
                    resolved.registration,
                    requestToken.token,
                ),
            )
        }

    suspend fun handleRedirect(uri: Uri): OAuth1Completion = withContext(Dispatchers.IO) {
        val requestToken = runCatching { uri.getQueryParameter("oauth_token") }.getOrNull()
            ?.takeIf { it.length in 1..4_096 && '\r' !in it && '\n' !in it }
            ?: return@withContext OAuth1Completion.Failure(
                sourceId = transactions.consumeCallback(uri.toString())?.identity?.sourceId,
                userMessage = "登入未完成或已取消，請重新登入。",
            )
        val transaction = transactions.consume(requestToken)
            ?: return@withContext OAuth1Completion.Failure(null, "登入要求已過期或已使用，請重新登入。")
        val sourceId = transaction.identity.sourceId
        runCatching {
            require(oauth1CallbackMatches(uri.toString(), transaction.callbackUri)) {
                "OAuth 1 callback URI mismatch"
            }
            val verifier = requireNotNull(uri.getQueryParameter("oauth_verifier")) {
                "OAuth 1 callback omitted verifier"
            }.also { require(it.length in 1..4_096 && '\r' !in it && '\n' !in it) }
            val resolved = registry.resolve(transaction.providerId, transaction.clientRegistrationId)
            val access = parseRequestToken(
                httpClient.exchange(
                    resolved.adapter.accessTokenRequest(
                        registration = resolved.registration,
                        requestToken = transaction.requestToken,
                        requestTokenSecret = transaction.requestTokenSecret,
                        verifier = verifier,
                        signer = OAuth1Signer(),
                    ),
                ),
            )
            tokenVault.put(
                StoredOAuth1Credential(
                    sourceStorageKey = sourceStorageKey(transaction.identity),
                    sourceId = sourceId,
                    providerId = resolved.adapter.providerId,
                    clientRegistrationId = resolved.registration.id,
                    accessToken = access.token,
                    accessTokenSecret = access.tokenSecret,
                ),
            )
        }.fold(
            onSuccess = { OAuth1Completion.Success(transaction.identity) },
            onFailure = { OAuth1Completion.Failure(sourceId, "無法完成 OAuth 1 登入，請稍後重新嘗試。") },
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

internal fun parseRequestToken(
    responseBody: String,
    requireCallbackConfirmed: Boolean = false,
): OAuth1RequestToken {
    require(responseBody.length <= 16_384) { "OAuth 1 token response is too large" }
    val pairs = responseBody.split('&').map { part ->
        val index = part.indexOf('=')
        require(index > 0) { "Invalid OAuth 1 token response" }
        decodeFormPart(part.substring(0, index)) to decodeFormPart(part.substring(index + 1))
    }
    require(pairs.map(Pair<String, String>::first).distinct().size == pairs.size) {
        "OAuth 1 token response contains duplicate fields"
    }
    val values = pairs.toMap()
    if (requireCallbackConfirmed) {
        require(values["oauth_callback_confirmed"] == "true") { "OAuth 1 callback was not confirmed" }
    }
    val token = requireNotNull(values["oauth_token"]) { "OAuth 1 token response omitted token" }
    val secret = requireNotNull(values["oauth_token_secret"]) { "OAuth 1 token response omitted secret" }
    require(token.length in 1..4_096 && secret.length in 1..4_096)
    require('\r' !in token && '\n' !in token && '\r' !in secret && '\n' !in secret)
    return OAuth1RequestToken(token, secret)
}

internal fun oauth1CallbackMatches(actualValue: String, expectedValue: String): Boolean = runCatching {
    val actual = URI(actualValue)
    val expected = URI(expectedValue)
    actual.scheme.equals(expected.scheme, ignoreCase = true) &&
        actual.host.equals(expected.host, ignoreCase = true) &&
        actual.port == expected.port &&
        actual.rawPath == expected.rawPath &&
        actual.rawUserInfo == null &&
        actual.rawFragment == null
}.getOrDefault(false)

private fun decodeFormPart(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
