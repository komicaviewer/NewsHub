package tw.kevinzhang.extension_api

data class SourceIdentity(
    val packageName: String,
    val signerSha256: String,
    val sourceId: String,
)

data class NetworkOperationPolicy(
    val name: String,
    val methods: Set<String>,
    val pathPrefixes: Set<String>,
    val credentialed: Boolean = false,
)

data class SourceNetworkPolicy(
    val exactHosts: Set<String>,
    val operations: Map<String, NetworkOperationPolicy>,
)

/** Constructed by the Host; the loader never implements network or credential policy itself. */
fun interface HostBrokerProvider {
    fun brokerFor(identity: SourceIdentity, policy: SourceNetworkPolicy): IHostBroker
}

/** Opaque Host-issued reference. It never contains an extension URL or credential. */
data class ResourceHandle(
    val sourceSession: String,
    val generation: Long,
    val token: String,
) {
    init {
        require(sourceSession.matches(Regex("[a-f0-9]{16}")))
        require(generation > 0)
        require(token.matches(Regex("[A-Za-z0-9_-]{32,128}")))
    }

    fun asModel(): String = "newshub-resource://$sourceSession/$generation/$token"

    companion object {
        fun parse(model: String): ResourceHandle? {
            val match = Regex("^newshub-resource://([a-f0-9]{16})/([1-9][0-9]*)/([A-Za-z0-9_-]{32,128})$")
                .matchEntire(model) ?: return null
            return runCatching {
                ResourceHandle(match.groupValues[1], match.groupValues[2].toLong(), match.groupValues[3])
            }.getOrNull()
        }
    }
}

data class ResourcePayload(val bytes: ByteArray, val contentType: String?)

interface HostResourceProvider {
    fun issueResource(
        identity: SourceIdentity,
        policy: SourceNetworkPolicy,
        untrustedUrl: String,
    ): ResourceHandle

    suspend fun openResource(handle: ResourceHandle): ResourcePayload
}
