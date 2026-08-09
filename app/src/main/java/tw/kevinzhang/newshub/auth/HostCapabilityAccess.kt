package tw.kevinzhang.newshub.auth

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import tw.kevinzhang.extension_api.HostResourceProvider

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HostResourceProviderEntryPoint {
    fun hostResourceProvider(): HostResourceProvider
}

fun Context.hostResourceProvider(): HostResourceProvider =
    EntryPointAccessors.fromApplication(
        applicationContext,
        HostResourceProviderEntryPoint::class.java,
    ).hostResourceProvider()
