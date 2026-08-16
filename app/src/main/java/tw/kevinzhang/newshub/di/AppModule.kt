package tw.kevinzhang.newshub.di

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.util.DebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import tw.kevinzhang.newshub.di.ApplicationScope
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.newshub.auth.ResourceHandleFetcher

private val Context.repoDataStore: DataStore<Preferences> by preferencesDataStore(name = "repo_settings")

@InstallIn(SingletonComponent::class)
@Module
object AppModule {

    @ApplicationScope
    @Provides
    @javax.inject.Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())

    @Provides
    @javax.inject.Singleton
    @Named("repoDataStore")
    fun provideRepoDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.repoDataStore

    @Provides
    fun provideBluetoothManager(@ApplicationContext context: Context) =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    @Provides
    fun provideImageLoader(
        @ApplicationContext context: Context,
        resourceProvider: HostResourceProvider,
    ) =
        ImageLoader.Builder(context)
            .logger(DebugLogger())
            .crossfade(true)
            // Resource handles are revocable capabilities; cached bytes must not outlive them.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .components {
                add(ResourceHandleFetcher.Factory(resourceProvider))
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
