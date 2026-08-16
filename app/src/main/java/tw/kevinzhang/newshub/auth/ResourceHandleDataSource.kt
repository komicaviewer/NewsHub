package tw.kevinzhang.newshub.auth

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.extension_api.ResourceHandle
import java.io.IOException
import kotlin.math.min

/** Media3 source that can read only a Host-issued handle and never sees an extension URL. */
@UnstableApi
class ResourceHandleDataSource(
    private val resourceProvider: HostResourceProvider,
) : BaseDataSource(false) {
    private var dataSpec: DataSpec? = null
    private var handle: ResourceHandle? = null
    private var readPosition = 0L
    private var remaining: Long? = null
    private var pending = ByteArray(0)
    private var pendingOffset = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val parsed = ResourceHandle.parse(dataSpec.uri.toString())
            ?: throw IOException("Media URI is not a Host resource handle")
        require(dataSpec.position >= 0L) { "Invalid media position" }
        this.dataSpec = dataSpec
        handle = parsed
        readPosition = dataSpec.position
        remaining = dataSpec.length.takeUnless { it == C.LENGTH_UNSET.toLong() }
        pending = ByteArray(0)
        pendingOffset = 0
        opened = true
        transferStarted(dataSpec)
        return remaining ?: C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (pendingOffset >= pending.size) {
            val requestLength = min(
                RANGE_CHUNK_BYTES.toLong(),
                remaining ?: RANGE_CHUNK_BYTES.toLong(),
            ).toInt()
            if (requestLength == 0) return C.RESULT_END_OF_INPUT
            val range = try {
                runBlocking(Dispatchers.IO) {
                    resourceProvider.openResourceRange(
                        requireNotNull(handle),
                        readPosition,
                        requestLength,
                    )
                }
            } catch (error: Exception) {
                throw IOException("Host media read failed", error)
            }
            if (range.offset != readPosition) throw IOException("Host returned an unexpected media range")
            pending = range.bytes
            pendingOffset = 0
            range.totalLength?.let { total ->
                if (total < readPosition) throw IOException("Host returned an invalid media length")
                val available = total - readPosition
                remaining = remaining?.let { min(it, available) } ?: available
            }
            if (range.totalLength == null && pending.size < requestLength) {
                remaining = min(remaining ?: Long.MAX_VALUE, pending.size.toLong())
            }
            if (pending.isEmpty()) {
                remaining = 0L
                return C.RESULT_END_OF_INPUT
            }
        }

        val copied = min(length, min(pending.size - pendingOffset, (remaining ?: Long.MAX_VALUE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
        pending.copyInto(buffer, offset, pendingOffset, pendingOffset + copied)
        pendingOffset += copied
        readPosition += copied
        remaining = remaining?.minus(copied)
        bytesTransferred(copied)
        return copied
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        dataSpec = null
        handle = null
        pending = ByteArray(0)
        pendingOffset = 0
        remaining = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val resourceProvider: HostResourceProvider,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ResourceHandleDataSource(resourceProvider)
    }

    private companion object {
        const val RANGE_CHUNK_BYTES = 256 * 1_024
    }
}
