package tw.kevinzhang.data.domain

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import tw.kevinzhang.extension_api.model.Paragraph
import java.lang.reflect.Type

private class ParagraphSerializer : JsonSerializer<Paragraph>, JsonDeserializer<Paragraph> {
    override fun serialize(src: Paragraph, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        when (src) {
            is Paragraph.Text -> { obj.addProperty("type", "Text"); obj.addProperty("content", src.content) }
            is Paragraph.Quote -> { obj.addProperty("type", "Quote"); obj.addProperty("content", src.content) }
            is Paragraph.Link -> { obj.addProperty("type", "Link"); obj.addProperty("content", src.content) }
            is Paragraph.ReplyTo -> { obj.addProperty("type", "ReplyTo"); obj.addProperty("targetId", src.targetId); obj.addProperty("preview", src.preview) }
            is Paragraph.ImageInfo -> { obj.addProperty("type", "ImageInfo"); obj.addProperty("thumb", src.thumb); obj.addProperty("raw", src.raw) }
            is Paragraph.VideoInfo -> { obj.addProperty("type", "VideoInfo"); obj.addProperty("url", src.url); obj.addProperty("site", src.site.name) }
        }
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Paragraph {
        val obj = json.asJsonObject
        return when (obj.get("type").asString) {
            "Text" -> Paragraph.Text(obj.get("content").asString)
            "Quote" -> Paragraph.Quote(obj.get("content").asString)
            "Link" -> Paragraph.Link(obj.get("content").asString)
            "ReplyTo" -> Paragraph.ReplyTo(obj.get("targetId").asString, obj.get("preview")?.takeIf { !it.isJsonNull }?.asString)
            "ImageInfo" -> Paragraph.ImageInfo(obj.get("thumb")?.takeIf { !it.isJsonNull }?.asString, obj.get("raw").asString)
            "VideoInfo" -> Paragraph.VideoInfo(obj.get("url").asString, Paragraph.VideoInfo.Site.valueOf(obj.get("site").asString))
            else -> Paragraph.Text("")
        }
    }
}

class ParagraphListConverter {
    private val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(Paragraph::class.java, ParagraphSerializer())
        .create()

    @TypeConverter
    fun toJson(paragraphs: List<Paragraph>): String =
        gson.toJson(paragraphs)

    @TypeConverter
    fun fromJson(json: String): List<Paragraph> =
        gson.fromJson(json, object : TypeToken<List<Paragraph>>() {}.type) ?: emptyList()
}
