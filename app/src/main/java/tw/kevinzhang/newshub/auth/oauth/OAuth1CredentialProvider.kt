package tw.kevinzhang.newshub.auth.oauth

import okhttp3.HttpUrl
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.newshub.auth.sourceStorageKey
import javax.inject.Inject
import javax.inject.Singleton

/** Signs each authorized OAuth 1 API request without revealing any secret to the extension. */
@Singleton
class OAuth1CredentialProvider @Inject constructor(
    private val registry: OAuth1ProviderRegistry,
    private val tokenVault: OAuth1TokenVault,
) {
    fun hasCredential(identity: SourceIdentity): Boolean =
        tokenVault.credential(sourceStorageKey(identity))?.let { credential ->
            credential.sourceId == identity.sourceId && credential.sourceStorageKey == sourceStorageKey(identity)
        } == true

    fun authorizationHeader(identity: SourceIdentity, method: String, targetUrl: HttpUrl): String? {
        val storageKey = sourceStorageKey(identity)
        val credential = tokenVault.credential(storageKey) ?: return null
        if (credential.sourceId != identity.sourceId || credential.sourceStorageKey != storageKey) return null
        val resolved = runCatching {
            registry.resolve(credential.providerId, credential.clientRegistrationId)
        }.getOrNull() ?: return null
        if (!resolved.adapter.acceptsApiUrl(targetUrl)) return null
        return OAuth1Signer().authorizationHeader(
            method = method,
            url = targetUrl,
            consumerKey = resolved.registration.consumerKey,
            consumerSecret = resolved.registration.consumerSecret,
            token = credential.accessToken,
            tokenSecret = credential.accessTokenSecret,
        )
    }
}
