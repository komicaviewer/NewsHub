package tw.kevinzhang.data.domain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import tw.kevinzhang.extension_api.SourceIdentity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import tw.kevinzhang.extension_api.BUILTIN_REPOSITORY_DOMAIN_ID

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
        Index(value = ["repositoryDomainId", "packageName", "signerSha256", "sourceId"], unique = true),
    ],
)
data class SourceIdentityEntity(
    @PrimaryKey val sourceKey: String,
    val packageName: String?,
    /** Stable signing-lineage anchor. This is deliberately not the APK's current signer. */
    val signerSha256: String?,
    /** Null only for unresolved legacy rows that remain permanently isolated. */
    val repositoryDomainId: String?,
    val sourceId: String,
    val resolution: SourceResolution,
) {
    init {
        when (resolution) {
            SourceResolution.OFFICIAL -> {
                require(sourceKey.matches(Regex("[a-f0-9]{64}")))
                require(!packageName.isNullOrBlank())
                require(signerSha256?.matches(Regex("[a-f0-9]{64}")) == true)
                require(
                    runCatching {
                        UUID.fromString(repositoryDomainId).toString() == repositoryDomainId
                    }.getOrDefault(false),
                )
                require(sourceId.isNotBlank())
            }
            SourceResolution.UNRESOLVED -> {
                require(sourceKey.matches(Regex("unresolved:[a-f0-9]{64}")))
                require(packageName == null && signerSha256 == null && repositoryDomainId == null)
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
 * owner can be resolved. Runtime identities have already been verified by a repository trust
 * domain and the Host loader; this layer persists that domain and the stable lineage anchor, and
 * never includes the current signer in sourceKey. `OFFICIAL` is retained as a storage-compatible
 * enum name, but means a domain-scoped trusted identity rather than global official ownership.
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
        return trusted(BUILTIN_REPOSITORY_DOMAIN_ID, bundle.packageName, bundle.lineageRootSha256, sourceId)
    }

    /** Historical v8 representation used only while traversing the released 7→8 schema. */
    internal fun fromLegacySourceIdForVersion8(sourceId: String): Version8SourceIdentity {
        val bundle = uniqueLegacyOfficialBundle(sourceId)
            ?: return Version8SourceIdentity(
                sourceKey = "unresolved:${sha256("newshub-unresolved\u0000legacy\u0000$sourceId")}",
                packageName = null,
                signerSha256 = null,
                sourceId = sourceId,
                resolution = SourceResolution.UNRESOLVED,
            )
        val lineageRoot = bundle.lineageRootSha256.lowercase(Locale.ROOT)
        val canonical = listOf(bundle.packageName, lineageRoot, sourceId)
            .joinToString(separator = "\u0000", prefix = "newshub-source\u0000")
        return Version8SourceIdentity(
            sourceKey = sha256(canonical),
            packageName = bundle.packageName,
            signerSha256 = lineageRoot,
            sourceId = sourceId,
            resolution = SourceResolution.OFFICIAL,
        )
    }

    fun fromRuntimeIdentity(identity: SourceIdentity): SourceIdentityEntity =
        trusted(identity.repositoryDomainId, identity.packageName, identity.signerSha256, identity.sourceId)

    private fun trusted(
        repositoryDomainId: String,
        packageName: String,
        lineageRootSha256: String,
        sourceId: String,
    ): SourceIdentityEntity {
        require(UUID.fromString(repositoryDomainId).toString() == repositoryDomainId) {
            "Repository domain id must be a canonical UUID"
        }
        val normalizedLineageRoot = lineageRootSha256.lowercase(Locale.ROOT)
        val canonical = listOf(repositoryDomainId, packageName, normalizedLineageRoot, sourceId)
            .joinToString(separator = "\u0000", prefix = "newshub-source\u0000")
        return SourceIdentityEntity(
            sourceKey = sha256(canonical),
            packageName = packageName,
            signerSha256 = normalizedLineageRoot,
            repositoryDomainId = repositoryDomainId,
            sourceId = sourceId,
            resolution = SourceResolution.OFFICIAL,
        )
    }

    private fun unresolved(sourceId: String, canonical: String) = SourceIdentityEntity(
        sourceKey = "unresolved:${sha256("newshub-unresolved\u0000$canonical")}",
        packageName = null,
        signerSha256 = null,
        repositoryDomainId = null,
        sourceId = sourceId,
        resolution = SourceResolution.UNRESOLVED,
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal data class Version8SourceIdentity(
    val sourceKey: String,
    val packageName: String?,
    val signerSha256: String?,
    val sourceId: String,
    val resolution: SourceResolution,
)
