package tw.kevinzhang.newshub.auth

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.extension_api.ExternalLinkHandle
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.extension_api.ResourceHandle
import tw.kevinzhang.extension_api.ResourcePayload
import tw.kevinzhang.extension_api.ResourceRange
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.newshub.ui.component.ResourceModel

@RunWith(AndroidJUnit4::class)
class ResourceHandleFetcherInstrumentedTest {
    private val handle = ResourceHandle(
        sourceSession = "0123456789abcdef",
        generation = 7,
        token = "abcdefghijklmnopqrstuvwxyzABCDEF",
    )

    @Test
    fun typedModelLoadsThroughRealCoilPipeline() = runBlocking {
        val provider = FakeResourceProvider {
            ResourcePayload(ONE_PIXEL_PNG, "image/png")
        }
        val loader = imageLoader(provider)
        try {
            val result = loader.execute(request(ResourceModel(handle)))

            assertTrue(result is SuccessResult)
            val success = result as SuccessResult
            assertEquals(1, provider.openCount)
            assertSame(handle, provider.lastHandle)
            assertEquals(1, success.drawable.intrinsicWidth)
            assertEquals(1, success.drawable.intrinsicHeight)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun revokedOrUnknownHandleFailsClosedWithoutFallback() = runBlocking {
        val provider = FakeResourceProvider {
            throw SecurityException("Resource handle is no longer valid")
        }
        val loader = imageLoader(provider)
        try {
            val result = loader.execute(request(ResourceModel(handle)))

            assertTrue(result is ErrorResult)
            assertEquals(1, provider.openCount)
            assertSame(handle, provider.lastHandle)
        } finally {
            loader.shutdown()
        }
    }

    private fun imageLoader(provider: HostResourceProvider): ImageLoader {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .components { add(ResourceHandleFetcher.Factory(provider)) }
            .build()
    }

    private fun request(model: ResourceModel): ImageRequest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ImageRequest.Builder(context)
            .data(model)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }

    private class FakeResourceProvider(
        private val open: suspend (ResourceHandle) -> ResourcePayload,
    ) : HostResourceProvider {
        var openCount = 0
            private set
        var lastHandle: ResourceHandle? = null
            private set

        override fun issueResource(
            identity: SourceIdentity,
            policy: SourceNetworkPolicy,
            untrustedUrl: String,
        ): ResourceHandle = error("unused")

        override suspend fun openResource(handle: ResourceHandle): ResourcePayload {
            openCount += 1
            lastHandle = handle
            return open(handle)
        }

        override suspend fun openResourceRange(
            handle: ResourceHandle,
            offset: Long,
            length: Int,
        ): ResourceRange = error("unused")

        override fun issueExternalLink(
            identity: SourceIdentity,
            policy: SourceNetworkPolicy,
            untrustedUrl: String,
        ): ExternalLinkHandle = error("unused")

        override fun consumeExternalLink(handle: ExternalLinkHandle): String = error("unused")

        override fun revoke(identity: SourceIdentity) = Unit
    }

    private companion object {
        val ONE_PIXEL_PNG: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
    }
}
