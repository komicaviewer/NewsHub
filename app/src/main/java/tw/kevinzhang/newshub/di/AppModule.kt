package tw.kevinzhang.newshub.di

import android.bluetooth.BluetoothManager
import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.util.DebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
object AppModule {

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
