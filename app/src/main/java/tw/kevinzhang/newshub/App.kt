package tw.kevinzhang.newshub

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App: Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var twocatMigrationCoordinator: TwocatMigrationCoordinator

    override fun onCreate() {
        super.onCreate()
        twocatMigrationCoordinator.start()
    }

    override fun newImageLoader() = imageLoader
}
