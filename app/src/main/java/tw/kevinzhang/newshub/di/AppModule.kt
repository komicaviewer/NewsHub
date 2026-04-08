package tw.kevinzhang.newshub.di

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.util.DebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import tw.kevinzhang.newshub.di.ApplicationScope

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_status")

@InstallIn(SingletonComponent::class)
@Module
object AppModule {

    @ApplicationScope
    @Provides
    @javax.inject.Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())

    @Provides
    @javax.inject.Singleton
    fun provideAuthDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.authDataStore

    @Provides
    fun provideBluetoothManager(@ApplicationContext context: Context) =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    @Provides
    fun provideImageLoader(@ApplicationContext context: Context) =
        ImageLoader.Builder(context)
            .logger(DebugLogger())
            .crossfade(true)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
