package tw.kevinzhang.newshub.ui.component

import tw.kevinzhang.extension_api.ResourceHandle

/**
 * Typed Coil model for a Host-issued capability. Keeping the handle out of String-based model
 * dispatch prevents another String fetcher from interpreting it as a URI.
 */
data class ResourceModel(val handle: ResourceHandle) {
    /** Coil and diagnostics may stringify models; never expose the capability token. */
    override fun toString(): String =
        "ResourceModel(sourceSession=${handle.sourceSession}, generation=${handle.generation}, token=REDACTED)"
}

/** Bare URLs and arbitrary schemes fail shut before reaching Coil. */
internal fun resourceModelOrNull(model: String?): ResourceModel? =
    model?.let(ResourceHandle::parse)?.let(::ResourceModel)
