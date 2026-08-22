package tw.kevinzhang.newshub.ui.marketplace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.marketplace.RepositoryAccessCredential
import tw.kevinzhang.marketplace.RepositoryAccessDescriptor
import tw.kevinzhang.marketplace.RepositoryAccessDraft
import tw.kevinzhang.marketplace.RepositoryAccessFailureReason
import tw.kevinzhang.marketplace.RepositoryAccessKind
import tw.kevinzhang.marketplace.RepositoryAccessRequiredException
import tw.kevinzhang.marketplace.RepositoryCredentialStore
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryRootPreview
import tw.kevinzhang.marketplace.RepositoryTrustDomain
import tw.kevinzhang.marketplace.RepositoryTrustDomains
import tw.kevinzhang.marketplace.RepositoryTrustMode
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import tw.kevinzhang.newshub.repo.RepoRepository
import tw.kevinzhang.newshub.repo.RepositoryAccessStatusStore
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ManageReposViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `inspection requires explicit confirmation and cancel persists nothing`() = runTest {
        val repo = FakeRepoRepository()
        val credentials = FakeCredentialStore()
        val marketplace = FakeMarketplaceRepository(credentials)
        val viewModel = viewModel(repo, marketplace, credentials)

        viewModel.onAddRepoUrlChanged("https://repo.example.test/extensions")
        viewModel.inspectNow()
        advanceUntilIdle()
        assertTrue(viewModel.validationState.value is AddRepoValidationState.AwaitingTrustConfirmation)
        assertTrue(repo.domains.value.isEmpty())

        viewModel.cancelTrustConfirmation()
        assertEquals(marketplace.preview.confirmationToken, marketplace.cancelledToken)
        assertTrue(repo.domains.value.isEmpty())
        assertTrue(credentials.values.isEmpty())
    }

    @Test
    fun `confirmation persists returned USER_PINNED domain`() = runTest {
        val repo = FakeRepoRepository()
        val credentials = FakeCredentialStore()
        val marketplace = FakeMarketplaceRepository(credentials)
        val viewModel = viewModel(repo, marketplace, credentials)

        viewModel.onAddRepoUrlChanged("https://repo.example.test/extensions")
        viewModel.inspectNow()
        advanceUntilIdle()
        viewModel.confirmTrust()
        advanceUntilIdle()

        assertEquals(listOf(marketplace.confirmedDomain), repo.domains.value)
        assertTrue(viewModel.validationState.value is AddRepoValidationState.Success)
    }

    @Test
    fun `private GitHub token is process local until confirmation then encrypted store is used`() = runTest {
        val repo = FakeRepoRepository()
        val credentials = FakeCredentialStore()
        val marketplace = FakeMarketplaceRepository(credentials)
        val viewModel = viewModel(repo, marketplace, credentials)

        viewModel.onAccessKindChanged(RepositoryAccessKind.GITHUB_CONTENTS)
        viewModel.onAddRepoUrlChanged("https://github.com/example/private-extensions")
        viewModel.onGithubTokenChanged("github-secret-token")
        viewModel.inspectNow()
        advanceUntilIdle()

        assertEquals(RepositoryAccessKind.GITHUB_CONTENTS, marketplace.inspectedDraft?.access?.kind)
        assertEquals("github-secret-token", marketplace.inspectedCredential?.raw())
        assertTrue(credentials.values.isEmpty())

        viewModel.confirmTrust()
        advanceUntilIdle()

        val domain = repo.domains.value.single()
        assertEquals(RepositoryAccessKind.GITHUB_CONTENTS, domain.access.kind)
        assertEquals("github-secret-token", credentials.values.getValue(domain.id).raw())
        assertEquals("", viewModel.githubToken.value)
    }

    @Test
    fun `domain persistence failure removes credential saved during confirmation`() = runTest {
        val repo = FakeRepoRepository(failAdd = true)
        val credentials = FakeCredentialStore()
        val marketplace = FakeMarketplaceRepository(credentials)
        val viewModel = viewModel(repo, marketplace, credentials)

        viewModel.onAccessKindChanged(RepositoryAccessKind.GITHUB_CONTENTS)
        viewModel.onAddRepoUrlChanged("https://github.com/example/private-extensions")
        viewModel.onGithubTokenChanged("temporary-secret")
        viewModel.inspectNow()
        advanceUntilIdle()
        viewModel.confirmTrust()
        advanceUntilIdle()

        assertTrue(repo.domains.value.isEmpty())
        assertTrue(credentials.values.isEmpty())
        assertTrue(viewModel.validationState.value is AddRepoValidationState.Error)
    }

    @Test
    fun `replacement token is verified before save and rejected token is discarded`() = runTest {
        val repo = FakeRepoRepository()
        val credentials = FakeCredentialStore()
        val marketplace = FakeMarketplaceRepository(credentials).apply { rejectVerification = true }
        val viewModel = viewModel(repo, marketplace, credentials)
        val domainId = marketplace.confirmedDomain.id
        credentials.saveCredential(domainId, RepositoryAccessCredential.githubToken("old-token"))

        viewModel.beginReauthorization(domainId)
        viewModel.onReplacementTokenChanged("rejected-token")
        viewModel.confirmReauthorization()
        advanceUntilIdle()

        assertEquals("old-token", credentials.values.getValue(domainId).raw())
        val state = viewModel.reauthorizationState.value as RepositoryReauthorizationState.Editing
        assertEquals("", state.token)
        assertTrue(state.errorMessage?.contains("拒絕") == true)
    }

    private fun viewModel(
        repo: RepoRepository,
        marketplace: MarketplaceRepository,
        credentials: RepositoryCredentialStore,
    ) = ManageReposViewModel(repo, marketplace, credentials, RepositoryAccessStatusStore())

    private class FakeRepoRepository(private val failAdd: Boolean = false) : RepoRepository {
        val domains = MutableStateFlow<List<RepositoryTrustDomain>>(emptyList())
        override fun getRepositoryDomains(): Flow<List<RepositoryTrustDomain>> = domains
        override fun getRepoUrls(): Flow<Set<String>> = domains.map { list ->
            list.mapTo(linkedSetOf(), RepositoryTrustDomain::canonicalBaseUrl)
        }
        override suspend fun addRepositoryDomain(domain: RepositoryTrustDomain) {
            if (failAdd) error("DataStore unavailable")
            domains.value += domain
        }
        override suspend fun setRepositoryDomainState(domainId: String, state: RepositoryDomainState) {
            domains.value = domains.value.map { if (it.id == domainId) it.copy(state = state) else it }
        }
        override suspend fun addRepoUrl(url: String) = Unit
        override suspend fun removeRepoUrl(url: String) = Unit
    }

    private class FakeCredentialStore : RepositoryCredentialStore {
        val values = mutableMapOf<String, RepositoryAccessCredential>()
        override suspend fun getCredential(domainId: String) = values[domainId]
        override suspend fun saveCredential(domainId: String, credential: RepositoryAccessCredential) {
            values[domainId] = credential
        }
        override suspend fun deleteCredential(domainId: String) {
            values.remove(domainId)
        }
    }

    private class FakeMarketplaceRepository(
        private val credentialStore: RepositoryCredentialStore,
    ) : MarketplaceRepository {
        override val officialRepositoryDomain = domain(
            RepositoryTrustDomains.OFFICIAL_ID,
            RepositoryTrustMode.BUILTIN_PINNED,
        )
        val preview = RepositoryRootPreview(
            confirmationToken = "preview-token",
            canonicalBaseUrl = "https://repo.example.test/extensions",
            rootThreshold = 1,
            rootKeyFingerprints = setOf("a".repeat(64)),
        )
        var inspectedDraft: RepositoryAccessDraft? = null
        var inspectedCredential: RepositoryAccessCredential? = null
        var rejectVerification = false
        val confirmedDomain: RepositoryTrustDomain
            get() = domain(
                "88888888-8888-4888-8888-888888888888",
                RepositoryTrustMode.USER_PINNED,
                inspectedDraft?.repositoryUrl ?: "https://repo.example.test/extensions",
                inspectedDraft?.access ?: RepositoryAccessDescriptor.publicHttps(),
            )
        var cancelledToken: String? = null

        override suspend fun inspectRepositoryRoot(repoUrl: String) = preview
        override suspend fun inspectRepositoryRoot(
            draft: RepositoryAccessDraft,
            credential: RepositoryAccessCredential?,
        ): RepositoryRootPreview {
            inspectedDraft = draft
            inspectedCredential = credential
            return preview.copy(canonicalBaseUrl = draft.repositoryUrl)
        }
        override suspend fun confirmRepositoryRoot(confirmationToken: String): RepositoryTrustDomain {
            val domain = confirmedDomain
            inspectedCredential?.let { credentialStore.saveCredential(domain.id, it) }
            return domain
        }
        override suspend fun verifyRepositoryAccess(
            domainId: String,
            credential: RepositoryAccessCredential,
        ) {
            if (rejectVerification) throw RepositoryAccessRequiredException(
                domainId,
                RepositoryAccessFailureReason.CREDENTIAL_REJECTED,
            )
        }
        override fun cancelRepositoryRootInspection(confirmationToken: String) { cancelledToken = confirmationToken }
        override fun registerRepositoryDomains(domains: Collection<RepositoryTrustDomain>) = Unit
        override fun setRepositoryDomainState(domain: RepositoryTrustDomain) = Unit
        override suspend fun fetchRepoMetadata(repoUrl: String): RepoMetadata = error("unused")
        override suspend fun fetchExtensions(repoUrl: String): List<ExtensionInfo> = error("unused")
        override fun getInstallState(info: ExtensionInfo) = InstallState.NOT_INSTALLED
        override suspend fun downloadApk(info: ExtensionInfo): File = error("unused")

        companion object {
            private fun domain(
                id: String,
                mode: RepositoryTrustMode,
                url: String = if (mode == RepositoryTrustMode.BUILTIN_PINNED) {
                    RepositoryTrustDomains.OFFICIAL_BASE_URL
                } else {
                    "https://repo.example.test/extensions"
                },
                access: RepositoryAccessDescriptor = RepositoryAccessDescriptor.publicHttps(),
            ) = RepositoryTrustDomain(
                id = id,
                canonicalBaseUrl = url,
                trustMode = mode,
                state = RepositoryDomainState.ACTIVE,
                rootThreshold = 1,
                rootKeyFingerprints = setOf("a".repeat(64)),
                access = access,
            )
        }
    }

    private fun RepositoryAccessCredential.raw(): String = withSecret { it }
}
