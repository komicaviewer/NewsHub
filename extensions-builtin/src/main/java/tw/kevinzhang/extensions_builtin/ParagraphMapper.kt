package tw.kevinzhang.extensions_builtin

import tw.kevinzhang.gamer_api.model.GImageInfo
import tw.kevinzhang.gamer_api.model.GLink
import tw.kevinzhang.gamer_api.model.GParagraph
import tw.kevinzhang.gamer_api.model.GQuote
import tw.kevinzhang.gamer_api.model.GReplyTo
import tw.kevinzhang.gamer_api.model.GText
import tw.kevinzhang.komica_api.model.KImageInfo
import tw.kevinzhang.komica_api.model.KLink
import tw.kevinzhang.komica_api.model.KParagraph
import tw.kevinzhang.komica_api.model.KQuote
import tw.kevinzhang.komica_api.model.KReplyTo
import tw.kevinzhang.komica_api.model.KText
import tw.kevinzhang.komica_api.model.KVideoInfo
import tw.kevinzhang.extension_api.model.Paragraph as ExtParagraph

fun KParagraph.toExtParagraph(): ExtParagraph = when (this) {
    is KQuote     -> ExtParagraph.Quote(content)
    is KReplyTo -> ExtParagraph.ReplyTo(targetId = targetId, preview = preview)
    is KText      -> ExtParagraph.Text(content)
    is KImageInfo -> ExtParagraph.ImageInfo(thumb, raw)
    is KVideoInfo -> ExtParagraph.VideoInfo(url)
    is KLink      -> ExtParagraph.Link(content)
    else          -> throw IllegalArgumentException("Unknown KParagraph: $this")
}

fun GParagraph.toExtParagraph(): ExtParagraph = when (this) {
    is GQuote     -> ExtParagraph.Quote(content)
    is GReplyTo -> ExtParagraph.ReplyTo(targetId = content)
    is GText      -> ExtParagraph.Text(content)
    is GImageInfo -> ExtParagraph.ImageInfo(thumb, raw)
    is GLink      -> ExtParagraph.Link(content)
    else          -> throw IllegalArgumentException("Unknown GParagraph: $this")
}
