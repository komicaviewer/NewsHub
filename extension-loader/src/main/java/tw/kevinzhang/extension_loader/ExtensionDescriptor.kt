package tw.kevinzhang.extension_loader

import org.json.JSONObject
import tw.kevinzhang.extension_api.Source

/**
 * The asset contract used by extension bundle APKs.
 *
 * The extension manifest identifies an APK as a NewsHub extension and points at this asset;
 * this document then declares every [Source] the bundle provides. Keeping source metadata in
 * one document allows one APK to supply more than one source without a parallel manifest API.
 */
data class ExtensionDescriptor(
    val schemaVersion: Int,
    val name: String,
    val sources: List<SourceDescriptor>,
)

data class SourceDescriptor(
    val className: String,
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
)

internal object ExtensionDescriptorJson {
    fun parse(json: String): ExtensionDescriptor {
        val root = JSONObject(json)
        val sources = root.getJSONArray("sources")
        return ExtensionDescriptor(
            schemaVersion = root.getInt("schemaVersion"),
            name = root.requiredString("name"),
            sources = List(sources.length()) { index ->
                val source = sources.getJSONObject(index)
                SourceDescriptor(
                    className = source.requiredString("className"),
                    id = source.requiredString("id"),
                    name = source.requiredString("name"),
                    lang = source.requiredString("lang"),
                    baseUrl = source.requiredString("baseUrl"),
                )
            },
        ).also(ExtensionDescriptorValidator::validate)
    }

    private fun JSONObject.requiredString(name: String): String =
        getString(name).trim().also { value ->
            require(value.isNotEmpty()) { "'$name' must not be blank" }
        }
}

/** Pure validation kept separate from Android package loading so malformed registries are testable. */
internal object ExtensionDescriptorValidator {
    const val SCHEMA_VERSION = 1

    fun validate(descriptor: ExtensionDescriptor) {
        require(descriptor.schemaVersion == SCHEMA_VERSION) {
            "Unsupported extension descriptor schema version: ${descriptor.schemaVersion}"
        }
        require(descriptor.name.isNotBlank()) { "Extension name must not be blank" }
        require(descriptor.sources.isNotEmpty()) { "Extension descriptor must declare at least one source" }

        descriptor.sources.forEach { source ->
            require(source.className.isNotBlank()) { "Source className must not be blank" }
            require(source.id.isNotBlank()) { "Source id must not be blank" }
            require(source.name.isNotBlank()) { "Source name must not be blank" }
            require(source.lang.isNotBlank()) { "Source lang must not be blank" }
            require(source.baseUrl.isNotBlank()) { "Source baseUrl must not be blank" }
        }
        val duplicateIds = descriptor.sources.groupBy(SourceDescriptor::id)
            .filterValues { it.size > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Extension descriptor contains duplicate source ids: ${duplicateIds.joinToString()}"
        }
    }

    fun validateRuntimeSource(descriptor: SourceDescriptor, source: Source) {
        require(descriptor.id == source.id) {
            "Source id mismatch for ${descriptor.className}: descriptor=${descriptor.id}, runtime=${source.id}"
        }
        require(descriptor.name == source.name) {
            "Source name mismatch for ${descriptor.className}: descriptor=${descriptor.name}, runtime=${source.name}"
        }
        require(descriptor.lang == source.language) {
            "Source language mismatch for ${descriptor.className}: descriptor=${descriptor.lang}, runtime=${source.language}"
        }
    }
}
