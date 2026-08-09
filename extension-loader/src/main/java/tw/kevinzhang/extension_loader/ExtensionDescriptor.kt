package tw.kevinzhang.extension_loader

import android.content.pm.ServiceInfo
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.SourceNetworkPolicy

/** Host-owned metadata for one isolated Source service. */
data class ExtensionDescriptor(
    val packageName: String,
    val serviceClassName: String,
    val processName: String,
    val sourceId: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val needsLogin: Boolean,
    val loginUrl: String?,
    val loginHosts: Set<String>,
)

internal object ExtensionDescriptorValidator {
    fun fromServiceInfo(service: ServiceInfo): ExtensionDescriptor {
        val metadata = requireNotNull(service.metaData) { "Missing Source service metadata" }
        require(metadata.getInt(ExtensionProtocol.META_PROTOCOL, -1) == ExtensionProtocol.VERSION) {
            "Unsupported extension protocol"
        }
        require(service.exported) { "Source service must be exported" }
        require(service.permission == ExtensionProtocol.BIND_PERMISSION) {
            "Source service must require ${ExtensionProtocol.BIND_PERMISSION}"
        }
        require(service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0) {
            "Source service must use isolatedProcess"
        }
        require(service.flags and ServiceInfo.FLAG_EXTERNAL_SERVICE == 0) {
            "Source service must not use externalService"
        }

        fun required(key: String): String = metadata.getString(key)?.trim().orEmpty().also {
            require(it.isNotEmpty()) { "Missing $key" }
            require(it.length <= 512) { "$key is too long" }
        }

        val sourceId = required(ExtensionProtocol.META_SOURCE_ID)
        require(sourceId.matches(Regex("[A-Za-z0-9._-]{1,160}"))) { "Invalid Source id" }
        val baseUrl = required(ExtensionProtocol.META_SOURCE_BASE_URL)
        val parsedBase = runCatching { java.net.URI(baseUrl) }.getOrNull()
        require(parsedBase?.scheme == "https" && !parsedBase.host.isNullOrBlank()) {
            "Source base URL must be HTTPS"
        }
        val needsLogin = metadata.getBoolean(ExtensionProtocol.META_NEEDS_LOGIN, false)
        val loginUrl = metadata.getString(ExtensionProtocol.META_LOGIN_URL)?.trim()?.takeIf(String::isNotEmpty)
        val loginHosts = metadata.getString(ExtensionProtocol.META_LOGIN_HOSTS)
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        require(!needsLogin || (loginUrl != null && loginHosts.isNotEmpty())) {
            "Authenticated Source must declare login URL and exact hosts"
        }

        return ExtensionDescriptor(
            packageName = service.packageName,
            serviceClassName = service.name,
            processName = service.processName,
            sourceId = sourceId,
            name = required(ExtensionProtocol.META_SOURCE_NAME),
            lang = required(ExtensionProtocol.META_SOURCE_LANG),
            baseUrl = baseUrl,
            needsLogin = needsLogin,
            loginUrl = loginUrl,
            loginHosts = loginHosts,
        )
    }
}

internal data class OfficialSourcePolicy(
    val packageName: String,
    val sourceId: String,
    val exactHosts: Set<String>,
) {
    fun networkPolicy() = SourceNetworkPolicy(
        exactHosts = exactHosts,
        operations = setOf(
            "catalog",
            "thread_summaries",
            "thread",
            "comments",
            "validate_session",
            "resource",
        ).associateWith { operation ->
            NetworkOperationPolicy(
                name = operation,
                methods = setOf("GET", "HEAD"),
                pathPrefixes = setOf("/"),
                credentialed = operation == "validate_session" || operation == "thread" || operation == "comments",
            )
        },
    )
}

/**
 * Bootstrap trust root. It is intentionally code-owned until the offline-root TUF client lands;
 * unknown packages and Source ids fail closed instead of falling back to a custom repository.
 */
internal object OfficialExtensionCatalog {
    const val RELEASE_SIGNER_SHA256 =
        "3df4717435423d5ba7adfed43a22a6e18bbeadc8d509d0bea94d82c7b0f2998d"

    private val entries = listOf(
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.eyny", "tw.kevinzhang.eyny", setOf("eyny.com", "www.eyny.com")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.gamer", "tw.kevinzhang.newshub.extension.gamer", setOf("forum.gamer.com.tw")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.hackernews", "tw.kevinzhang.newshub.extension.hackernews", setOf("news.ycombinator.com")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.komica.twocat", setOf("2cat.org")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.komica.sora", setOf("komica1.org")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.akraft", setOf("www.akraft.net")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.nagatoyuki", setOf("eclair.nagatoyuki.org")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.wtako", setOf("kemono.wtako.net")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.komica2", "tw.kevinzhang.komica2.twocat", setOf("2cat.org")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.komica2", "tw.kevinzhang.komica2.sora", setOf("komica1.org")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.komica2", "tw.kevinzhang.komica2.zawarudo", setOf("majeur.zawarudo.org")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.mobile01", "tw.kevinzhang.mobile01", setOf("www.mobile01.com")),
        OfficialSourcePolicy("tw.kevinzhang.newshub.extension.ptt", "tw.kevinzhang.newshub.extension.ptt", setOf("www.ptt.cc")),
    )

    fun policyFor(packageName: String, sourceId: String): OfficialSourcePolicy? =
        entries.singleOrNull { it.packageName == packageName && it.sourceId == sourceId }

    fun isOfficialPackage(packageName: String): Boolean = entries.any { it.packageName == packageName }
}
