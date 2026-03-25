package tw.kevinzhang.extensions_builtin

import tw.kevinzhang.extension_api.model.Paragraph as ExtParagraph
import tw.kevinzhang.gamer_api.model.*
import tw.kevinzhang.hub_server.data.Paragraph as HubParagraph
import tw.kevinzhang.komica_api.model.*

fun KParagraph.toExtParagraph(): ExtParagraph = when (this) {
    is KQuote     -> ExtParagraph.Quote(content)
    is KReplyTo   -> ExtParagraph.ReplyTo(id = content)
    is KText      -> ExtParagraph.Text(content)
    is KImageInfo -> ExtParagraph.ImageInfo(thumb, raw)
    is KVideoInfo -> ExtParagraph.VideoInfo(url)
    is KLink      -> ExtParagraph.Link(content)
    else          -> throw IllegalArgumentException("Unknown KParagraph: $this")
}

fun GParagraph.toExtParagraph(): ExtParagraph = when (this) {
    is GQuote     -> ExtParagraph.Quote(content)
    is GReplyTo   -> ExtParagraph.ReplyTo(id = content)
    is GText      -> ExtParagraph.Text(content)
    is GImageInfo -> ExtParagraph.ImageInfo(thumb, raw)
    is GLink      -> ExtParagraph.Link(content)
    else          -> throw IllegalArgumentException("Unknown GParagraph: $this")
}

fun HubParagraph.toExtParagraph(): ExtParagraph = when (this) {
    is HubParagraph.Quote     -> ExtParagraph.Quote(content)
    is HubParagraph.ReplyTo   -> ExtParagraph.ReplyTo(id = id)
    is HubParagraph.Text      -> ExtParagraph.Text(content)
    is HubParagraph.ImageInfo -> ExtParagraph.ImageInfo(thumb, raw)
    is HubParagraph.VideoInfo -> ExtParagraph.VideoInfo(url)
    is HubParagraph.Link      -> ExtParagraph.Link(content)
}
