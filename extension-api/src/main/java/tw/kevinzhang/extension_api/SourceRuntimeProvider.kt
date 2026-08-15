package tw.kevinzhang.extension_api

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** Stable trust-domain UUID used by the repository bundled with NewsHub. */
const val BUILTIN_REPOSITORY_DOMAIN_ID = "00000000-0000-0000-0000-000000000001"

data class SourceIdentity(
    val packageName: String,
    /** Stable Host-pinned signing-lineage anchor. Identity keys must use this, never signer order. */
    val signerSha256: String,
    val sourceId: String,
    /** Certificate currently signing the installed APK; authorization verifies it belongs to the lineage. */
    val currentSignerSha256: String = signerSha256,
    /** Canonical UUID of the independently trusted repository that authorized this Source. */
    val repositoryDomainId: String = BUILTIN_REPOSITORY_DOMAIN_ID,
) {
    init {
        require(runCatching { UUID.fromString(repositoryDomainId).toString() == repositoryDomainId }.getOrDefault(false)) {
            "Repository domain id must be a canonical UUID"
        }
    }
}

data class NetworkOperationPolicy(
    val name: String,
    val methods: Set<String>,
    val pathPrefixes: Set<String>,
    val credentialed: Boolean = false,
)

data class SourceNetworkPolicy(
    val exactHosts: Set<String>,
    val operations: Map<String, NetworkOperationPolicy>,
    val namedCapabilities: Set<String> = emptySet(),
)

/** Deterministic policy representation stored as `policyHash` in signed repository targets. */
fun SourceNetworkPolicy.canonicalJson(): String {
    require(exactHosts.all { it.matches(Regex("[a-z0-9.-]{1,253}")) }) { "Hosts must be canonical" }
    require(operations.all { (key, value) -> key == value.name }) { "Operation key/name mismatch" }
    require(operations.values.all { operation ->
        operation.name.matches(Regex("[a-z0-9_]{1,64}")) &&
            operation.methods.all { it.matches(Regex("[A-Z]{3,12}")) } &&
            operation.pathPrefixes.all { it.matches(Regex("/[\\x20-\\x7e]{0,255}")) }
    }) { "Operations must be canonical" }
    require(namedCapabilities.all { it.matches(Regex("[a-z0-9_]{1,64}")) }) {
        "Named capabilities must be canonical"
    }
    fun String.jsonString(): String = buildString {
        append('"')
        this@jsonString.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
    fun Iterable<String>.jsonArray(): String = sorted().joinToString(",", "[", "]") { it.jsonString() }

    val operationJson = operations.values.sortedBy(NetworkOperationPolicy::name).joinToString(",", "[", "]") {
        "{\"credentialed\":${it.credentialed},\"methods\":${it.methods.jsonArray()}," +
            "\"name\":${it.name.jsonString()},\"pathPrefixes\":${it.pathPrefixes.jsonArray()}}"
    }
    return "{\"exactHosts\":${exactHosts.jsonArray()}," +
        "\"namedCapabilities\":${namedCapabilities.jsonArray()},\"operations\":$operationJson}"
}

fun SourceNetworkPolicy.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(canonicalJson().toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

object NamedHostCapabilities {
    const val PTT_ADULT_CONSENT_STATUS = "ptt_adult_consent_status"
    const val EYNY_CHALLENGE_PROOF = "eyny_challenge_proof"
    const val RESOURCE_READ = "resource_read"
    const val EXTERNAL_LINK = "external_link"
}

object NetworkOperations {
    /** A bounded, Host-authorized source fetch. Raw sockets and arbitrary operation names are forbidden. */
    const val SOURCE_READ = "source_read"
}

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

data class ResourceRange(
    val bytes: ByteArray,
    val contentType: String?,
    val offset: Long,
    val totalLength: Long?,
)

data class ExternalLinkHandle(
    val sourceSession: String,
    val generation: Long,
    val token: String,
) {
    init {
        require(sourceSession.matches(Regex("[a-f0-9]{16}")))
        require(generation > 0)
        require(token.matches(Regex("[A-Za-z0-9_-]{32,128}")))
    }

    fun asModel(): String = "newshub-link://$sourceSession/$generation/$token"

    companion object {
        fun parse(model: String): ExternalLinkHandle? {
            val match = Regex("^newshub-link://([a-f0-9]{16})/([1-9][0-9]*)/([A-Za-z0-9_-]{32,128})$")
                .matchEntire(model) ?: return null
            return runCatching {
                ExternalLinkHandle(match.groupValues[1], match.groupValues[2].toLong(), match.groupValues[3])
            }.getOrNull()
        }
    }
}

interface HostResourceProvider {
    fun issueResource(
        identity: SourceIdentity,
        policy: SourceNetworkPolicy,
        untrustedUrl: String,
    ): ResourceHandle

    suspend fun openResource(handle: ResourceHandle): ResourcePayload

    suspend fun openResourceRange(handle: ResourceHandle, offset: Long, length: Int): ResourceRange

    fun issueExternalLink(
        identity: SourceIdentity,
        policy: SourceNetworkPolicy,
        untrustedUrl: String,
    ): ExternalLinkHandle

    /** Consumes a single-use handle. Call only from a direct user gesture. */
    fun consumeExternalLink(handle: ExternalLinkHandle): String

    /** Revokes network, resource, and link capabilities for an unbound or updated Source. */
    fun revoke(identity: SourceIdentity)
}
