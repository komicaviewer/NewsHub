package tw.kevinzhang.marketplace

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

class TrustedMetadataVerifierTest {
    private val rootOne = keyPair()
    private val rootTwo = keyPair()
    private val targets = keyPair()
    private val snapshot = keyPair()
    private val timestamp = keyPair()
    private val now = Instant.parse("2026-08-10T00:00:00Z")

    @Test
    fun `threshold signed metadata is accepted and tampering is rejected`() {
        val verifier = TrustedMetadataVerifier(root(version = 1), now = { now })
        val valid = metadata("targets", 4, listOf(targets))

        assertEquals(4L, verifier.verify(valid, "targets").version)

        val tampered = valid.copyOf().also { bytes ->
            val index = bytes.indexOfSequence("targets".toByteArray())
            bytes[index] = 'x'.code.toByte()
        }
        assertThrows(TrustedMetadataException::class.java) {
            verifier.verify(tampered, "targets")
        }
    }

    @Test
    fun `missing threshold unknown key and expired metadata fail closed`() {
        val oneRootSignature = root(version = 1, rootSigners = listOf(rootOne))
        assertThrows(TrustedMetadataException::class.java) {
            TrustedMetadataVerifier(oneRootSignature, now = { now })
        }

        val verifier = TrustedMetadataVerifier(root(version = 1), now = { now })
        assertThrows(TrustedMetadataException::class.java) {
            verifier.verify(metadata("targets", 1, listOf(keyPair())), "targets")
        }
        assertThrows(TrustedMetadataException::class.java) {
            verifier.verify(
                metadata(
                    type = "targets",
                    version = 1,
                    signers = listOf(targets),
                    expires = "2026-08-09T23:59:59Z",
                ),
                "targets",
            )
        }
    }

    @Test
    fun `root rotation requires consecutive version and both thresholds`() {
        val verifier = TrustedMetadataVerifier(root(version = 1), now = { now })
        val nextRootOne = keyPair()
        val nextRootTwo = keyPair()
        val nextRoot = root(
            version = 2,
            rootKeys = listOf(nextRootOne, nextRootTwo),
            rootSigners = listOf(rootOne, rootTwo, nextRootOne, nextRootTwo),
        )
        verifier.updateRoot(nextRoot)
        assertEquals(2L, verifier.rootVersion)

        assertThrows(TrustedMetadataException::class.java) {
            verifier.updateRoot(
                root(
                    version = 4,
                    rootKeys = listOf(nextRootOne, nextRootTwo),
                    rootSigners = listOf(nextRootOne, nextRootTwo),
                ),
            )
        }
    }

    @Test
    fun `root rejects role keys outside P256`() {
        assertThrows(TrustedMetadataException::class.java) {
            TrustedMetadataVerifier(
                root(version = 1, targetsKey = keyPair("secp384r1")),
                now = { now },
            )
        }
    }

    @Test
    fun `descriptor enforces exact hash and length`() {
        val bytes = "snapshot".toByteArray()
        val descriptor = MetadataDescriptor(3, bytes.size.toLong(), bytes.sha256Hex())
        descriptor.verify(bytes, "snapshot")
        assertThrows(TrustedMetadataException::class.java) {
            descriptor.verify("snapshot!".toByteArray(), "snapshot")
        }
    }

    @Test
    fun `same targets version is immutable`() {
        val trusted = "trusted targets".toByteArray()
        rejectSameVersionReplacement("targets", 4, 4, trusted, trusted.copyOf())

        val error = assertThrows(TrustedMetadataException::class.java) {
            rejectSameVersionReplacement(
                "targets",
                4,
                4,
                "replacement".toByteArray(),
                trusted,
            )
        }
        assertTrue(error.message.orEmpty().contains("without a version increment"))
    }

    @Test
    fun `persisted trust expires at the earliest repository role`() {
        assertEquals(
            100L,
            minimumExpiryEpochMillis(400L, 100L, 300L, 200L),
        )
    }

    private fun root(
        version: Long,
        rootKeys: List<KeyPair> = listOf(rootOne, rootTwo),
        rootSigners: List<KeyPair> = rootKeys,
        targetsKey: KeyPair = targets,
    ): ByteArray {
        val signed = base("root", version)
        signed.addProperty("consistentSnapshot", true)
        val allKeys = (rootKeys + targetsKey + snapshot + timestamp).distinctBy { it.keyId() }
        signed.add("keys", JsonObject().apply {
            allKeys.forEach { keyPair -> add(keyPair.keyId(), keyObject(keyPair)) }
        })
        signed.add("roles", JsonObject().apply {
            add("root", role(rootKeys, threshold = 2))
            add("targets", role(listOf(targetsKey), threshold = 1))
            add("snapshot", role(listOf(snapshot), threshold = 1))
            add("timestamp", role(listOf(timestamp), threshold = 1))
        })
        return envelope(signed, rootSigners)
    }

    private fun metadata(
        type: String,
        version: Long,
        signers: List<KeyPair>,
        expires: String = "2030-01-01T00:00:00Z",
    ): ByteArray = envelope(base(type, version, expires), signers)

    private fun base(
        type: String,
        version: Long,
        expires: String = "2030-01-01T00:00:00Z",
    ) = JsonObject().apply {
        addProperty("_type", type)
        addProperty("specVersion", "1.0")
        addProperty("version", version)
        addProperty("expires", expires)
    }

    private fun role(keys: List<KeyPair>, threshold: Int) = JsonObject().apply {
        add("keyids", JsonArray().apply { keys.forEach { add(it.keyId()) } })
        addProperty("threshold", threshold)
    }

    private fun keyObject(keyPair: KeyPair) = JsonObject().apply {
        addProperty("keytype", "ecdsa")
        addProperty("scheme", "ecdsa-sha2-nistp256")
        add("keyval", JsonObject().apply {
            addProperty("public", Base64.getEncoder().encodeToString(keyPair.public.encoded))
        })
    }

    private fun envelope(signed: JsonObject, signers: List<KeyPair>): ByteArray {
        val canonical = CanonicalJson.encode(signed)
        val signatures = JsonArray().apply {
            signers.forEach { signer ->
                add(JsonObject().apply {
                    addProperty("keyid", signer.keyId())
                    addProperty("sig", Base64.getEncoder().encodeToString(signer.sign(canonical)))
                })
            }
        }
        return JsonObject().apply {
            add("signed", signed)
            add("signatures", signatures)
        }.toString().toByteArray()
    }

    private fun keyPair(curve: String = "secp256r1"): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec(curve))
    }.generateKeyPair()

    private fun KeyPair.keyId(): String = public.encoded.sha256Hex()

    private fun KeyPair.sign(bytes: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(private)
        update(bytes)
        sign()
    }

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private fun ByteArray.indexOfSequence(value: ByteArray): Int {
        for (start in 0..size - value.size) {
            if (value.indices.all { this[start + it] == value[it] }) return start
        }
        error("Sequence not found")
    }
}
