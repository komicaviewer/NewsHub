package tw.kevinzhang.newshub.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryTrustDomain
import tw.kevinzhang.marketplace.RepositoryTrustDomains
import tw.kevinzhang.marketplace.RepositoryTrustMode
import tw.kevinzhang.newshub.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Named

private val LEGACY_REPO_URLS_KEY = stringSetPreferencesKey("repo_urls")
private val REPOSITORY_DOMAINS_KEY = stringPreferencesKey("repository_trust_domains_v1")
private const val LEGACY_OFFICIAL_REPO_URL = "https://github.com/komicaviewer/extensions"

class RepoRepositoryImpl @Inject constructor(
    @Named("repoDataStore") private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val marketplaceRepository: MarketplaceRepository,
) : RepoRepository {
    private val officialDomain: RepositoryTrustDomain
        get() = marketplaceRepository.officialRepositoryDomain

    init {
        applicationScope.launch {
            migrateLegacyRepositoryUrls()
            getRepositoryDomains().collect(marketplaceRepository::registerRepositoryDomains)
        }
    }

    override fun getRepositoryDomains(): Flow<List<RepositoryTrustDomain>> =
        dataStore.data.map { preferences ->
            preferences[REPOSITORY_DOMAINS_KEY]
                ?.let(::decodeRepositoryDomains)
                ?.ensureOfficial(officialDomain)
                ?: listOf(officialDomain)
        }

    override fun getRepoUrls(): Flow<Set<String>> = getRepositoryDomains().map { domains ->
        domains.filter { it.state == RepositoryDomainState.ACTIVE }
            .mapTo(linkedSetOf(), RepositoryTrustDomain::canonicalBaseUrl)
    }

    override suspend fun addRepositoryDomain(domain: RepositoryTrustDomain) {
        require(domain.trustMode == RepositoryTrustMode.USER_PINNED) {
            "Only explicitly confirmed user repositories may be added"
        }
        require(domain.state == RepositoryDomainState.ACTIVE) { "New repository must be active" }
        dataStore.edit { preferences ->
            val current = currentDomains(preferences)
            require(current.none { it.id == domain.id }) { "Repository id already exists" }
            require(current.none { it.canonicalBaseUrl == domain.canonicalBaseUrl }) {
                "Repository source already exists"
            }
            preferences[REPOSITORY_DOMAINS_KEY] = encodeRepositoryDomains(current + domain)
        }
        marketplaceRepository.registerRepositoryDomains(listOf(domain))
    }

    override suspend fun setRepositoryDomainState(domainId: String, state: RepositoryDomainState) {
        require(state != RepositoryDomainState.EXPIRED) { "Expiry is derived from repository metadata" }
        var updated: RepositoryTrustDomain? = null
        dataStore.edit { preferences ->
            val current = currentDomains(preferences)
            val domain = current.singleOrNull { it.id == domainId }
                ?: error("Unknown repository source")
            require(domain.id != RepositoryTrustDomains.OFFICIAL_ID) {
                "The built-in repository cannot be changed"
            }
            require(domain.state != RepositoryDomainState.REVOKED || state == RepositoryDomainState.REVOKED) {
                "A revoked repository cannot be restored"
            }
            updated = domain.copy(state = state)
            preferences[REPOSITORY_DOMAINS_KEY] = encodeRepositoryDomains(
                current.map { if (it.id == domainId) requireNotNull(updated) else it },
            )
        }
        marketplaceRepository.setRepositoryDomainState(requireNotNull(updated))
    }

    override suspend fun addRepoUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (normalized == LEGACY_OFFICIAL_REPO_URL || normalized == officialDomain.canonicalBaseUrl) return
        error("請從管理來源畫面核對金鑰指紋後再加入來源")
    }

    override suspend fun removeRepoUrl(url: String) {
        val domain = getRepositoryDomains().first().singleOrNull {
            it.canonicalBaseUrl == url.trim().trimEnd('/')
        } ?: return
        if (domain.id != RepositoryTrustDomains.OFFICIAL_ID) {
            setRepositoryDomainState(domain.id, RepositoryDomainState.REVOKED)
        }
    }

    private suspend fun migrateLegacyRepositoryUrls() {
        dataStore.edit { preferences ->
            if (preferences[REPOSITORY_DOMAINS_KEY] == null) {
                // Legacy third-party URLs were never pinned to a root and cannot be silently
                // promoted. The built-in URL alone migrates to its embedded pinned domain.
                preferences[REPOSITORY_DOMAINS_KEY] = encodeRepositoryDomains(listOf(officialDomain))
            }
            if (preferences[LEGACY_REPO_URLS_KEY]?.contains(LEGACY_OFFICIAL_REPO_URL) == true) {
                preferences[LEGACY_REPO_URLS_KEY] = setOf(LEGACY_OFFICIAL_REPO_URL)
            }
        }
    }

    private fun currentDomains(preferences: Preferences): List<RepositoryTrustDomain> =
        preferences[REPOSITORY_DOMAINS_KEY]
            ?.let(::decodeRepositoryDomains)
            ?.ensureOfficial(officialDomain)
            ?: listOf(officialDomain)
}

internal fun encodeRepositoryDomains(domains: Collection<RepositoryTrustDomain>): String =
    JsonArray().apply {
        domains.sortedBy(RepositoryTrustDomain::id).forEach { domain ->
            add(JsonObject().apply {
                addProperty("id", domain.id)
                addProperty("canonicalBaseUrl", domain.canonicalBaseUrl)
                addProperty("trustMode", domain.trustMode.name)
                addProperty("state", domain.state.name)
                addProperty("rootThreshold", domain.rootThreshold)
                add("rootKeyFingerprints", JsonArray().apply {
                    domain.rootKeyFingerprints.sorted().forEach(::add)
                })
            })
        }
    }.toString()

internal fun decodeRepositoryDomains(value: String): List<RepositoryTrustDomain> {
    val array = runCatching { JsonParser.parseString(value).asJsonArray }
        .getOrElse { error("Invalid repository trust-domain settings") }
    val domains = array.map { element ->
        val item = element.asJsonObject
        RepositoryTrustDomain(
            id = item.get("id").asString,
            canonicalBaseUrl = item.get("canonicalBaseUrl").asString,
            trustMode = RepositoryTrustMode.valueOf(item.get("trustMode").asString),
            state = RepositoryDomainState.valueOf(item.get("state").asString),
            rootThreshold = item.get("rootThreshold").asInt,
            rootKeyFingerprints = item.getAsJsonArray("rootKeyFingerprints")
                .mapTo(linkedSetOf()) { it.asString },
        )
    }
    require(domains.map(RepositoryTrustDomain::id).distinct().size == domains.size) {
        "Duplicate repository domain id"
    }
    require(domains.map(RepositoryTrustDomain::canonicalBaseUrl).distinct().size == domains.size) {
        "Duplicate repository URL"
    }
    return domains
}

private fun List<RepositoryTrustDomain>.ensureOfficial(
    official: RepositoryTrustDomain,
): List<RepositoryTrustDomain> = filterNot { it.id == official.id } + official
