package tw.kevinzhang.newshub

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.newshub.di.ApplicationScope
import javax.inject.Inject

@HiltAndroidApp
class App: Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var marketplaceRepository: MarketplaceRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Repository construction restores the last threshold-verified snapshot. Refresh in the
        // application scope so installed Sources do not depend on visiting Marketplace first.
        applicationScope.launch {
            runCatching { marketplaceRepository.fetchExtensions(OFFICIAL_EXTENSION_REPOSITORY) }
                .onFailure { error -> Log.w(TAG, "Unable to refresh extension trust", error) }
        }
    }

    override fun newImageLoader() = imageLoader

    private companion object {
        const val TAG = "NewsHubApp"
        const val OFFICIAL_EXTENSION_REPOSITORY = "https://github.com/komicaviewer/extensions"
    }
}
