package tw.kevinzhang.marketplace

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.AlgorithmParameters
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

internal class TrustedMetadataException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

internal data class TrustedMetadata(
    val type: String,
    val version: Long,
    val expires: Instant,
    val signed: JsonObject,
    val canonicalSigned: ByteArray,
)

internal data class MetadataDescriptor(
    val version: Long,
    val length: Long,
    val sha256: String,
) {
    init {
        require(version > 0)
        require(length >= 0)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
    }

    fun verify(bytes: ByteArray, name: String) {
        if (bytes.size.toLong() != length) {
            throw TrustedMetadataException("$name length mismatch")
        }
        val actual = bytes.sha256Hex()
        if (!MessageDigest.isEqual(actual.hexBytes(), sha256.hexBytes())) {
            throw TrustedMetadataException("$name hash mismatch")
        }
    }

    companion object {
        fun from(parent: JsonObject, name: String): MetadataDescriptor {
            val value = parent.requiredObject("meta").requiredObject(name)
            return MetadataDescriptor(
                version = value.requiredPositiveLong("version"),
                length = value.requiredNonNegativeLong("length"),
                sha256 = value.requiredObject("hashes").requiredString("sha256").lowercase(),
            )
        }
    }
}

/**
 * Verifies the current in-place extension repository metadata format. The embedded root is the
 * only bootstrap trust input; network metadata can rotate it only when both old and new root
 * thresholds sign the immediately consecutive version.
 */
internal class TrustedMetadataVerifier(
    embeddedRoot: ByteArray,
    private val now: () -> Instant = Instant::now,
) {
    private var rootEnvelope = parseEnvelope(embeddedRoot)
    private var root = RootTrust.from(rootEnvelope.signed)

    init {
        requireTypeAndVersion(rootEnvelope.signed, "root")
        verifySignatures(rootEnvelope, root, "root")
    }

    val rootVersion: Long get() = rootEnvelope.signed.requiredPositiveLong("version")
    val rootExpiresAtEpochMillis: Long get() = parseExpiry(rootEnvelope.signed, "root").toEpochMilli()

    fun updateRoot(candidateBytes: ByteArray) {
        val candidate = parseEnvelope(candidateBytes)
        requireTypeAndVersion(candidate.signed, "root")
        val candidateVersion = candidate.signed.requiredPositiveLong("version")
        if (candidateVersion != rootVersion + 1) {
            throw TrustedMetadataException("Root version must advance exactly once")
        }
        verifySignatures(candidate, root, "root")
        val candidateTrust = RootTrust.from(candidate.signed)
        verifySignatures(candidate, candidateTrust, "root")
        requireNotExpired(candidate.signed, "root")
        rootEnvelope = candidate
        root = candidateTrust
    }

    fun verify(bytes: ByteArray, role: String): TrustedMetadata {
        require(role in ONLINE_ROLES) { "Unsupported metadata role: $role" }
        requireNotExpired(rootEnvelope.signed, "root")
        val envelope = parseEnvelope(bytes)
        requireTypeAndVersion(envelope.signed, role)
        verifySignatures(envelope, root, role)
        requireNotExpired(envelope.signed, role)
        return TrustedMetadata(
            type = role,
            version = envelope.signed.requiredPositiveLong("version"),
            expires = parseExpiry(envelope.signed, role),
            signed = envelope.signed,
            canonicalSigned = envelope.canonicalSigned,
        )
    }

    private fun verifySignatures(envelope: Envelope, trust: RootTrust, roleName: String) {
        val role = trust.roles[roleName]
            ?: throw TrustedMetadataException("Root does not authorize $roleName")
        val accepted = linkedSetOf<String>()
        envelope.signatures.forEach { signatureObject ->
            val keyId = signatureObject.requiredString("keyid").lowercase()
            if (keyId !in role.keyIds || keyId in accepted) return@forEach
            val key = trust.keys[keyId] ?: return@forEach
            val signatureBytes = decodeBase64(signatureObject.requiredString("sig"), "signature")
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initVerify(key)
                update(envelope.canonicalSigned)
            }
            if (runCatching { verifier.verify(signatureBytes) }.getOrDefault(false)) {
                accepted += keyId
            }
        }
        if (accepted.size < role.threshold) {
            throw TrustedMetadataException(
                "$roleName signature threshold not met: ${accepted.size}/${role.threshold}",
            )
        }
    }

    private fun requireNotExpired(signed: JsonObject, role: String) {
        if (!parseExpiry(signed, role).isAfter(now())) {
            throw TrustedMetadataException("$role metadata is expired")
        }
    }

    private fun parseExpiry(signed: JsonObject, role: String): Instant = runCatching {
        Instant.parse(signed.requiredString("expires"))
    }.getOrElse { throw TrustedMetadataException("Invalid $role expiry", it) }

    private data class Envelope(
        val signed: JsonObject,
        val signatures: List<JsonObject>,
        val canonicalSigned: ByteArray,
    )

    private fun parseEnvelope(bytes: ByteArray): Envelope {
        if (bytes.isEmpty() || bytes.size > MAX_METADATA_BYTES) {
            throw TrustedMetadataException("Metadata size is outside safety bounds")
        }
        val rootObject = StrictJson.parseObject(bytes)
        val signed = rootObject.requiredObject("signed")
        val signatures = rootObject.requiredArray("signatures").map { element ->
            runCatching { element.asJsonObject }
                .getOrElse { throw TrustedMetadataException("Malformed metadata signature", it) }
        }
        if (signatures.size !in 1..MAX_SIGNATURES) {
            throw TrustedMetadataException("Invalid metadata signature count")
        }
        return Envelope(signed, signatures, CanonicalJson.encode(signed))
    }

    private data class RootTrust(
        val keys: Map<String, java.security.PublicKey>,
        val roles: Map<String, RoleTrust>,
    ) {
        companion object {
            fun from(signed: JsonObject): RootTrust {
                val keyObjects = signed.requiredObject("keys")
                if (keyObjects.size() !in 1..MAX_ROOT_KEYS) {
                    throw TrustedMetadataException("Invalid root key count")
                }
                val keys = keyObjects.entrySet().associate { (declaredId, element) ->
                    val normalizedId = declaredId.lowercase()
                    if (!normalizedId.matches(Regex("[a-f0-9]{64}"))) {
                        throw TrustedMetadataException("Malformed root key id")
                    }
                    val keyObject = element.asJsonObject
                    if (keyObject.requiredString("keytype") != KEY_TYPE ||
                        keyObject.requiredString("scheme") != KEY_SCHEME
                    ) {
                        throw TrustedMetadataException("Unsupported root key type")
                    }
                    val encoded = decodeBase64(
                        keyObject.requiredObject("keyval").requiredString("public"),
                        "public key",
                    )
                    if (encoded.sha256Hex() != normalizedId) {
                        throw TrustedMetadataException("Root key id does not match public key")
                    }
                    val publicKey = runCatching {
                        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
                    }.getOrElse { throw TrustedMetadataException("Invalid root public key", it) }
                    if (!isP256(publicKey as? ECPublicKey)) {
                        throw TrustedMetadataException("Root key must use the P-256 curve")
                    }
                    normalizedId to publicKey
                }
                val rolesObject = signed.requiredObject("roles")
                val roles = REQUIRED_ROLES.associateWith { roleName ->
                    val role = rolesObject.requiredObject(roleName)
                    val keyIds = role.requiredArray("keyids").mapTo(linkedSetOf()) {
                        it.requiredStringValue("$roleName key id").lowercase()
                    }
                    val threshold = role.requiredPositiveLong("threshold").toInt()
                    if (keyIds.isEmpty() || threshold !in 1..keyIds.size || keyIds.any { it !in keys }) {
                        throw TrustedMetadataException("Invalid $roleName root role")
                    }
                    RoleTrust(keyIds, threshold)
                }
                return RootTrust(keys, roles)
            }
        }
    }

    private data class RoleTrust(val keyIds: Set<String>, val threshold: Int)

    companion object {
        private const val MAX_METADATA_BYTES = 1024 * 1024
        private const val MAX_SIGNATURES = 32
        private const val MAX_ROOT_KEYS = 64
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val KEY_TYPE = "ecdsa"
        private const val KEY_SCHEME = "ecdsa-sha2-nistp256"
        private val REQUIRED_ROLES = setOf("root", "targets", "snapshot", "timestamp")
        private val ONLINE_ROLES = setOf("targets", "snapshot", "timestamp")
    }
}

internal object CanonicalJson {
    // TUF metadata is signed by Python's canonical JSON encoder, which leaves HTML-sensitive
    // characters (including Base64 padding `=`) unescaped. Gson's default HTML escaping would
    // rewrite those bytes (for example, `=` as `\u003d`) and invalidate otherwise valid signatures.
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val integerPattern = Regex("-?(0|[1-9][0-9]*)")

    fun encode(element: JsonElement): ByteArray = buildString {
        appendCanonical(element)
    }.toByteArray(StandardCharsets.UTF_8)

    private fun StringBuilder.appendCanonical(element: JsonElement) {
        when {
            element.isJsonNull -> append("null")
            element.isJsonObject -> {
                append('{')
                element.asJsonObject.entrySet().sortedBy { it.key }.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    append(gson.toJson(entry.key))
                    append(':')
                    appendCanonical(entry.value)
                }
                append('}')
            }
            element.isJsonArray -> {
                append('[')
                element.asJsonArray.forEachIndexed { index, value ->
                    if (index > 0) append(',')
                    appendCanonical(value)
                }
                append(']')
            }
            else -> {
                val primitive = element.asJsonPrimitive
                when {
                    primitive.isBoolean -> append(if (primitive.asBoolean) "true" else "false")
                    primitive.isString -> append(gson.toJson(primitive.asString))
                    primitive.isNumber -> {
                        val value = primitive.toString()
                        if (!integerPattern.matches(value)) {
                            throw TrustedMetadataException("Canonical metadata permits integers only")
                        }
                        append(value)
                    }
                    else -> throw TrustedMetadataException("Unsupported JSON primitive")
                }
            }
        }
    }
}

private object StrictJson {
    private val integerPattern = Regex("-?(0|[1-9][0-9]*)")

    fun parseObject(bytes: ByteArray): JsonObject = runCatching {
        JsonReader(StringReader(bytes.toString(StandardCharsets.UTF_8))).use { reader ->
            reader.isLenient = false
            val result = readValue(reader).asJsonObject
            if (reader.peek() != JsonToken.END_DOCUMENT) error("Trailing JSON content")
            result
        }
    }.getOrElse { throw TrustedMetadataException("Malformed or ambiguous metadata JSON", it) }

    private fun readValue(reader: JsonReader): JsonElement = when (reader.peek()) {
        JsonToken.BEGIN_OBJECT -> JsonObject().also { result ->
            reader.beginObject()
            val names = hashSetOf<String>()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (!names.add(name)) error("Duplicate JSON member: $name")
                result.add(name, readValue(reader))
            }
            reader.endObject()
        }
        JsonToken.BEGIN_ARRAY -> JsonArray().also { result ->
            reader.beginArray()
            while (reader.hasNext()) result.add(readValue(reader))
            reader.endArray()
        }
        JsonToken.STRING -> JsonPrimitive(reader.nextString())
        JsonToken.NUMBER -> {
            val raw = reader.nextString()
            if (!integerPattern.matches(raw)) error("Non-canonical JSON number")
            JsonPrimitive(raw.toLong())
        }
        JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
        JsonToken.NULL -> {
            reader.nextNull()
            JsonNull.INSTANCE
        }
        else -> error("Unexpected JSON token: ${reader.peek()}")
    }
}

private fun requireTypeAndVersion(signed: JsonObject, expectedType: String) {
    if (signed.requiredString("_type") != expectedType) {
        throw TrustedMetadataException("Expected $expectedType metadata")
    }
    signed.requiredPositiveLong("version")
    if (signed.requiredString("specVersion") != "1.0") {
        throw TrustedMetadataException("Unsupported metadata specification")
    }
}

internal fun JsonObject.requiredObject(name: String): JsonObject = runCatching {
    get(name)?.asJsonObject ?: error("missing")
}.getOrElse { throw TrustedMetadataException("Missing or invalid object: $name", it) }

internal fun JsonObject.requiredArray(name: String): JsonArray = runCatching {
    get(name)?.asJsonArray ?: error("missing")
}.getOrElse { throw TrustedMetadataException("Missing or invalid array: $name", it) }

internal fun JsonObject.requiredString(name: String): String = runCatching {
    get(name)?.asJsonPrimitive?.takeIf { it.isString }?.asString
        ?.takeIf(String::isNotEmpty) ?: error("missing")
}.getOrElse { throw TrustedMetadataException("Missing or invalid string: $name", it) }

internal fun JsonObject.requiredPositiveLong(name: String): Long = runCatching {
    get(name)?.asJsonPrimitive?.takeIf { it.isNumber }?.asLong
        ?.takeIf { it > 0 } ?: error("invalid")
}.getOrElse { throw TrustedMetadataException("Missing or invalid positive integer: $name", it) }

internal fun JsonObject.requiredNonNegativeLong(name: String): Long = runCatching {
    get(name)?.asJsonPrimitive?.takeIf { it.isNumber }?.asLong
        ?.takeIf { it >= 0 } ?: error("invalid")
}.getOrElse { throw TrustedMetadataException("Missing or invalid non-negative integer: $name", it) }

internal fun JsonElement.requiredStringValue(label: String): String = runCatching {
    asJsonPrimitive.takeIf { it.isString }?.asString?.takeIf(String::isNotEmpty)
        ?: error("invalid")
}.getOrElse { throw TrustedMetadataException("Missing or invalid string: $label", it) }

private fun decodeBase64(value: String, label: String): ByteArray = runCatching {
    Base64.getDecoder().decode(value)
}.getOrElse { throw TrustedMetadataException("Invalid base64 $label", it) }

private val p256Parameters: ECParameterSpec by lazy {
    AlgorithmParameters.getInstance("EC").apply {
        init(ECGenParameterSpec("secp256r1"))
    }.getParameterSpec(ECParameterSpec::class.java)
}

private fun isP256(key: ECPublicKey?): Boolean {
    val actual = key?.params ?: return false
    val expected = p256Parameters
    return actual.curve == expected.curve &&
        actual.generator == expected.generator &&
        actual.order == expected.order &&
        actual.cofactor == expected.cofactor
}

private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }

private fun String.hexBytes(): ByteArray {
    if (length % 2 != 0) throw TrustedMetadataException("Malformed hexadecimal value")
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
