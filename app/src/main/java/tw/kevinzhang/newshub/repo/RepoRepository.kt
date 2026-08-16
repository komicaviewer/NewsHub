package tw.kevinzhang.newshub.repo

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryTrustDomain

interface RepoRepository {
    fun getRepositoryDomains(): Flow<List<RepositoryTrustDomain>>
    fun getRepoUrls(): Flow<Set<String>>

    suspend fun addRepositoryDomain(domain: RepositoryTrustDomain)
    suspend fun setRepositoryDomainState(domainId: String, state: RepositoryDomainState)

    /** Legacy adapters retained while Marketplace callers migrate to domain records. */
    suspend fun addRepoUrl(url: String)
    suspend fun removeRepoUrl(url: String)
}
