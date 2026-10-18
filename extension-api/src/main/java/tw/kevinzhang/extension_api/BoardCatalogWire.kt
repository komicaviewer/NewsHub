package tw.kevinzhang.extension_api

import com.google.gson.*
import java.lang.reflect.Type
import tw.kevinzhang.extension_api.model.*

/** Construct validated models explicitly: Gson reflection bypasses Kotlin initializers. */
internal class BoardPageRequestAdapter : JsonDeserializer<BoardPageRequest> {
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): BoardPageRequest {
        val obj = json.asJsonObject
        val query = obj.optional("query")?.asJsonObject
        return BoardPageRequest(
            query = BoardQuery(query?.optional("text")?.asString.orEmpty(), query?.optional("categoryId")?.asString),
            pageToken = obj.optional("pageToken")?.asString,
            pageSize = obj.optional("pageSize")?.asInt ?: 30,
            refresh = obj.optional("refresh")?.asBoolean ?: false,
            contextEpoch = obj.optional("contextEpoch")?.asString,
        )
    }
}

internal class BoardCatalogInfoAdapter : JsonDeserializer<BoardCatalogInfo> {
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): BoardCatalogInfo {
        val obj = json.asJsonObject
        return BoardCatalogInfo(
            mode = enumValues<BoardCatalogMode>().firstOrNull { it.name == obj.optional("mode")?.asString } ?: BoardCatalogMode.UNKNOWN,
            updatedAtMillis = obj.optional("updatedAtMillis")?.asLong,
            isStale = obj.optional("isStale")?.asBoolean ?: false,
            coverage = enumValues<BoardCatalogCoverage>().firstOrNull { it.name == obj.optional("coverage")?.asString } ?: BoardCatalogCoverage.UNKNOWN,
        )
    }
}

private fun JsonObject.optional(name: String): JsonElement? = get(name)?.takeUnless { it.isJsonNull }
