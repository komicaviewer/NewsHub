package tw.kevinzhang.extension_loader

import android.content.pm.ServiceInfo
import tw.kevinzhang.extension_api.ExtensionProtocol

/** Host-owned metadata for one isolated Source service. */
data class ExtensionDescriptor(
    val packageName: String,
    val serviceClassName: String,
    val processName: String,
    val sourceId: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val protocol: Int,
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
        require(service.processName.startsWith("${service.packageName}:")) {
            "Source service must use a private package process"
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
            protocol = metadata.getInt(ExtensionProtocol.META_PROTOCOL),
            needsLogin = needsLogin,
            loginUrl = loginUrl,
            loginHosts = loginHosts,
        )
    }
}
