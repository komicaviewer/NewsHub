package tw.kevinzhang.newshub.extension

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.extension_loader.ExtensionManager
import tw.kevinzhang.extension_loader.ExtensionTrustPolicyProvider

/** Internal process boundary used by package broadcasts and instrumentation verification. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ExtensionManagementEntryPoint {
    fun manager(): ExtensionManager
    fun loader(): ExtensionLoader
    fun trustProvider(): ExtensionTrustPolicyProvider
}
