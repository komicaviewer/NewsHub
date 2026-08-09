package tw.kevinzhang.newshub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.extension_loader.ExtensionManager
import tw.kevinzhang.extension_loader.ExpectedSourceService
import tw.kevinzhang.extension_loader.ExtensionSigningPolicy
import tw.kevinzhang.extension_loader.ExtensionTrustPolicyProvider
import tw.kevinzhang.extension_loader.VerifiedExtensionTrustSnapshot
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
    ): VerifiedRepositoryTrustConsumer = VerifiedRepositoryTrustConsumer { repositorySnapshot ->
        policyProvider.installVerifiedSnapshot(
            VerifiedExtensionTrustSnapshot(
                rootVersion = repositorySnapshot.rootVersion,
                targetsVersion = repositorySnapshot.targetsVersion,
                expiresAtEpochMillis = repositorySnapshot.expiresAtEpochMillis,
                policies = repositorySnapshot.policies.map { policy ->
                    ExtensionSigningPolicy(
                        packageName = policy.packageName,
                        expectedVersionCode = policy.expectedVersionCode,
                        targetLength = policy.targetLength,
                        targetSha256 = policy.targetSha256,
                        lineageAnchorsSha256 = policy.lineageAnchorsSha256,
                        approvedCurrentSignersSha256 = policy.approvedCurrentSignersSha256,
                        sources = policy.sources.mapValues { (_, source) ->
                            ExpectedSourceService(
                                serviceClassName = source.serviceClassName,
                                name = source.name,
                                lang = source.lang,
                                baseUrl = source.baseUrl,
                                protocol = source.protocol,
                                policyHash = source.policyHash,
                            )
                        },
                    )
                },
            ),
        )
        extensionManager.refreshAllExtensions()
    }
}
