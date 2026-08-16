package tw.kevinzhang.newshub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.extension_loader.ExtensionManager
import tw.kevinzhang.extension_loader.AcceptedExtensionArtifact
import tw.kevinzhang.extension_loader.ExpectedSourceService
import tw.kevinzhang.extension_loader.ExtensionSigningPolicy
import tw.kevinzhang.extension_loader.ExtensionTrustPolicyProvider
import tw.kevinzhang.extension_loader.RepositoryTrustDomainState
import tw.kevinzhang.extension_loader.VerifiedExtensionTrustSnapshot
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.VerifiedRepositoryTrustConsumer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExtensionTrustModule {
    @Provides
    @Singleton
    fun provideVerifiedRepositoryTrustConsumer(
        policyProvider: ExtensionTrustPolicyProvider,
        extensionManager: ExtensionManager,
    ): VerifiedRepositoryTrustConsumer = object : VerifiedRepositoryTrustConsumer {
        override fun install(repositorySnapshot: tw.kevinzhang.marketplace.VerifiedRepositoryTrustSnapshot) {
            policyProvider.installVerifiedSnapshot(VerifiedExtensionTrustSnapshot(
                rootVersion = repositorySnapshot.rootVersion,
                targetsVersion = repositorySnapshot.targetsVersion,
                expiresAtEpochMillis = repositorySnapshot.expiresAtEpochMillis,
                repositoryDomainId = repositorySnapshot.repositoryDomainId,
                policies = repositorySnapshot.policies.map { policy ->
                    ExtensionSigningPolicy(
                        packageName = policy.packageName,
                        expectedVersionCode = policy.expectedVersionCode,
                        targetLength = policy.targetLength,
                        targetSha256 = policy.targetSha256,
                        acceptedArtifacts = policy.acceptedArtifacts.map { artifact ->
                            AcceptedExtensionArtifact(
                                versionCode = artifact.versionCode,
                                length = artifact.length,
                                sha256 = artifact.sha256,
                            )
                        },
                        lineageAnchorsSha256 = policy.lineageAnchorsSha256,
                        approvedCurrentSignersSha256 = policy.approvedCurrentSignersSha256,
                        repositoryDomainId = policy.repositoryDomainId,
                        sources = policy.sources.mapValues { (_, source) ->
                            ExpectedSourceService(
                                serviceClassName = source.serviceClassName,
                                name = source.name,
                                lang = source.lang,
                                baseUrl = source.baseUrl,
                                protocol = source.protocol,
                                policyHash = source.policyHash,
                                repositoryDomainId = policy.repositoryDomainId,
                                networkPolicy = requireNotNull(source.networkPolicy) {
                                    "Verified repository Source is missing its signed network policy"
                                },
                            )
                        },
                    )
                },
            ))
            extensionManager.refreshAllExtensions()
        }

        override fun setDomainState(repositoryDomainId: String, state: RepositoryDomainState) {
            val loaderState = when (state) {
                RepositoryDomainState.ACTIVE -> RepositoryTrustDomainState.ACTIVE
                RepositoryDomainState.SUSPENDED -> RepositoryTrustDomainState.SUSPENDED
                RepositoryDomainState.REVOKED -> RepositoryTrustDomainState.REVOKED
                RepositoryDomainState.EXPIRED -> null
            }
            if (loaderState == null) {
                policyProvider.clear(repositoryDomainId)
            } else {
                runCatching { policyProvider.setDomainState(repositoryDomainId, loaderState) }
                    .getOrElse { policyProvider.clear(repositoryDomainId) }
            }
            extensionManager.refreshAllExtensions()
        }
    }
}
