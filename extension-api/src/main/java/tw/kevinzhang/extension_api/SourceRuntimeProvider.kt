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

data class NetworkRequestRule(
    val exactHosts: Set<String>,
    val operation: NetworkOperationPolicy,
)

data class SourceNetworkPolicy(
    /** v1 compatibility name. In v2 this is strictly the request scope. */
    val exactHosts: Set<String>,
    val operations: Map<String, NetworkOperationPolicy>,
    val namedCapabilities: Set<String> = emptySet(),
    val policyVersion: Int = 1,
    val resourceExactHosts: Set<String> = exactHosts,
    val externalExactHosts: Set<String> = exactHosts,
    val authExactHosts: Set<String> = exactHosts,
    val requestRules: List<NetworkRequestRule> = operations.values.map { operation ->
        NetworkRequestRule(exactHosts, operation)
    },
) {
    val allExactHosts: Set<String>
        get() = exactHosts + resourceExactHosts + externalExactHosts + authExactHosts
}

/** Deterministic policy representation stored as `policyHash` in signed repository targets. */
fun SourceNetworkPolicy.canonicalJson(): String {
    require(policyVersion == 1 || policyVersion == 2) { "Unsupported network policy version" }
    require(exactHosts.isNotEmpty() && exactHosts.size <= MAX_SCOPE_HOSTS) {
        "Request hosts must be non-empty and bounded"
    }
    require(
        listOf(exactHosts, resourceExactHosts, externalExactHosts, authExactHosts).all { hosts ->
            hosts.size <= MAX_SCOPE_HOSTS && hosts.all(String::isCanonicalExactDnsHost)
        },
    ) { "Hosts must be canonical exact DNS names" }
    require(allExactHosts.size <= MAX_SCOPE_HOSTS) { "Combined policy hosts exceed the Host limit" }
    if (policyVersion == 1) {
        require(
            resourceExactHosts == exactHosts && externalExactHosts == exactHosts && authExactHosts == exactHosts,
        ) { "Version 1 policy cannot express scoped hosts" }
    }
    require(operations.all { (key, value) -> key == value.name }) { "Operation key/name mismatch" }
    if (policyVersion == 1) {
        require(operations.keys == setOf(NetworkOperations.SOURCE_READ)) { "Unknown network operation" }
    }
    require(requestRules.isNotEmpty() && requestRules.size <= MAX_REQUEST_RULES) {
        "Request rules must be non-empty and bounded"
    }
    require(requestRules.all { rule ->
        rule.exactHosts.isNotEmpty() && rule.exactHosts.size <= MAX_SCOPE_HOSTS &&
            rule.exactHosts.all(String::isCanonicalExactDnsHost) &&
            rule.operation.name == NetworkOperations.SOURCE_READ
    }) { "Request rules must use exact hosts and a known operation" }
    require(requestRules.map { it.exactHosts to it.operation }.distinct().size == requestRules.size) {
        "Duplicate request rule"
    }
    require(requestRules.map(NetworkRequestRule::operation).all { operation ->
        operation.methods.isNotEmpty() && operation.methods.all { it == "GET" || it == "HEAD" } &&
            operation.pathPrefixes.isNotEmpty() && operation.pathPrefixes.size <= MAX_PATH_PREFIXES &&
            operation.pathPrefixes.all { it.matches(Regex("/[\\x20-\\x7e]{0,255}")) }
    }) { "Operations must be canonical and read-only" }
    if (policyVersion == 1) {
        require(requestRules.size == 1 && requestRules.single().exactHosts == exactHosts &&
            requestRules.single().operation == operations.getValue(NetworkOperations.SOURCE_READ)
        ) { "Version 1 policy must map to one compatibility request rule" }
    } else {
        require(requestRules.flatMapTo(linkedSetOf(), NetworkRequestRule::exactHosts) == exactHosts) {
            "Version 2 request hosts must equal the request-rule host union"
        }
    }
    require(namedCapabilities.size <= MAX_NAMED_CAPABILITIES && namedCapabilities.all {
        it in KNOWN_NAMED_CAPABILITIES
    }) {
        "Named capabilities must be known and bounded"
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

    fun NetworkOperationPolicy.json(): String =
        "{\"credentialed\":${credentialed},\"methods\":${methods.jsonArray()}," +
            "\"name\":${name.jsonString()},\"pathPrefixes\":${pathPrefixes.jsonArray()}}"
    val operationJson = operations.values.sortedBy(NetworkOperationPolicy::name).joinToString(",", "[", "]") {
        it.json()
    }
    return if (policyVersion == 1) {
        "{\"exactHosts\":${exactHosts.jsonArray()}," +
            "\"namedCapabilities\":${namedCapabilities.jsonArray()},\"operations\":$operationJson}"
    } else {
        val ruleJson = requestRules.map { rule ->
            "{\"exactHosts\":${rule.exactHosts.jsonArray()},\"operation\":${rule.operation.json()}}"
        }.sorted().joinToString(",", "[", "]")
        "{\"auth\":{\"exactHosts\":${authExactHosts.jsonArray()}}," +
            "\"external\":{\"exactHosts\":${externalExactHosts.jsonArray()}}," +
            "\"namedCapabilities\":${namedCapabilities.jsonArray()}," +
            "\"request\":{\"rules\":$ruleJson}," +
            "\"resource\":{\"exactHosts\":${resourceExactHosts.jsonArray()}},\"schemaVersion\":2}"
    }
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

private fun String.isCanonicalExactDnsHost(): Boolean {
    if (length !in 1..253 || this != lowercase() || '*' in this || ':' in this ||
        startsWith('.') || endsWith('.') || ".." in this || isIpv4Literal()
    ) return false
    return split('.').size >= 2 && split('.').all { label ->
        label.length in 1..63 && label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

private fun String.isIpv4Literal(): Boolean {
    val labels = split('.')
    return labels.size == 4 && labels.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
}

private val KNOWN_NAMED_CAPABILITIES = setOf(
    NamedHostCapabilities.PTT_ADULT_CONSENT_STATUS,
    NamedHostCapabilities.EYNY_CHALLENGE_PROOF,
    NamedHostCapabilities.RESOURCE_READ,
    NamedHostCapabilities.EXTERNAL_LINK,
)

private const val MAX_SCOPE_HOSTS = 32
private const val MAX_PATH_PREFIXES = 32
private const val MAX_NAMED_CAPABILITIES = 16
private const val MAX_REQUEST_RULES = 32

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
