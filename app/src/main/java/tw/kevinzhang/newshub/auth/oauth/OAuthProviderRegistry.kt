package tw.kevinzhang.newshub.auth.oauth

import tw.kevinzhang.extension_api.AuthSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuthProviderRegistry @Inject constructor(
    adapters: Set<@JvmSuppressWildcards OAuthProviderAdapter>,
) {
    private val adaptersById = adapters.associateBy(OAuthProviderAdapter::providerId).also { indexed ->
        require(indexed.size == adapters.size) { "Duplicate OAuth provider adapter" }
        require(indexed.keys.all { it.matches(Regex("[a-z][a-z0-9_-]{1,31}")) }) {
            "Invalid OAuth provider id"
        }
    }

    fun resolve(spec: AuthSpec.OAuth): ResolvedOAuthProvider = resolve(
        providerId = spec.providerId,
        registrationId = spec.clientRegistrationId,
        scopes = spec.scopes,
    )

    fun resolve(providerId: String, registrationId: String, scopes: Set<String>): ResolvedOAuthProvider {
        val adapter = requireNotNull(adaptersById[providerId]) { "Unsupported OAuth provider" }
        require(registrationId.matches(Regex("[a-z][a-z0-9_-]{1,63}"))) {
            "Invalid OAuth client registration"
        }
        val registration = requireNotNull(adapter.registration(registrationId)) {
            "OAuth client registration is not configured"
        }
        require(scopes.isNotEmpty() && scopes.size <= 24) { "OAuth scopes must be non-empty and bounded" }
        require(scopes.all { it.matches(Regex("[A-Za-z0-9._:-]{1,80}")) && it in adapter.allowedScopes }) {
            "OAuth scope is not authorized by the Host"
        }
        require(registration.clientId.isNotBlank()) { "OAuth client registration is not configured"
        }
        return ResolvedOAuthProvider(adapter, registration, scopes)
    }

    fun adapter(providerId: String): OAuthProviderAdapter? = adaptersById[providerId]
}
