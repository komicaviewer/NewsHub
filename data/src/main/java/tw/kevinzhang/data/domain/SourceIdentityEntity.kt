package tw.kevinzhang.data.domain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import tw.kevinzhang.extension_api.SourceIdentity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

enum class SourceResolution {
    OFFICIAL,
    UNRESOLVED,
}

class SourceResolutionConverter {
    @TypeConverter
    fun fromStorage(value: String): SourceResolution = SourceResolution.valueOf(value)

    @TypeConverter
    fun toStorage(value: SourceResolution): String = value.name
}

@Entity(
    tableName = "source_identities",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["packageName", "signerSha256", "sourceId"], unique = true),
    ],
)
data class SourceIdentityEntity(
    @PrimaryKey val sourceKey: String,
    val packageName: String?,
    /** Stable signing-lineage anchor. This is deliberately not the APK's current signer. */
    val signerSha256: String?,
    val sourceId: String,
    val resolution: SourceResolution,
) {
    init {
        when (resolution) {
            SourceResolution.OFFICIAL -> {
                require(sourceKey.matches(Regex("[a-f0-9]{64}")))
                require(!packageName.isNullOrBlank())
                require(signerSha256?.matches(Regex("[a-f0-9]{64}")) == true)
                require(sourceId.isNotBlank())
            }
            SourceResolution.UNRESOLVED -> {
                require(sourceKey.matches(Regex("unresolved:[a-f0-9]{64}")))
                require(packageName == null && signerSha256 == null)
                require(sourceId.isNotBlank())
            }
        }
    }

    val canAccessNetworkOrCredentials: Boolean
        get() = resolution == SourceResolution.OFFICIAL
}

/**
 * Canonical identity storage policy.
 *
 * Legacy rows contain only sourceId, so only a sourceId with exactly one historical official
 * owner can be resolved. Runtime identities have already been verified by the Host loader; this
 * layer persists their stable lineage anchor and never includes the current signer in sourceKey.
 */
object CanonicalSourceIdentities {
    private const val LEGACY_RELEASE_LINEAGE_ROOT_SHA256 =
        "3df4717435423d5ba7adfed43a22a6e18bbeadc8d509d0bea94d82c7b0f2998d"

    private data class LegacyOfficialBundle(
        val packageName: String,
        val lineageRootSha256: String,
        val sourceIds: Set<String>,
    )

    // This table is migration history, not the runtime trust policy. The Host owns current pins.
    private val legacyOfficialBundles = listOf(
        LegacyOfficialBundle(
            "tw.kevinzhang.newshub.extension.eyny",
            LEGACY_RELEASE_LINEAGE_ROOT_SHA256,
            setOf("tw.kevinzhang.eyny"),
        ),
        LegacyOfficialBundle(
            "tw.kevinzhang.newshub.extension.gamer",
            LEGACY_RELEASE_LINEAGE_ROOT_SHA256,
            setOf("tw.kevinzhang.newshub.extension.gamer"),
        ),
        LegacyOfficialBundle(
            "tw.kevinzhang.newshub.extension.hackernews",
            LEGACY_RELEASE_LINEAGE_ROOT_SHA256,
            setOf("tw.kevinzhang.newshub.extension.hackernews"),
        ),
        LegacyOfficialBundle(
            "tw.kevinzhang.newshub.extension.komica",
            LEGACY_RELEASE_LINEAGE_ROOT_SHA256,
            setOf(
                "tw.kevinzhang.komica.twocat",
                "tw.kevinzhang.komica.sora",
                "tw.kevinzhang.akraft",
                "tw.kevinzhang.nagatoyuki",
                "tw.kevinzhang.wtako",
            ),
        ),
        LegacyOfficialBundle(
            "tw.kevinzhang.newshub.extension.komica2",
            LEGACY_RELEASE_LINEAGE_ROOT_SHA256,
            setOf(
                "tw.kevinzhang.komica2.twocat",
                "tw.kevinzhang.komica2.sora",
                "tw.kevinzhang.komica2.zawarudo",
            ),
        ),
        LegacyOfficialBundle(
            "tw.kevinzhang.newshub.extension.mobile01",
            LEGACY_RELEASE_LINEAGE_ROOT_SHA256,
            setOf("tw.kevinzhang.mobile01"),
        ),
        LegacyOfficialBundle(
            "tw.kevinzhang.newshub.extension.ptt",
            LEGACY_RELEASE_LINEAGE_ROOT_SHA256,
            setOf("tw.kevinzhang.newshub.extension.ptt"),
        ),
    )

    private fun uniqueLegacyOfficialBundle(sourceId: String): LegacyOfficialBundle? =
        legacyOfficialBundles
            .filter { sourceId in it.sourceIds }
            .singleOrNull()

    fun fromLegacySourceId(sourceId: String): SourceIdentityEntity {
        val bundle = uniqueLegacyOfficialBundle(sourceId)
            ?: return unresolved(sourceId, "legacy\u0000$sourceId")
        return official(bundle.packageName, bundle.lineageRootSha256, sourceId)
    }

    fun fromRuntimeIdentity(identity: SourceIdentity): SourceIdentityEntity =
        official(identity.packageName, identity.signerSha256, identity.sourceId)

    private fun official(packageName: String, lineageRootSha256: String, sourceId: String): SourceIdentityEntity {
        val normalizedLineageRoot = lineageRootSha256.lowercase(Locale.ROOT)
        val canonical = listOf(packageName, normalizedLineageRoot, sourceId)
            .joinToString(separator = "\u0000", prefix = "newshub-source\u0000")
        return SourceIdentityEntity(
            sourceKey = sha256(canonical),
            packageName = packageName,
            signerSha256 = normalizedLineageRoot,
            sourceId = sourceId,
            resolution = SourceResolution.OFFICIAL,
        )
    }

    private fun unresolved(sourceId: String, canonical: String) = SourceIdentityEntity(
        sourceKey = "unresolved:${sha256("newshub-unresolved\u0000$canonical")}",
        packageName = null,
        signerSha256 = null,
        sourceId = sourceId,
        resolution = SourceResolution.UNRESOLVED,
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
