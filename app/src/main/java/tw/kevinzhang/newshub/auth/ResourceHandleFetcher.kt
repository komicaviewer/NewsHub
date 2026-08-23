package tw.kevinzhang.newshub.auth

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.extension_api.ResourceHandle
import tw.kevinzhang.newshub.ui.component.ResourceModel

/** Coil fetcher that recognizes only Host-issued opaque resource handles. */
class ResourceHandleFetcher private constructor(
    private val handle: ResourceHandle,
    private val options: Options,
    private val resourceProvider: HostResourceProvider,
) : Fetcher {
    override suspend fun fetch(): SourceResult {
        val payload = resourceProvider.openResource(handle)
        return SourceResult(
            source = ImageSource(Buffer().write(payload.bytes), options.context),
            mimeType = payload.contentType,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(
        private val resourceProvider: HostResourceProvider,
    ) : Fetcher.Factory<ResourceModel> {
        override fun create(data: ResourceModel, options: Options, imageLoader: ImageLoader): Fetcher =
            ResourceHandleFetcher(data.handle, options, resourceProvider)
    }
}
