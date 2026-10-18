package tw.kevinzhang.newshub.repo

import tw.kevinzhang.marketplace.*
import tw.kevinzhang.newshub.github.GitHubAccountSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryCredentialStore @Inject constructor(
    private val manual: RepositoryCredentialVault,
    private val account: GitHubAccountSession,
) : RepositoryCredentialStore {
    override suspend fun getCredential(domainId: String) = manual.getCredential(domainId)
    override suspend fun getCredential(domain: RepositoryTrustDomain): RepositoryAccessCredential? = when (domain.access.credentialBinding) {
        RepositoryCredentialBinding.GITHUB_ACCOUNT -> account.currentCredential()
        RepositoryCredentialBinding.MANUAL_PAT -> manual.getCredential(domain.id)
        RepositoryCredentialBinding.NONE -> null
    }
    override suspend fun saveCredential(domainId: String, credential: RepositoryAccessCredential) = manual.saveCredential(domainId, credential)
    override suspend fun deleteCredential(domainId: String) = manual.deleteCredential(domainId)
}
