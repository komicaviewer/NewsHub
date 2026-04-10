package tw.kevinzhang.newshub.repo

import kotlinx.coroutines.flow.Flow

interface RepoRepository {
    fun getRepoUrls(): Flow<Set<String>>
    suspend fun addRepoUrl(url: String)
    suspend fun removeRepoUrl(url: String)
}
