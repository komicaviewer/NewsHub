package tw.kevinzhang.newshub.auth.oauth

import tw.kevinzhang.extension_api.AuthSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuth1ProviderRegistry @Inject constructor(
    adapters: Set<@JvmSuppressWildcards OAuth1ProviderAdapter>,
) {
    private val adaptersById = adapters.associateBy(OAuth1ProviderAdapter::providerId).also { indexed ->
        require(indexed.size == adapters.size) { "Duplicate OAuth 1 provider adapter" }
        require(indexed.keys.all { it.matches(Regex("[a-z][a-z0-9_-]{1,31}")) }) {
            "Invalid OAuth 1 provider id"
        }
    }

    fun resolve(spec: AuthSpec.OAuth1): ResolvedOAuth1Provider =
        resolve(spec.providerId, spec.clientRegistrationId)

    fun resolve(providerId: String, registrationId: String): ResolvedOAuth1Provider {
        val adapter = requireNotNull(adaptersById[providerId]) { "Unsupported OAuth 1 provider" }
        require(registrationId.matches(Regex("[a-z][a-z0-9_-]{1,63}"))) {
            "Invalid OAuth 1 client registration"
        }
        val registration = requireNotNull(adapter.registration(registrationId)) {
            "OAuth 1 client registration is not configured"
        }
        require(registration.consumerKey.isNotBlank() && registration.consumerSecret.isNotBlank()) {
            "OAuth 1 client registration is not configured"
        }
        return ResolvedOAuth1Provider(adapter, registration)
    }
}
