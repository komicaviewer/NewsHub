package tw.kevinzhang.extension_api

/** Constructed by the host so the loader remains Android/UI agnostic. */
fun interface SourceRuntimeProvider {
    fun runtimeFor(sourceId: String): SourceRuntime
}
