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
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryRootPreview
import tw.kevinzhang.marketplace.RepositoryTrustDomain
import tw.kevinzhang.marketplace.RepositoryTrustDomains
import tw.kevinzhang.marketplace.RepositoryTrustMode
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import tw.kevinzhang.newshub.repo.RepoRepository
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ManageReposViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `inspection requires explicit confirmation and cancel persists nothing`() = runTest {
        val repo = FakeRepoRepository()
        val marketplace = FakeMarketplaceRepository()
        val viewModel = ManageReposViewModel(repo, marketplace)

        viewModel.onAddRepoUrlChanged("https://repo.example.test/extensions")
        viewModel.inspectNow()
        advanceUntilIdle()
        assertTrue(viewModel.validationState.value is AddRepoValidationState.AwaitingTrustConfirmation)
        assertTrue(repo.domains.value.isEmpty())

        viewModel.cancelTrustConfirmation()
        assertEquals(marketplace.preview.confirmationToken, marketplace.cancelledToken)
        assertTrue(repo.domains.value.isEmpty())
    }

    @Test
    fun `confirmation persists returned USER_PINNED domain`() = runTest {
        val repo = FakeRepoRepository()
        val marketplace = FakeMarketplaceRepository()
        val viewModel = ManageReposViewModel(repo, marketplace)

        viewModel.onAddRepoUrlChanged("https://repo.example.test/extensions")
        viewModel.inspectNow()
        advanceUntilIdle()
        viewModel.confirmTrust()
        advanceUntilIdle()

        assertEquals(listOf(marketplace.confirmedDomain), repo.domains.value)
        assertTrue(viewModel.validationState.value is AddRepoValidationState.Success)
    }

    private class FakeRepoRepository : RepoRepository {
        val domains = MutableStateFlow<List<RepositoryTrustDomain>>(emptyList())
        override fun getRepositoryDomains(): Flow<List<RepositoryTrustDomain>> = domains
        override fun getRepoUrls(): Flow<Set<String>> = domains.map { list ->
            list.mapTo(linkedSetOf(), RepositoryTrustDomain::canonicalBaseUrl)
        }
        override suspend fun addRepositoryDomain(domain: RepositoryTrustDomain) { domains.value += domain }
        override suspend fun setRepositoryDomainState(domainId: String, state: RepositoryDomainState) {
            domains.value = domains.value.map { if (it.id == domainId) it.copy(state = state) else it }
        }
        override suspend fun addRepoUrl(url: String) = Unit
        override suspend fun removeRepoUrl(url: String) = Unit
    }

    private class FakeMarketplaceRepository : MarketplaceRepository {
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
        val confirmedDomain = domain(
            "88888888-8888-4888-8888-888888888888",
            RepositoryTrustMode.USER_PINNED,
        )
        var cancelledToken: String? = null

        override suspend fun inspectRepositoryRoot(repoUrl: String) = preview
        override suspend fun confirmRepositoryRoot(confirmationToken: String) = confirmedDomain
        override fun cancelRepositoryRootInspection(confirmationToken: String) { cancelledToken = confirmationToken }
        override fun registerRepositoryDomains(domains: Collection<RepositoryTrustDomain>) = Unit
        override fun setRepositoryDomainState(domain: RepositoryTrustDomain) = Unit
        override suspend fun fetchRepoMetadata(repoUrl: String): RepoMetadata = error("unused")
        override suspend fun fetchExtensions(repoUrl: String): List<ExtensionInfo> = error("unused")
        override fun getInstallState(info: ExtensionInfo) = InstallState.NOT_INSTALLED
        override suspend fun downloadApk(info: ExtensionInfo): File = error("unused")

        companion object {
            private fun domain(id: String, mode: RepositoryTrustMode) = RepositoryTrustDomain(
                id = id,
                canonicalBaseUrl = if (mode == RepositoryTrustMode.BUILTIN_PINNED) {
                    RepositoryTrustDomains.OFFICIAL_BASE_URL
                } else {
                    "https://repo.example.test/extensions"
                },
                trustMode = mode,
                state = RepositoryDomainState.ACTIVE,
                rootThreshold = 1,
                rootKeyFingerprints = setOf("a".repeat(64)),
            )
        }
    }
}
